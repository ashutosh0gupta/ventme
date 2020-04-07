package com.example.ventme

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.androidplot.xy.FixedSizeEditableXYSeries

private const val TAG = "DATAHandler"

class VentilatorDataHandler() {

    // reference display
    // https://www.getinge.com/siteassets/products-a-z/servo-u-mechanical-ventilator/servo-u-2.1-automode-en-nonus-750x500.jpg

    inner class DataPack {

        // data flow header
        var packetCount: Int = 0
        var sampleRate : Int = 0
        var numSamples : Int = 0

        //-------------------------------------
        // control values set in the ventilator
        var setOxygen : Number? = null
        var setPeep : Number? = null
        var setRespiratoryRate : Number? = null
        var setTidalVolume : Number? = null
        var setRatioIE : String? = null

        // data
        var oxygen : Number? = null
        var pressureSamples : MutableList<Number> = mutableListOf<Number>()
        var airflowSamples : MutableList<Number> = mutableListOf<Number>()

        // values derived from the data
        var tidalVolumeSamples : MutableList<Number>? = null  // integrate dV
        var pressureMax : Number? = null
        var pEEP : Number? = null                // minimum pressure
        var pressureAverage : Number? = null
        var complianceDynamic : Number? = null   // dV/dP
        var respiratoryRate : Number? = null
        var ratioIE : String? = null
        var tidalVolumePerBodyMass : Number? = null

        // ---------------------------------
        // deduce alarms
        var resparationInactive : Boolean = false
        var pressureOutOfBounds : Boolean = false
        var tidalVolumeOutOfBounds : Boolean = false
    }
    var pressureData : MutableList<Number> = MutableList(10000) { 0 }
    var airflowData : MutableList<Number> = MutableList(10000) {0}
    var tidalVolData : MutableList<Number> = MutableList(10000) { 0.0 }
    private var pack : DataPack = DataPack()

    //var data : MutableList<DataPack> = mutableListOf<DataPack>()

    var packetCounter : Int = 0
    var sampleRate : Int = 0

    @RequiresApi(Build.VERSION_CODES.N)
    private fun checkAndUpdateData(pack : DataPack ) {
        if( (pack.packetCount != packetCounter + 1) or (pack.sampleRate != sampleRate) ) {
            currentIndex = 0
            pressureData.replaceAll { 0 }
            airflowData.replaceAll { 0 }
            tidalVolData.replaceAll { 0.0 }
        }
        packetCounter = pack.packetCount
        sampleRate = pack.sampleRate
    }

    private fun readPacket(packet: ByteArray, numBytes : Int, idx: Int ) : Pair<Int,Int>{
        assert( packet.size >= idx + numBytes )
        var v = 0
        var shift = 1
        var idxWrite : Int = idx
        // correct handling of signed unsigned numbers
        for( i in 0..numBytes) {
            v += packet[idxWrite] * shift
            shift *= 256
            idxWrite += 1
        }
        return Pair(v, idxWrite)
    }

    fun addRawPacket(rawPacket: ByteArray) : DataPack {
        var pack : DataPack = DataPack()
        var packetIndex = 0

        fun readVal( numBytes: Int ) : Int {
            var returnPair = readPacket( rawPacket,4, packetIndex )
            packetIndex = returnPair.second
            return returnPair.first
        }

        // read control bytes
        pack.packetCount = readVal(4)
        pack.sampleRate = readVal(4)
        pack.numSamples = readVal(4)

        checkAndUpdateData(pack)

        // read ventilator configuration
        pack.setOxygen = readVal(4)
        pack.setPeep = readVal(4)
        pack.setRespiratoryRate = readVal(4)
        pack.setTidalVolume = readVal(4)
        pack.setRatioIE = readVal(4).toString()

        // read data collected by sensors
        pack.oxygen = readVal(4)
        for( i in 0..pack.numSamples ) {
            pack.pressureSamples.add( readVal(4) )
        }
        for( i in 0..pack.numSamples ) {
            pack.airflowSamples.add( readVal(4) )
        }
        this.pack = pack
        updateDerivedValues()
        return this.pack
    }

    fun writeToData(series: MutableList<Number>?, initialIndex : Int, samples : MutableList<Number>?  ) : Int {
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
            if( index >= series.size ) {
                index = 0
            }
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
            val dv = (sample as Int).toDouble() / sampleRate
            //Log.d( TAG, "accumulated value of tidal volume $double_dv")
            v += dv
            pack.tidalVolumeSamples!!.add( v )
        }
        writeToData( tidalVolData, currentIndex, pack.tidalVolumeSamples )
        currentIndex = newIndex
    }

    // dummy packet testing code
    var dummyPackCounter = 0
    fun dummyPack() : DataPack {
        var pack : DataPack = DataPack()
        pack.packetCount = dummyPackCounter
        pack.sampleRate = 100
        pack.numSamples = 11
        checkAndUpdateData(pack)

        // read ventilator configuration
        pack.setOxygen = (0..100).random()
        pack.setPeep = (0..100).random()
        pack.setRespiratoryRate = (0..100).random()
        pack.setTidalVolume = 200+50*(1..10).random()
        pack.setRatioIE = "1:2"

        // read data collected by sensors
        pack.oxygen = (0..100).random()
        pack.respiratoryRate = (0..100).random()

        pack.pressureSamples = mutableListOf(1, 4, 2, 8, 4, 16, 8, 32, 16, 64, 3)
        pack.airflowSamples = mutableListOf(5, 2, -50, -25, 20, -10, 40, 20, 60, -40, -20)

        pack.tidalVolumeSamples = null  // integrate dV
        pack.pressureMax = (0..100).random()
        pack.pEEP = (0..100).random()
        pack.pressureAverage = (0..100).random()
        pack.complianceDynamic = (0..100).random()
        pack.respiratoryRate = (0..100).random()
        pack.ratioIE = "1:3"
        pack.tidalVolumePerBodyMass = null

        dummyPackCounter = dummyPackCounter + 1
        this.pack = pack
        updateDerivedValues()
        return pack
    }
}