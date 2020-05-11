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
    companion object{

        const val historyLength = 10000 // 200 sec samples

        const val expectedHeader = 0xAA55AA55.toInt()
        const val expectedFooter = 0x5A5A5A5A.toInt()
        const val streamSize = 2000
    }

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



    //Raw data storage
    // packet format
    //   B = 1 bytes   ( 8 bit signed )
    //   S = 2 bytes   (16 bit signed )
    //
    //
    //   Packet
    //     0xAA55AA55 : BBBB  marks start of packet
    //    packetCount : S
    //    sampleRate  : B   in Hz (not expecting fast sampling)
    //    numSamples  : B   a small number = N [Expected to be 20 ]
    //    errorCode   : S   from a list of codes [ to be defined] [10 bytes header]
    //
    //    setOxygen     : B   in %
    //    setPeep       : B   in H2Omm [pay attention to the unit]
    //    setRR         : B   in pm
    //    setTidalVol   : S   in ml
    //    setIERatio    : B   in [Values 1 = 1:1  2 = 2:1  3 = 3:1 .... -2 = 1:2, -3 = 3:1 ]
    //    future2       : 4 bytes padding for future needs [ 10 bytes set data]
    //
    //    oxygen          : B in %
    //    N blocks of the following 3xS bytes
    //      -- pressureSamples : S in 10^-2 h2ocm   max possible val :  ~327h2ocm
    //      -- airflowSamples  : S in 10^-2 lpm     max possible val :  ~327lpm
    //      -- tidalVolSamples : S in 10^-1 ml      max possible val :  ~3276ml
    //    0x5A5A5A5A      : BBBB  marks end of packet [1 + N*6 bytes + 4]
    //
    //   Sampling is done at the rate of 100 Hz
    //   and 5 packets are sent in every second. Therefore, N = 20

    //-------------------------------------
    // data from device
    //var oxygen : Number? = null
    //var pressureSamples : MutableList<Number> = mutableListOf<Number>()
    //var airflowSamples : MutableList<Number> = mutableListOf<Number>()

    private var pack : DataPack = DataPack()
    var packetCounter : Int = 0
    var sampleRate : Int = 0
    private var packetStartPoint : Int = 0
    private var packetEndPoint : Int = 0
    private var dataPosition : Int = 0
    private var inputStream : ByteArray = ByteArray(streamSize) { _ -> 0 }
    private var sync_state = -1

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

    fun read_header() : Boolean {
        val packetBuffer = ByteBuffer.wrap(inputStream,packetStartPoint+4,16)

        pack.packetCount = packetBuffer.short.toInt() // 2 bytes
        pack.sampleRate = packetBuffer.get().toInt()  // 1 bytes
        pack.numSamples = packetBuffer.get().toInt()  // 1
        pack.errorCode =  packetBuffer.short.toInt()  // 2

        pack.setOxygen = packetBuffer.get().toInt()           // 1
        pack.setPEEP = packetBuffer.get().toFloat()/10        // 2
        pack.setRespiratoryRate =  packetBuffer.get().toInt() // 1
        pack.setTidalVolume =  packetBuffer.short.toInt()     // 2
        pack.setRatioIE =
            when( packetBuffer.get().toInt() ) {
                1 -> {"1:1"} 2 -> {"1:2"} 3 -> {"1:3"} -2 -> {"2:1"} -3 -> {"3:1"} else -> { "ERR" } }           // 1
        packetBuffer.get()  // dumping four bytes
        packetBuffer.get()
        packetBuffer.get()
        packetBuffer.get()

        // check if header is processed correctly
        var response = true
        if( sampleRate != 0 ) {
            if ( (pack.packetCount != packetCounter + 1) or (sampleRate != pack.sampleRate) or
                (pack.sampleRate == 0) ) {
                response = false
            }
        }
        packetCounter = pack.packetCount
        sampleRate = pack.sampleRate
        //Log.d(TAG, "Packet counte and sample rate ${pack.packetCount} ${pack.sampleRate} ${response}")
        return response
    }

    fun read_samples() : Boolean {
        val length = 1+pack.numSamples*6+4
        val packetBuffer = ByteBuffer.wrap(inputStream,packetStartPoint+20, length)
        pack.oxygen = packetBuffer.get().toInt()  //1
        pack.pressureSamples.clear()
        pack.airflowSamples.clear()
        pack.tidalVolumeSamples!!.clear()
        for (i in 0 until pack.numSamples) {
            pack.pressureSamples.add( packetBuffer.short.toFloat()/100 )
            pack.airflowSamples.add( packetBuffer.short.toFloat()/100 )
            pack.tidalVolumeSamples!!.add( packetBuffer.short.toFloat()/10 )
        }
        //Log.d(TAG, "Received prerssure ${pack.pressureSamples}")
        return packetBuffer.int == expectedFooter
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun addRawPacket(rawPacket: ByteArray) : DataPack? {
        //Log.d(TAG, "Packet received ${bytesToHex(rawPacket, 0, rawPacket.size )} Sync state : ${sync_state}")
        for( b in rawPacket ) {
            inputStream[dataPosition] = b
            dataPosition += 1
            dataPosition %= inputStream.size
        }
        if( sync_state < 0 ) {
            if( dataPosition < 4 ) return null
            val buf = ByteBuffer.wrap(inputStream)
            for( i in 0 until dataPosition - 4 ) {
                if( buf.getInt(i) == expectedHeader ) {
                    packetStartPoint = i
                    sync_state = 0
                    break
                }
            }
            if( sync_state < 0 ) {
                inputStream[0] = inputStream[dataPosition-3]
                inputStream[1] = inputStream[dataPosition-2]
                inputStream[2] = inputStream[dataPosition-1]
                //requires burst
                Log.d(TAG, "Header did not match!! ${ByteBuffer.wrap(inputStream).int}")
                //failed to match header
                dataPosition = 3
                return null
            }
        }
        assert( sync_state >= 0 )
        val dataLength = dataPosition - packetStartPoint
        var neededSize = 20
        if( sync_state == 0 ) {
            if( dataLength < neededSize )  return null
            val response = read_header()
            if(!response){
                Log.d(TAG, "Packet data did not match!")
                dataPosition = 0
                resetHistory()
                sync_state = -1
                return null
            }
            sync_state = 1
        }
        neededSize += 1 + pack.numSamples*6 + 4
        //Log.d(TAG, "Reached here! $neededSize $dataLength")
        if( sync_state == 1 ) {
            if( dataLength < neededSize  ) return null
            //Log.d(TAG, "Got enough samples!")
            val response = read_samples()
            for( i in neededSize until dataPosition ) {
                inputStream[i-neededSize] = inputStream[i]
            }
            dataPosition -= neededSize
            if( response ) {
                sync_state = 0
                updateDerivedValues()
                return this.pack
            }else{
                resetHistory()
                sync_state = -1
                return null
            }
        }
        // Unreachable
        assert(false)
        return null
 /*       assert(false)


        if( dataPosition <= 4 ) {
            dataPosition = 0
            packetStartPoint = 0
            packetEndPoint = 0
            return null
        }*/

//        if( ByteBuffer.wrap(inputStream).int != expectedHeader ) {
//            //requires burst
//            Log.d(TAG, "Header did not match!! ${ByteBuffer.wrap(inputStream).int}")
//            //failed to match header
//            dataPosition = 0
//            packetStartPoint = 0
//            packetEndPoint = 0
//            return null
//        }
//
//        if( dataPosition > 20 ) {
//            // received sufficient data
//            if( ByteBuffer.wrap(inputStream).getInt(dataPosition-4) ==  expectedFooter ) {
//                packetEndPoint = dataPosition
//            }
//        }
//
//        if ( packetStartPoint == packetEndPoint ) {
//            if( dataPosition > inputStream.size - 20 ) {
//                // too long packet end is not arrived termination
//                dataPosition = 0
//                packetStartPoint = 0
//                packetStartPoint = 0
//            }
//            return null
//        }
//
//        // processing data
//        var packetLength = packetEndPoint - packetStartPoint
//        if ( packetLength < 0 ) {
//            packetLength += inputStream.size
//        }
//
//        val pack = DataPack()
//        val packetBuffer = ByteBuffer.wrap(inputStream)
//
//        // read control bytes
//        val header = packetBuffer.int                 // 4 bytes
//        pack.packetCount = packetBuffer.short.toInt() // 2 bytes
//        pack.sampleRate = packetBuffer.get().toInt()  // 1 bytes
//        pack.numSamples = packetBuffer.get().toInt()  // 1
//        pack.errorCode =  packetBuffer.short.toInt()  // 2
//
//        Log.d(TAG, "Successfully recognized packet ${pack.packetCount}")
//
//        if( !checkAndUpdateData(pack, header, packetLength) ) {
//            Log.d(TAG, "Packet did not pass sanity check!")
//            dataPosition = 0
//            packetStartPoint = 0
//            packetEndPoint = 0
//            pack.sampleRate = 0 // sends msg that there was an error
//            return pack
//        }
//
//        pack.setOxygen = packetBuffer.get().toInt()           // 1
//        pack.setPEEP = packetBuffer.get().toFloat()/10        // 2
//        pack.setRespiratoryRate =  packetBuffer.get().toInt() // 1
//        pack.setTidalVolume =  packetBuffer.short.toInt()     // 2
//        pack.setRatioIE =
//            when( packetBuffer.get().toInt() ) {
//            1 -> {"1:1"} 2 -> {"1:2"} 3 -> {"1:3"} -2 -> {"2:1"} -3 -> {"3:1"} else -> { "ERR" } }           // 1
//        packetBuffer.get()  // dumping four bytes
//        packetBuffer.get()
//        packetBuffer.get()
//        packetBuffer.get()
//
//        // data received at 100 times to avoid transporting decimals
//
//        pack.oxygen = packetBuffer.get().toInt()  //1
//        for (i in 0 until pack.numSamples) {
//            pack.pressureSamples.add( packetBuffer.short.toFloat()/100 )
//            pack.airflowSamples.add( packetBuffer.short.toFloat()/100 )
//            pack.tidalVolumeSamples!!.add( packetBuffer.short.toFloat()/100 )
//        }
//
//        packetStartPoint = 0
//        packetEndPoint = 0
//        dataPosition = 0
//
//        this.pack = pack
//        updateDerivedValues()
//        return this.pack
    }

    // Data processing
    //
    var currentIndex = 0
    var pressureData : MutableList<Number> = MutableList(historyLength) { 0 }
    var airflowData : MutableList<Number> = MutableList(historyLength) {0}
    var tidalVolData : MutableList<Number> = MutableList(historyLength) { 0.0 }
    var smoothPressureData : MutableList<Number> = MutableList(historyLength) { 0 }
    var diffPressureData : MutableList<Number> = MutableList(historyLength) { 0 }
    var maxMinPositions : MutableList<Number> = MutableList(historyLength) { 0.0 }
    var derivedPmax : Number? = null
    var derivedPEEP : Number? = null
    var derivedRR   : Number? = null
    var derivedRatioIE : String? = null

    @RequiresApi(Build.VERSION_CODES.N)
    private fun resetHistory() {
        currentIndex = 0
        pressureData.replaceAll { 0 }
        airflowData.replaceAll { 0 }
        tidalVolData.replaceAll { 0.0 }
        smoothPressureData.replaceAll { 0.0 }
        diffPressureData.replaceAll { 0.0 }
        derivedPmax = null
        derivedPEEP = null
        derivedRR = null
        derivedRatioIE = null
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

    private val lowPass = 0.1
    @RequiresApi(Build.VERSION_CODES.N)
    private fun generateSmoothPressure() {
        //
        // pass via low pass filter
        //
        var prevIdx = Math.floorMod( (currentIndex - 1), smoothPressureData.size )
        var idx = currentIndex % smoothPressureData.size
        var smoothVal = smoothPressureData[prevIdx].toDouble()
        for( i in 0 until pack.pressureSamples.size ) {
            val inValue = pressureData[idx].toDouble()
            smoothVal = lowPass*smoothVal + (1.0-lowPass)*inValue
            smoothPressureData[idx] = smoothVal
            idx = (idx+1) % smoothPressureData.size
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun differentiatePressure() {
        var prevIdx = Math.floorMod( (currentIndex - 1), smoothPressureData.size )
        var idx = currentIndex % smoothPressureData.size
        var prevValue = smoothPressureData[prevIdx].toDouble()
        for( i in 0 until pack.pressureSamples.size ) {
            val currValue = smoothPressureData[idx].toDouble()
            diffPressureData[idx] = prevValue - currValue
            prevValue = currValue
            idx = (idx+1) % smoothPressureData.size
        }
    }

    var minmaxIndex : Int = 0
    var minmaxPositions : MutableList<Int> = MutableList(10) { 0 }

    fun compute_rr_ie() {
        if ( minmaxIndex != 0 ) {
            return
        }
        var lambdas = mutableListOf<Int>()
        var ratios = mutableListOf<Double>()
        var last_inhale : Int = 0
        var last_exhale : Int = 0
        var isMin = (maxMinPositions[0] > 0)
        for( i in 1 until maxMinPositions.size ) {
            if (isMin == (maxMinPositions[i] > 0)) {
                // min max are not alternating
                return
            }
            var diff = maxMinPositions[i] + maxMinPositions[i - 1]
            if (isMin) diff = -diff
            diff = diff % historyLength
            if(isMin)
                last_exhale = diff
            else
                last_inhale = diff
            if( last_inhale != 0 && last_exhale != 0 && isMin) {
                lambdas.add(last_inhale + last_exhale)
                ratios.add(last_inhale.toDouble() / last_exhale.toDouble())
            }
            isMin = !isMin
        }
        pack.respiratoryRate = (60*pack.sampleRate/lambdas.average()).toInt()

        var avg_ratios = ratios.average()
        val flipped = (avg_ratios < 1 )
        if(avg_ratios < 1 ) avg_ratios = 1/avg_ratios
        var ratio_str = ".1f".format(avg_ratios)
        for( i in 1 until 4) {
            if( i * 0.8 < avg_ratios && avg_ratios < i * 1.2) {
                ratio_str = i.toString()
                break
            }
        }
        if( flipped ) {
            pack.ratioIE = ratio_str + ":1"
        }else{
            pack.ratioIE = "1:" + ratio_str
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun minmaxPressure() {

        var prevIdx = Math.floorMod( (currentIndex - 1), smoothPressureData.size )
        var idx = currentIndex % smoothPressureData.size
        var prevValue = diffPressureData[prevIdx].toDouble()
        for( i in 0 until pack.pressureSamples.size ) {
            var prev_mmidx = Math.floorMod( minmaxIndex-1, minmaxPositions.size )
            var last_event = minmaxPositions[prev_mmidx]
            if ( last_event > 0 && abs(last_event)-1 == idx ) {
                minmaxPositions = MutableList(10) { 0 }
                minmaxIndex = 0
            }
            val currValue = diffPressureData[idx].toDouble()
            if (prevValue <= 0 && currValue > 0 && last_event < 0 ) {
                //found a minima at the start of the rising edge
                minmaxPositions[minmaxIndex] = -(idx+1)
                minmaxIndex = (minmaxIndex + 1) % minmaxPositions.size
                compute_rr_ie()
            }
            if ( prevValue >= 0 && currValue > 0 && last_event > 0 ) {
                //found a maxima at the start of the falling edge
                minmaxPositions[minmaxIndex] = (idx + 1)
                minmaxIndex = (minmaxIndex + 1) % minmaxPositions.size
                compute_rr_ie()
            }
            prevValue = currValue
            idx = (idx+1) % smoothPressureData.size
        }
    }

    private fun analyzePressure() {
        if( actionTime(1 ) ) {
            var idx = (currentIndex - DisplayFragment.maxSamples) % tidalVolData.size
            var minMax = getMinMax( pressureData, idx, DisplayFragment.maxSamples )
            derivedPEEP = minMax.first
            derivedPmax = minMax.second

            idx = (currentIndex - DisplayFragment.maxSamples -1) % tidalVolData.size
            minMax = getMinMax( diffPressureData, idx, DisplayFragment.maxSamples )
            val scale = minMax.second - minMax.first
            // find zero crossings
            var prevV = diffPressureData[idx].toFloat()
            var pressurePeeks : MutableList<Int> = mutableListOf()
            for( i in 0 until DisplayFragment.maxSamples-1 ) {
                idx = (idx + 1) % diffPressureData.size
                val v = diffPressureData[idx].toFloat()
                if( prevV >= 0 && v < 0 ) {
                    // found a maximum
                    pressurePeeks.add(idx)
                }else if( prevV < 0 && v >= 0 ) {
                    //found a minimum
                    // do nothing
                }
                prevV = v
            }
            if( pressurePeeks.size > 2 ) {
                var wavelength = (pressurePeeks[0]-pressurePeeks[1]) % diffPressureData.size
                for( i in 2 until pressurePeeks.size ) {
                     wavelength += (pressurePeeks[i]-pressurePeeks[i-1]) % diffPressureData.size
                }
                wavelength /= (pressurePeeks.size - 1)
                derivedRR = 60 * pack.sampleRate / wavelength
            }
        }
        pack.pressureMax = derivedPmax
        pack.pEEP = derivedPEEP
        pack.respiratoryRate = derivedRR
        pack.ratioIE = derivedRatioIE
    }

    private fun updateDerivedValues() {
        val newIndex = writeToData( pressureData, currentIndex, pack.pressureSamples )
        generateSmoothPressure()
        differentiatePressure()
        minmaxPressure()
        analyzePressure()
        writeToData( airflowData, currentIndex, pack.airflowSamples )
        //computingTidalVolume()
        //integrate for tidal volume
        writeToData( tidalVolData, currentIndex, pack.tidalVolumeSamples )
        currentIndex = newIndex
    }

/*    // dummy packet testing code
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
    }*/
}
