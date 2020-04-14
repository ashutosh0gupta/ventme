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
        const val expectedFooter = 0x55AA55AA.toInt()
    }

    var pressureData : MutableList<Number> = MutableList(historyLength) { 0 }
    var airflowData : MutableList<Number> = MutableList(historyLength) {0}
    var tidalVolData : MutableList<Number> = MutableList(historyLength) { 0.0 }
    private var pack : DataPack = DataPack()

    //var data : MutableList<DataPack> = mutableListOf<DataPack>()

    var packetCounter : Int = 0
    var sampleRate : Int = 0

    @RequiresApi(Build.VERSION_CODES.N)
    private fun checkAndUpdateData(pack : DataPack, header: Int, packetSize : Int ) : Boolean {

//        if( (pack.packetCount != packetCounter + 1 && pack.packetCount != 0) or
//            (pack.sampleRate != sampleRate) ) {
        val expectedPacketSize : Int = 25 + pack.numSamples*12
        var response = true

//        Log.d( TAG, "${pack.packetCount} != ${packetCounter}")
//        Log.d( TAG, "${expectedPacketSize} != ${packetSize}")
//        Log.d( TAG, "${expectedHeader} != ${header}")

        if( (pack.packetCount != packetCounter + 1 && pack.packetCount != Short.MIN_VALUE.toInt() ) or
            ( expectedPacketSize != packetSize ) or (header != expectedHeader ) ) {
            currentIndex = 0
            pressureData.replaceAll { 0 }
            airflowData.replaceAll { 0 }
            tidalVolData.replaceAll { 0.0 }
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
    //    setPeep       : S   in mbar
    //    setRR         : B   in pm
    //    setTidalVol   : S   in ml
    //    setIERatio    : B   in [Values 1 = 1:1  2 = 2:1  3 = 3:1 .... -2 = 1:2, -3 = 3:1 ]
    //    future2       : 3 bytes padding for future needs [ 10 bytes set data]
    //
    //    oxygen          : B in %
    //    pressureSamples : I x N   in mbar
    //    airflowSamples  : I x N   in lpm
    //    tidalVolSamples : I x N   in ml
    //     ####           : BBBB  marks end of packet [1 + N*12 bytes + 4]
    //
    //   Sampling is done at the rate of 100 Hz
    //   and 10 packets are sent in every second. Therefore, N = 10
    //

    //-------------------------------------
    // data from device
    //var oxygen : Number? = null
    //var pressureSamples : MutableList<Number> = mutableListOf<Number>()
    //var airflowSamples : MutableList<Number> = mutableListOf<Number>()


    private var streamSize = 2000
    private var packetStartPoint : Int = 0
    private var packetEndPoint : Int = 0
    private var dataPosition : Int = 0
    private var inputStream : ByteArray = ByteArray(streamSize) { _ -> 0 }

//    private fun readInt( numBytes : Int, idx: Int ) : Pair<Int,Int>{
//        assert( packetEndPoint >= idx + numBytes )
//        var v = 0
//        var shift = 1
//        var idxRead : Int = idx
//        // correct handling of signed unsigned numbers
//        for( i in 0..numBytes) {
//            v += inputStream[idxRead] * shift
//            shift *= 256
//            idxRead += 1
//            idxRead %= inputStream.size
//        }
//        return Pair(v, idxRead)
//    }

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
//        if( dataPosition == 0 ) {
//            if( rawPacket.size < 20 )
//                return null
//            // After reset we need to check if the header is there
//            //Log.d(TAG, "Header matched ${rawPacket.toString(Charsets.UTF_8)}")
//            packetStartPoint = dataPosition
//            packetEndPoint = dataPosition // toDetect new packet processing has started
//        }
//        val inHeader : Boolean = ( dataPosition <= 4 )
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

//        var packetIndex = packetStartPoint
//        fun readVal(numBytes: Int): Int {
//            var returnPair = readInt(numBytes, packetIndex)
//            packetIndex = returnPair.second
//            return returnPair.first
//        }

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


        pack.oxygen = packetBuffer.get().toInt()  //1
        for (i in 0..pack.numSamples-1) {
            pack.pressureSamples.add( packetBuffer.int.toFloat()/100 )
        }
        for (i in 0..pack.numSamples-1) {
            pack.airflowSamples.add( packetBuffer.int.toFloat()/100 )
        }
        for (i in 0..pack.numSamples-1) {
            pack.tidalVolumeSamples!!.add( packetBuffer.int.toFloat()/100 )
        }


        // read control bytes
//        pack.packetCount = readVal(8)
//        pack.sampleRate = readVal(2)
//        pack.numSamples = readVal(2)


        // read ventilator configuration
//        pack.setOxygen = readVal(4)
//        pack.setPEEP = readVal(4)
//        pack.setRespiratoryRate = readVal(4)
//        pack.setTidalVolume = readVal(4)
//        pack.setRatioIE = readVal(4).toString()

        // read data collected by sensors
//        pack.oxygen = readVal(4)
//        for (i in 0..pack.numSamples) {
//            pack.pressureSamples.add(readVal(4))
//        }
//        for (i in 0..pack.numSamples) {
//            pack.airflowSamples.add(readVal(4))
//        }
//

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

    var currentIndex = 0
    fun updateDerivedValues() {
        val newIndex = writeToData( pressureData, currentIndex, pack.pressureSamples )
        writeToData( airflowData, currentIndex, pack.airflowSamples )
        //integrate for tidal volume
        val previousIndex = when(currentIndex) { 0 -> {tidalVolData.size-1} else -> currentIndex-1}
        var v = tidalVolData[previousIndex] as Double
        pack.tidalVolumeSamples = mutableListOf()
        for( sample in pack.airflowSamples ) {
            val dv = 1000*sample.toDouble() / (60*sampleRate)
            //Log.d( TAG, "accumulated value of tidal volume $double_dv")
            v += dv
            pack.tidalVolumeSamples!!.add( v )
        }
        writeToData( tidalVolData, currentIndex, pack.tidalVolumeSamples )
        if( currentIndex % DisplayFragment.maxSamples < pack.numSamples ) {
            // some condition to evaluate the situation
//            var min = tidalVolData[0].toFloat()
//            var max = tidalVolData[0].toFloat()
//            for( v in tidalVolData ) {
//                val v = tidalVolData[i].toFloat()
//                if( v < min )
//                    min = v
//                if( v > max )
//                    max = v
//            }
//            //val min = tidalVolData.min()
//            //val max = tidalVolData.max()
//            if( abs(min) > (max-min)/10 ) {
//                for( i in 0 until tidalVolData.size )
//                    tidalVolData[i] = tidalVolData[i].toFloat() - min
//            }
        }
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
