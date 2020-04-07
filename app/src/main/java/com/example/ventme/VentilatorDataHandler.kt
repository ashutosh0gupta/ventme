package com.example.ventme


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

    }

    var data : MutableList<DataPack> = mutableListOf<DataPack>()
    var packetCounter : Int = 0
    var sampleRate : Int = 0

    private fun checkAndUpdateData(pack : DataPack ) {
        if( (pack.packetCount != packetCounter + 1) or (pack.sampleRate != sampleRate) ) {
            data.clear()
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
    var dummyPackCounter = 0
    fun dummyPack() : DataPack {
        var pack : DataPack = DataPack()
        pack.packetCount = dummyPackCounter
        pack.sampleRate = 100
        pack.numSamples = 11

        // read ventilator configuration
        pack.setOxygen = (0..100).random()
        pack.setPeep = (0..100).random()
        pack.setRespiratoryRate = (0..100).random()
        pack.setRatioIE = "1:2"

        // read data collected by sensors
        pack.oxygen = (0..100).random()
        pack.respiratoryRate = (0..100).random()

        pack.pressureSamples = mutableListOf(1, 4, 2, 8, 4, 16, 8, 32, 16, 64, 3)
        pack.airflowSamples = mutableListOf(5, 2, 10, 5, 20, 10, 40, 20, 80, 40, 20)

        pack.tidalVolumeSamples = null  // integrate dV
        pack.pressureMax = (0..100).random()
        pack.pEEP = (0..100).random()
        pack.pressureAverage = (0..100).random()
        pack.complianceDynamic = (0..100).random()
        pack.respiratoryRate = (0..100).random()
        pack.ratioIE = "1:3"
        pack.tidalVolumePerBodyMass = null

        dummyPackCounter = dummyPackCounter + 1
        return pack
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
        pack.setRatioIE = readVal(4).toString()

        // read data collected by sensors
        pack.oxygen = readVal(4)
        for( i in 0..pack.numSamples ) {
            pack.pressureSamples.add( readVal(4) )
        }
        for( i in 0..pack.numSamples ) {
            pack.airflowSamples.add( readVal(4) )
        }
        data.add(pack)
        return pack
    }
}