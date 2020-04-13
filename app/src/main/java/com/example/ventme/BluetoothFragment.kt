package com.example.ventme

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_second.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule


private const val TAG="VentMeBluetooth search"

class BluetoothFragment : Fragment() {

    //private lateinit var bluetoothManager: BluetoothManager

    private lateinit var btDevicesView: RecyclerView
    private lateinit var btDeviceViewAdapter: BtDeviceAdapter
    private lateinit var btDeviceViewManager: RecyclerView.LayoutManager

    companion object {
        // Reading name of some BLE device

        //org.bluetooth.characteristic.gap.device_name
        //const val serviceUUID: String = "00001800-0000-1000-8000-00805f9b34fb"
        //const val characteristicUUID: String = "00002a00-0000-1000-8000-00805f9b34fb"

        //const val serviceUUID: String = "00001204-0000-1000-8000-00805f9b34fb"
        //const val characteristicUUID: String =  "00001A01-0000-1000-8000-00805f9b34fb"

        const val characteristicUUID: String = "0000ffe1-0000-1000-8000-00805f9b34fb"
        const val serviceUUID: String = "0000ffe0-0000-1000-8000-00805f9b34fb"

        val ACTION_INITIATE_CONNECTION = "com.ventme.BLUETOOTH_INITIATE_CONNECTION"
        val EXTRA_DEVICE_ADDRESS = "com.ventme.BLUETOOTH_DEVICE_ADDRESS"
    }

    fun enableAndCollectDevices() {

    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result != null) {
                foundDevice( result.device )
            }
            Log.d(TAG, "onScanResult(): ${result?.device?.address} - ${result?.device?.name}")
        }
    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun scanLeDevice(enable: Boolean, bluetoothAdapter: BluetoothAdapter?) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            Timer("StopScan",false).schedule(20000){
                Log.d(TAG, "....Stopping Le scan")
                //bluetoothAdapter!!.bluetoothLeScanner.stopScan(leScanCallback)
                scanLeDevice( false, bluetoothAdapter )
            }
            Log.d(TAG, "Starting Le scan at " + Calendar.getInstance().time.toString() )
            bluetoothAdapter!!.bluetoothLeScanner.startScan(leScanCallback)
        } else {
            Log.d(TAG, "Stopping Le scan at " + Calendar.getInstance().time.toString())
            bluetoothAdapter!!.bluetoothLeScanner.stopScan(leScanCallback)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun findAvailableDevices(){
        if(activity != null ) {
            val bluetoothManager =
                activity!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if( !checkBluetoothSupport(bluetoothAdapter) ) {
                bluetoothHeadline.text = getString(R.string.no_bt)
                return
            }
            if (!bluetoothAdapter.isEnabled) {
                Log.d(TAG, "Bluetooth is currently disabled...enabling")
                bluetoothAdapter.enable()
                Timer("Enabling Bluetooth", false).schedule(2000) { findAvailableDevices() }
                return
            }
            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
            foundDevice(ArrayList(pairedDevices))
            // todo if permission denied we need to post a message
            ActivityCompat.requestPermissions(
                    activity!!, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1 )
            when (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                PackageManager.PERMISSION_DENIED -> { Log.d(TAG, "permission : Denied") }
                PackageManager.PERMISSION_GRANTED -> { Log.d(TAG, "permission : Granted") }
            }
            scanLeDevice( true, bluetoothAdapter)
            //val isSuccess = bluetoothAdapter.startDiscovery()
            //if( !isSuccess )
            //    Log.d(TAG, "Failed to start discovery of bluetooth devices")

        }
    }


    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_second, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // initiate the display content
        val devices : ArrayList<BluetoothDevice> = arrayListOf()
        btDeviceViewManager = LinearLayoutManager(context)
        btDeviceViewAdapter = BtDeviceAdapter(devices)

        btDevicesView = view.findViewById<RecyclerView>(R.id.available_vents).apply {
            setHasFixedSize(true)
            layoutManager = btDeviceViewManager
            adapter = btDeviceViewAdapter
        }

        view.findViewById<Button>(R.id.button_second).setOnClickListener {
            findNavController().navigate(R.id.action_BluetoothFragment_to_DisplayFragment)
        }

        findAvailableDevices()
    }


    fun foundDevice( device : BluetoothDevice? ) {
        if( device != null )
            btDeviceViewAdapter.insertDeviceAtBack(device)
    }

    fun foundDevice( devices : ArrayList<BluetoothDevice> ) {
        for(device in devices )
            btDeviceViewAdapter.insertDeviceAtBack(device)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "Device search stopped")
    }

    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported")
            return false
        }

        if (!activity!!.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported")
            return false
        }

        return true
    }

    inner class BtDeviceAdapter(private val devices:  ArrayList<BluetoothDevice>) :
        RecyclerView.Adapter<BtDeviceAdapter.MyViewHolder>() {

        inner class MyViewHolder(val iview: View) : RecyclerView.ViewHolder(iview), View.OnClickListener {
            init{
                val button = iview.findViewById(R.id.bt_device_name) as Button
                button.setOnClickListener(this)
            }

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onClick(view: View) {
                val dev : BluetoothDevice = devices[layoutPosition]

                // stop scanning bluetooth
                val bluetoothManager =
                    activity!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter
                scanLeDevice( false, bluetoothAdapter )

                //Launch a serive for connection
                val intent = Intent(ACTION_INITIATE_CONNECTION)
                intent.putExtra( EXTRA_DEVICE_ADDRESS, dev.address)
                activity?.sendBroadcast(intent)

                Log.d( TAG, "clicked on device ${dev.address}" )
                //runConnectBluetoothService(dev)
            }
        }
        //class MyViewHolder(val iview: View) : RecyclerView.ViewHolder(iview)

        // Create new views (invoked by the layout manager)
        override fun onCreateViewHolder(parent: ViewGroup,
                                        viewType: Int): MyViewHolder {
            // create a new view
            val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.bt_device, parent, false)

            return MyViewHolder(itemView)
        }

        // Replace the contents of a view (invoked by the layout manager)
        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            val button = holder.iview.findViewById(R.id.bt_device_name) as Button
            //holder.d = myDataset[position]
            button.text = devices[position].name + "\n" + devices[position].address
        }

        // Return the size of your dataset (invoked by the layout manager)
        override fun getItemCount() = devices.size

        fun insertDeviceAtBack( d : BluetoothDevice) {
            for( seenDevice in devices ) {
                if( seenDevice.address == d.address )
                    return
            }
            val position = devices.size
            devices.add(position, d)
            this.notifyItemInserted(position)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //todo: this part needs to be moved to activity
    }

}



