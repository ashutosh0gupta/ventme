package com.example.ventme

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.androidplot.xy.*
import kotlinx.android.synthetic.main.fragment_first.*
import java.util.*


private const val TAG = "VentPlotting"

class DisplayFragment : Fragment() {

    // display settings
    private var currentIndex : Int = 0
    private var maxSamples : Int = 40
    private var refreshRate : Long = 500 // in milliseconds

    //plotting data
    private lateinit var pressure : XYPlot
    private lateinit var airflow : XYPlot
    private lateinit var tidalVolume : XYPlot
    private lateinit var seriesPressure : FixedSizeEditableXYSeries
    private lateinit var seriesAirflow : FixedSizeEditableXYSeries
    private lateinit var seriesTidalVolume : FixedSizeEditableXYSeries

    // control
    private var sampleRate: Int = 0
    private var lastDataTime : Date = Calendar.getInstance().time
    private var packetCount : Int = 0

    // data
    private var pack : VentilatorDataHandler.DataPack? = null
    //private var otwoValue : Number? = null
    //private var peepValue : Number? = null
    //private var brValue : Number? = null

    fun get_display_string( n : Number? ) : String {
        if( n == null )
            return getString(R.string.unknown)
        return n.toString()
    }
    fun get_display_string( n : String? ) : String {
        if( n == null )
            return getString(R.string.unknown)
        return n.toString()
    }
    /* Display refresh function */
    fun refreshDisplay() {
        val now = Calendar.getInstance().time
        // if no update in last 10 seconds no data
        if( now.time - lastDataTime.time < 10000 ) {
            activity!!.runOnUiThread(Runnable {
                // o2num != null to sure if the fragment is active
                if( (o2num != null) and (pack != null) ) {
                    o2num.text = get_display_string(pack!!.oxygen)
                    brnum.text = get_display_string(pack!!.respiratoryRate)
                    peepnum.text = get_display_string(pack!!.pEEP)
                    ienum.text = get_display_string(pack!!.ratioIE)
                }
            })
        }else {
            activity!!.runOnUiThread( Runnable {
                if( o2num != null ) {
                    o2num.text = getString(R.string.unknown)
                    brnum.text = getString(R.string.unknown)
                    peepnum.text = getString(R.string.unknown)
                }
            })
            resetPlots()
        }
        pressure.redraw()
        airflow.redraw()
        tidalVolume.redraw()
    }

    fun resetPlots() {
        for( i in 1..maxSamples) {
            seriesPressure.setX(i,i-1)
            seriesAirflow.setX(i,i-1)
            seriesTidalVolume.setX(i,i-1)
            seriesPressure.setY(0,i-1)
            seriesAirflow.setY(0,i-1)
            seriesTidalVolume.setY(0,i-1)
        }
    }
    // process incoming data
//    fun insertSample( localPacketCount : Int, localSampleRate : Int,
//                      pressureSamples : List<Number>, airflowSamples : List<Number>,
//                      localOtwo : Number, localBr : Number, localPeep : Number ) {
//        var idx = 0
//        for( sample in pressureSamples) {
//            seriesPressure.setY(sample,currentIndex)
//            if( idx < airflowSamples.size ) {
//                seriesAirflow.setY(airflowSamples[idx],currentIndex)
//            }
//            currentIndex += 1
//            idx += 1
//            if( currentIndex >= maxSamples ) {
//                currentIndex = 0
//            }
//        }
//        seriesPressure.setY(null, currentIndex)
//        seriesAirflow.setY(null, currentIndex)
//        otwoValue =  localOtwo
//        peepValue = localPeep
//        brValue  =  localBr
//        if( sampleRate != localSampleRate || localPacketCount != packetCount + 1) {
//            // triggers cleanup if there is a loss of packets or
//            // change in sample rate
//            otwoValue =  null
//            peepValue = null
//            brValue  =  null
//            resetPlots()
//            //Log.w(TAG, "---> I am here 2 <----" + lastDataTime.toString() )
//            //Log.w(TAG, "---> I am here 3 <----" + localPacketCount.toString() )
//            //Log.w(TAG, "---> I am here 4 <----" + packateCount.toString() )
//        }
//        packetCount = localPacketCount
//        sampleRate = localSampleRate
//        lastDataTime = Calendar.getInstance().time
//        // Log.w(TAG, "---> I am here <----" + lastDataTime.toString() )
//    }

    // process incoming data
    fun insertPack( pack : VentilatorDataHandler.DataPack ) {
        var idx = 0
        for( sample in pack.pressureSamples ) {
            seriesPressure.setY(sample,currentIndex)
            if( idx < pack.airflowSamples.size ) {
                seriesAirflow.setY(pack.airflowSamples[idx],currentIndex)
            }
            currentIndex += 1
            idx += 1
            if( currentIndex >= maxSamples ) {
                currentIndex = 0
            }
        }
        seriesPressure.setY(null, currentIndex)
        seriesAirflow.setY(null, currentIndex)
        if( sampleRate != pack.sampleRate || pack.packetCount != packetCount + 1) {
            // triggers cleanup if there is a loss of packets or change in sample rate
            resetPlots()
        }
        this.pack = pack
        packetCount = pack.packetCount
        sampleRate = pack.sampleRate
        lastDataTime = Calendar.getInstance().time
    }


    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_first, container, false)


        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pressure = view.findViewById<XYPlot>(R.id.pressure)
        pressure.setDomainStep(StepMode.SUBDIVIDE, 1.0)
        pressure.setRangeStep(StepMode.SUBDIVIDE, 2.0)
        pressure.legend.isVisible = false
        val seriesFormat = LineAndPointFormatter(Color.RED, null, null, null)
        //seriesFormat.interpolationParams = CatmullRomInterpolator.Params(10, CatmullRomInterpolator.Type.Centripetal)
        seriesPressure = FixedSizeEditableXYSeries(null,maxSamples)
        pressure.addSeries(seriesPressure, seriesFormat)

        airflow = view.findViewById<XYPlot>(R.id.airflow)
        airflow.setDomainStep(StepMode.SUBDIVIDE, 1.0)
        airflow.setRangeStep(StepMode.SUBDIVIDE, 2.0)
        airflow.legend.isVisible = false
        seriesAirflow = FixedSizeEditableXYSeries(null,maxSamples)
        airflow.addSeries(seriesAirflow, seriesFormat)

        tidalVolume = view.findViewById<XYPlot>(R.id.tidalvolume)
        tidalVolume.setDomainStep(StepMode.SUBDIVIDE, 1.0)
        tidalVolume.setRangeStep(StepMode.SUBDIVIDE, 2.0)
        tidalVolume.legend.isVisible = false
        seriesTidalVolume = FixedSizeEditableXYSeries(null,maxSamples)
        tidalVolume.addSeries(seriesTidalVolume, seriesFormat)

        resetPlots()

        pressure.redraw()
        airflow.redraw()
        tidalVolume.redraw()

        Timer().scheduleAtFixedRate( object : TimerTask() {
            override fun run() { refreshDisplay() }
        }, 1000,refreshRate)

    }
}
