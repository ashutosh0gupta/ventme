package com.example.ventme

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


private const val TAG="VentMeBluetooth search"

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    //private lateinit var bluetoothManager: BluetoothManager

    private lateinit var btDevicesView: RecyclerView
    private lateinit var btDeviceViewAdapter: btDeviceAdapter
    private lateinit var btDeviceViewManager: RecyclerView.LayoutManager

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun findAvailableDevices() : ArrayList<BluetoothDevice>{
        if(activity != null ) {
            val bluetoothManager =
                activity!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if( bluetoothAdapter != null ) {

                val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
                val MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1
                ActivityCompat.requestPermissions(
                    activity!!, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION
                )
                when (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    PackageManager.PERMISSION_DENIED -> {
                        Log.d(TAG, "permission status : Denied")
                    }
                    PackageManager.PERMISSION_GRANTED -> {
                        Log.d(TAG, "permission status : Granted")
                    }
                }
                val isSuccess = bluetoothAdapter.startDiscovery()
                if( !isSuccess)
                    Log.d(TAG, "Failed to start discovery of bluetooth devices")
                return ArrayList(pairedDevices)
            }
        }
        return ArrayList()
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_second, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val devices = findAvailableDevices()

        btDeviceViewManager = LinearLayoutManager(context)
        btDeviceViewAdapter = btDeviceAdapter(devices)

        btDevicesView = view.findViewById<RecyclerView>(R.id.available_vents).apply {
            setHasFixedSize(true)
            layoutManager = btDeviceViewManager
            adapter = btDeviceViewAdapter
        }

        view.findViewById<Button>(R.id.button_second).setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
    }


    public fun foundDevice( device : BluetoothDevice ) {
        btDeviceViewAdapter.insertDeviceAtBack(device)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "Device search stopped")
    }
}


class btDeviceAdapter(private val myDataset:  ArrayList<BluetoothDevice>) :
    RecyclerView.Adapter<btDeviceAdapter.MyViewHolder>() {

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder.
    // Each data item is just a string in this case that is shown in a TextView.
    class MyViewHolder(val iview: View) : RecyclerView.ViewHolder(iview)


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): btDeviceAdapter.MyViewHolder {
        // create a new view
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.bt_device, parent, false)

        return MyViewHolder(itemView)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        val textView = holder.iview.findViewById(R.id.bt_device_name) as TextView
        textView.text = myDataset[position].name + "\n" + myDataset[position].address
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = myDataset.size

    public fun insertDeviceAtBack( d : BluetoothDevice) {
        val position = myDataset.size
        myDataset.add(position, d)
        this.notifyItemInserted(position)
    }
}
