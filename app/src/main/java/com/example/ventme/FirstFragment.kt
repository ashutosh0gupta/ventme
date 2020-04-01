package com.example.ventme

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.androidplot.xy.LineAndPointFormatter
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.XYPlot
import com.androidplot.xy.XYSeries


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val plot = view.findViewById<XYPlot>(R.id.pressure);

        // create a couple arrays of y-values to plot:
        val domainLabels =
            arrayOf<Number>(1, 2, 3, 6, 7, 8, 9, 10, 13, 14)
        val series1Numbers =
            arrayOf<Number>(1, 4, 2, 8, 4, 16, 8, 32, 16, 64)
        val series2Numbers =
            arrayOf<Number>(5, 2, 10, 5, 20, 10, 40, 20, 80, 40)


        val series1: XYSeries = SimpleXYSeries(
            series1Numbers.toList(), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Series1"
        )

        val series1Format =
            LineAndPointFormatter(Color.RED, Color.GREEN, Color.BLUE, null)

        plot.addSeries(series1, series1Format)

        /*view.findViewById<Button>(R.id.button_first).setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }*/
    }
}
