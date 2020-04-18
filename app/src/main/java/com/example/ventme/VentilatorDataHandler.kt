package com.example.ventme

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.math.abs

private const val TAG = "DATAHandler"
//todo : dumping data into file
class VentilatorDataHandler() {

    // reference display
    // https://www.getinge.com/siteassets/products-a-z/servo-u-mechanical-ventilator/servo-u-2.1-automode-en-nonus-750x500.jpg

    inner class DataPack {

        //-------------------------------------
        // data flow header
        var packetCount: Int = 0
        var sampleRate : Int = 0
        var numSamples : Int = 0
        var errorCode  : Int = 0 // error code if there are any errors in the device

        //-------------------------------------
        // control values set in the ventilator
        var setOxygen : Number? = null
        var setPEEP : Number? = null
        var setRespiratoryRate : Number? = null
        var setTidalVolume : Number? = null
        var setRatioIE : String? = null

        //-------------------------------------
        // data from device
        var oxygen : Number? = null
        var pressureSamples : MutableList<Number> = mutableListOf()
        var airflowSamples : MutableList<Number> = mutableListOf()

        // ---------------------------------
        // values derived from the data
        var tidalVolumeSamples : MutableList<Number>? = mutableListOf()  // integrate dV
        var pressureMax : Number? = null
        var pEEP : Number? = null                // minimum pressure
        var pressureAverage : Number? = null
        var complianceDynamic : Number? = null   // dV/dP
        var respiratoryRate : Number? = null
        var ratioIE : String? = null
        var tidalVolumePerBodyMass : Number? = null

        // ---------------------------------
        // deduced alarms
        var respirationOutOfBounds : Boolean = false
        var oxygenOutOfBounds: Boolean = false
        var ratioIEOutOfBounds: Boolean = false
        var pressureOutOfBounds : Boolean = false
        var tidalVolumeOutOfBounds : Boolean = false

    }

    companion object{

        const val historyLength = 10000 // 200 sec samples

        const val expectedHeader = 0xAA55AA55.toInt()
        const val expectedFooter = 0x5A5A5A5A.toInt()
        const var streamSize = 2000
    }

    var currentIndex = 0
    var pressureData : MutableList<Number> = MutableList(historyLength) { 0 }
    var airflowData : MutableList<Number> = MutableList(historyLength) {0}
    var tidalVolData : MutableList<Number> = MutableList(historyLength) { 0.0 }
    var maxMinPositions : MutableList<Number> = MutableList(historyLength) { 0.0 }
    var derivedPmax : Number? = null
    var derivedPEEP : Number? = null
    var derivedRR   : Number? = null
    var derivedRatioIE : String? = null

    private fun resetHistory() {
        currentIndex = 0
        pressureData.replaceAll { 0 }
        airflowData.replaceAll { 0 }
        tidalVolData.replaceAll { 0.0 }
        derivedPmax = null
        derivedPEEP = null
        derivedRR = null
        derivedRatioIE = null
    }

    private var pack : DataPack = DataPack()

    //var data : MutableList<DataPack> = mutableListOf<DataPack>()

    var packetCounter : Int = 0
    var sampleRate : Int = 0

    @RequiresApi(Build.VERSION_CODES.N)
    private fun checkAndUpdateData(pack : DataPack, header: Int, packetSize : Int ) : Boolean {
        val expectedPacketSize : Int = 25 + pack.numSamples*6
        var response = true

        if( (pack.packetCount != packetCounter + 1 && pack.packetCount != Short.MIN_VALUE.toInt() ) or
            ( expectedPacketSize != packetSize ) or (header != expectedHeader ) ) {
            resetHistory()
            response = false
        }
        packetCounter = pack.packetCount
        sampleRate = pack.sampleRate
        return response
    }

    //Raw data storage
    // packet format
    //   C = 1 byte    (header/termination)
    //   B = 1 bytes   ( 8 bit signed )
    //   S = 2 bytes   (16 bit signed )
    //   I = 4 bytes   (32 bit signed )
    //   L = 8 bytes   (64 bit signed )
    //
    //
    //   Packet
    //     0xAA55AA55 : BBBB  marks start of packet
    //    packetCount : S
    //    sampleRate  : B   in Hz (not expecting fast sampling)
    //    numSamples  : B   a small number [Expected to be 10 ]
    //    errorCode   : S   from a list of codes [ to be defined] [10 bytes header]
    //
    //    setOxygen     : B   in %
    //    setPeep       : S   in h2ocm
    //    setRR         : B   in pm
    //    setTidalVol   : S   in ml
    //    setIERatio    : B   in [Values 1 = 1:1  2 = 2:1  3 = 3:1 .... -2 = 1:2, -3 = 3:1 ]
    //    future2       : 3 bytes padding for future needs [ 10 bytes set data]
    //
    //    oxygen          : B in %
    //    pressureSamples : I x S   in 10^-2 h2ocm
    //    airflowSamples  : I x S   in 10^-2 lpm
    //    tidalVolSamples : I x S   in 10^-2 ml
    //    0x5A5A5A5A      : BBBB  marks end of packet [1 + N*6 bytes + 4]
    //
    //   Sampling is done at the rate of 100 Hz
    //   and 5 packets are sent in every second. Therefore, N = 20
    //

    //-------------------------------------
    // data from device
    //var oxygen : Number? = null
    //var pressureSamples : MutableList<Number> = mutableListOf<Number>()
    //var airflowSamples : MutableList<Number> = mutableListOf<Number>()


    private var packetStartPoint : Int = 0
    private var packetEndPoint : Int = 0
    private var dataPosition : Int = 0
    private var inputStream : ByteArray = ByteArray(streamSize) { _ -> 0 }


    private val hexArray = "0123456789ABCDEF".toCharArray()
    fun bytesToHex(bytes: ByteArray, start : Int, end : Int ): String {
        var actualEnd = end
        if ( end >= bytes.size )
            actualEnd = bytes.size - 1
        var actualStart = start
        if( start > actualEnd )
            actualStart = actualEnd
        val hexChars = CharArray((actualEnd-actualStart+1) * 2)
        for (j in actualStart..actualEnd ) {
            val v = (bytes[j] and 0xFF.toByte()).toInt()

            hexChars[j * 2] = hexArray[ (v ushr 4) and 0x0F ]
            hexChars[j * 2 + 1] = hexArray[ v and 0x0F ]

        }
        return String(hexChars)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun addRawPacket(rawPacket: ByteArray) : DataPack? {
        //Log.d(TAG, "Packet received ${bytesToHex(rawPacket, 0, rawPacket.size )}")
        for( b in rawPacket ) {
            inputStream[dataPosition] = b
            dataPosition += 1
            dataPosition %= inputStream.size
        }
        if( dataPosition <= 4 ) {
            dataPosition = 0
            packetStartPoint = 0
            packetEndPoint = 0
            return null
        }
        if( ByteBuffer.wrap(inputStream).int != expectedHeader ) {
            //requires burst
            Log.d(TAG, "Header did not match!! ${ByteBuffer.wrap(inputStream).int}")
            //failed to match header
            dataPosition = 0
            packetStartPoint = 0
            packetEndPoint = 0
            return null
        }

        if( dataPosition > 20 ) {
            // received sufficient data
            if( ByteBuffer.wrap(inputStream).getInt(dataPosition-4) ==  expectedFooter ) {
                packetEndPoint = dataPosition
            }
        }

        if ( packetStartPoint == packetEndPoint ) {
            if( dataPosition > inputStream.size - 20 ) {
                // too long packet end is not arrived termination
                dataPosition = 0
                packetStartPoint = 0
                packetStartPoint = 0
            }
            return null
        }

        // processing data
        var packetLength = packetEndPoint - packetStartPoint
        if ( packetLength < 0 ) {
            packetLength += inputStream.size
        }

        val pack = DataPack()
        val packetBuffer = ByteBuffer.wrap(inputStream)

        // read control bytes
        val header = packetBuffer.int                 // 4 bytes
        pack.packetCount = packetBuffer.short.toInt() // 2 bytes
        pack.sampleRate = packetBuffer.get().toInt()  // 1 bytes
        pack.numSamples = packetBuffer.get().toInt()  // 1
        pack.errorCode =  packetBuffer.short.toInt()  // 2

        Log.d(TAG, "Successfully recognized packet ${pack.packetCount}")

        if( !checkAndUpdateData(pack, header, packetLength) ) {
            Log.d(TAG, "Packet did not pass sanity check!")
            dataPosition = 0
            packetStartPoint = 0
            packetEndPoint = 0
            pack.sampleRate = 0 // sends msg that there was an error
            return pack
        }

        pack.setOxygen = packetBuffer.get().toInt()           // 1
        pack.setPEEP = packetBuffer.short.toInt()             // 2
        pack.setRespiratoryRate =  packetBuffer.get().toInt() // 1
        pack.setTidalVolume =  packetBuffer.short.toInt()     // 2
        pack.setRatioIE =
            when( packetBuffer.get().toInt() ) {
            1 -> {"1:1"} 2 -> {"1:2"} 3 -> {"1:3"} -2 -> {"2:1"} -3 -> {"3:1"} else -> { "ERR" } }           // 1
        packetBuffer.get()  // dumping three bytes
        packetBuffer.get()
        packetBuffer.get()

        // data received at 100 times to avoid transporting decimals

        pack.oxygen = packetBuffer.get().toInt()  //1
        for (i in 0 until pack.numSamples) {
            pack.pressureSamples.add( packetBuffer.short.toFloat()/100 )
        }
        for (i in 0 until pack.numSamples) {
            pack.airflowSamples.add( packetBuffer.short.toFloat()/100 )
        }
        for (i in 0 until pack.numSamples) {
            pack.tidalVolumeSamples!!.add( packetBuffer.short.toFloat()/100 )
        }

        packetStartPoint = 0
        packetEndPoint = 0
        dataPosition = 0

        this.pack = pack
        updateDerivedValues()
        return this.pack
    }

    private fun writeToData(series: MutableList<Number>?, initialIndex : Int, samples : MutableList<Number>?  ) : Int {
        if ( (samples == null) or (series == null) ) {
            return initialIndex
        }
        var initialSamplePosition = 0
        // if we have a long update then update only the trailpart
        if( samples!!.size > series!!.size ) {
            initialSamplePosition = samples.size - series.size
        }
        var index = initialIndex
        for( sampleIndex in initialSamplePosition until samples.size) {
            series[index] = samples[sampleIndex]
            index += 1
            index %= series.size
        }
        return index
    }

    private fun actionTime( timing : Int ) : Boolean {
        val minRange = DisplayFragment.maxSamples - (timing+1)*pack.numSamples
        val maxRange = DisplayFragment.maxSamples - timing*pack.numSamples
        val current = currentIndex % DisplayFragment.maxSamples
        if( current in (minRange + 1)..maxRange) {
            return true
        }
        return false
    }

    private fun computingTidalVolume() : Boolean {
        var driftCorrection = false
        val previousIndex = when(currentIndex) { 0 -> {tidalVolData.size-1} else -> currentIndex-1}
        // drift correction
        if( actionTime(0 ) ) {
            // some condition to evaluate the situation
            var min = tidalVolData[previousIndex].toFloat()
            var max = tidalVolData[previousIndex].toFloat()
            var idx = previousIndex
            for( i in 0 until DisplayFragment.maxSamples ) {
                val v = tidalVolData[idx].toFloat()
                if( v < min )
                    min = v
                if( v > max )
                    max = v
                idx = when( idx ) { 0 -> {tidalVolData.size-1} else -> idx - 1 }
            }

            if( abs(min) > (max-min)/10 ) {
                for( i in 0 until tidalVolData.size ) {
                    tidalVolData[i] = tidalVolData[i].toFloat() - min
                }
                driftCorrection = true
            }
        }

        // updating the pack
        pack.tidalVolumeSamples = mutableListOf()
        if( driftCorrection ) {
            var idx = (currentIndex - DisplayFragment.maxSamples) % tidalVolData.size
            for( i in 0 until DisplayFragment.maxSamples ) {
                pack.tidalVolumeSamples!!.add( tidalVolData[idx] )
               idx = ( idx + 1) % tidalVolData.size
            }
        }
        // integrate new data
        var v = tidalVolData[previousIndex] as Double
        for( sample in pack.airflowSamples ) {
            // ml = (1000/60)*(lpm/Hz)
            val dv = 1000*sample.toDouble() / (60*sampleRate)
            //Log.d( TAG, "accumulated value of tidal volume $double_dv")
            v += dv
            pack.tidalVolumeSamples!!.add( v )
        }
        return driftCorrection
    }

    private fun getMinMax( data: MutableList<Number>, position : Int, length : Int ) : Pair<Float,Float> {
        var idx = position
        var min = data[idx].toFloat()
        var max = data[idx].toFloat()
        for( i in 0 until length ) {
            val v = pressureData[idx].toFloat()
            if( v < min )
                min = v
            if( v > max )
                max = v
            idx = (idx+1) % data.size
        }
        return Pair(min,max)
    }

    private fun analyzePressure() {
        if( actionTime(1 ) ) {
            var idx = (currentIndex - DisplayFragment.maxSamples) % tidalVolData.size
            var minMax = getMinMax( pressureData, idx, DisplayFragment.maxSamples )
            derivedPEEP = minMax.first
            derivedPmax = minMax.second
            val scale = derivedPmax!!.toFloat()-derivedPEEP!!.toFloat()

            for( i in 0 until DisplayFragment.maxSamples ) {
                getMinMax( pressureData, idx, )
                if( isLocalMax( idx, 10, scale ) ) {

                }
                idx = ( idx + 1) % tidalVolData.size
            }
        }
        pack.pressureMax = derivedPmax
        pack.pEEP = derivedPEEP
        pack.respiratoryRate = derivedRR
        pack.ratioIE = derivedRatioIE
    }
    private fun updateDerivedValues() {
        val newIndex = writeToData( pressureData, currentIndex, pack.pressureSamples )
        writeToData( airflowData, currentIndex, pack.airflowSamples )
        computingTidalVolume()
        //integrate for tidal volume
        writeToData( tidalVolData, currentIndex, pack.tidalVolumeSamples )
        currentIndex = newIndex
    }

    // dummy packet testing code
    var dummyPackCounter : Int = 0
    @RequiresApi(Build.VERSION_CODES.N)
    fun dummyPack() : DataPack {
        val pack = DataPack()
        pack.packetCount = dummyPackCounter
        pack.sampleRate = 100
        pack.numSamples = 11
        //checkAndUpdateData(pack, expectedHeader, 25+11*12)

        // read ventilator configuration
        pack.setOxygen = (0..100).random()
        pack.setPEEP = (0..100).random()
        pack.setRespiratoryRate = (0..100).random()
        pack.setTidalVolume = 200+50*(1..10).random()
        pack.setRatioIE = "1:2"

        // read data collected by sensors
        pack.oxygen = (0..100).random()
        pack.respiratoryRate = (0..100).random()

        pack.pressureSamples = mutableListOf(1.1, 4.2, 2.0, 8.0, 4.0, 16.0, 8.0, 32.0, 16.0, 64.0, 3.0)
        pack.airflowSamples = mutableListOf(5.0, 2.0, -50.0, -27.0, 20.0, -10.0, 40.0, 20.0, 60.0, -40.0, -20.0)

        pack.tidalVolumeSamples = null  // integrate dV
        pack.pressureMax = (0..100).random()
        pack.pEEP = (0..100).random()
        pack.pressureAverage = (0..100).random()
        pack.complianceDynamic = (0..100).random()
        pack.respiratoryRate = (0..100).random()
        pack.ratioIE = "1:3"
        pack.tidalVolumePerBodyMass = null

        val inc : Short = 1
        dummyPackCounter = dummyPackCounter + inc
        this.pack = pack
        updateDerivedValues()
        return pack
    }
}
