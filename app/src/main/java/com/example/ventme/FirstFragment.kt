package com.example.ventme

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.androidplot.xy.*
import kotlinx.android.synthetic.main.fragment_first.*
import java.util.*


private const val TAG = "VentPlotting"

class FirstFragment : Fragment() {

    private var currentIndex : Int = 0
    private final var maxSamples : Int = 40
    private final var refreshRate : Long = 500 // in milliseconds

    private lateinit var pressure : XYPlot
    private lateinit var airflow : XYPlot
    private lateinit var seriesPressure : FixedSizeEditableXYSeries
    private lateinit var seriesAirflow : FixedSizeEditableXYSeries
    private var otwoValue : Number? = null
    private var peepValue : Number? = null
    private var brValue : Number? = null
    private var lastDataTime : Date = Calendar.getInstance().time

    /* Display refresh function */
    fun refreshDisplay() {
        val now = Calendar.getInstance().time
        // if no update in last 10 seconds no data
        if( now.time - lastDataTime.time < 10000 ) {
            if( otwoValue != null ) {
                activity!!.runOnUiThread(Runnable {
                    o2num.text = otwoValue.toString()
                    brnum.text = brValue.toString()
                    peepnum.text = peepValue.toString()
                })
            }
        }else {
            activity!!.runOnUiThread( Runnable {
                o2num.text = getString(R.string.unknown)
                brnum.text = getString(R.string.unknown)
                peepnum.text = getString(R.string.unknown)
            })
        }
        pressure.redraw()
        airflow.redraw()
    }

    // process incoming data
    public fun insertSample( pressureSamples : List<Number>, airflowSamples : List<Number>,
                             localOtwo : Number, localBr : Number, localPeep : Number ) {
        var idx = 0
        for( sample in pressureSamples) {
            seriesPressure.setY(sample,currentIndex)
            if( idx < airflowSamples.size ) {
                seriesAirflow.setY(airflowSamples[idx],currentIndex)
            }
            currentIndex += 1
            idx += 1
            if( currentIndex >= maxSamples ) {
                currentIndex = 0
            }
        }
        seriesPressure.setY(null, currentIndex)
        seriesAirflow.setY(null, currentIndex)
        otwoValue =  localOtwo
        peepValue = localPeep
        brValue  =  localBr
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

        pressure = view.findViewById<XYPlot>(R.id.pressure);
        pressure.setDomainStep(StepMode.SUBDIVIDE, 1.0)
        pressure.setRangeStep(StepMode.SUBDIVIDE, 2.0)
        pressure.legend.isVisible = false
        val seriesFormat = LineAndPointFormatter(Color.RED, null, null, null)
        //seriesFormat.interpolationParams = CatmullRomInterpolator.Params(10, CatmullRomInterpolator.Type.Centripetal)


        seriesPressure = FixedSizeEditableXYSeries(null,maxSamples)
        pressure.addSeries(seriesPressure, seriesFormat)
        airflow = view.findViewById<XYPlot>(R.id.airflow);
        airflow.setDomainStep(StepMode.SUBDIVIDE, 1.0)
        airflow.setRangeStep(StepMode.SUBDIVIDE, 2.0)
        airflow.legend.isVisible = false

        seriesAirflow = FixedSizeEditableXYSeries(null,maxSamples)

        airflow.addSeries(seriesAirflow, seriesFormat)
        for( i in 1..maxSamples) {
            seriesPressure.setX(i,i-1)
            seriesAirflow.setX(i,i-1)
            seriesPressure.setY(0,i-1)
            seriesAirflow.setY(0,i-1)
        }
        pressure.redraw()
        airflow.redraw()

        Timer().scheduleAtFixedRate( object : TimerTask() {
            override fun run() { refreshDisplay() }
        }, 1000,refreshRate)

        //val series1Numbers = listOf<Number>(1, 4, 2, 8, 4, 16, 8, 32, 16, 64, 3)
        //val series2Numbers = listOf<Number>(5, 2, 10, 5, 20, 10, 40, 20, 80, 40, 20)
        //insertSample( series1Numbers, series2Numbers)
        //insertSample( series1Numbers, series2Numbers)
        //insertSample( series1Numbers, series2Numbers)

    }
}
