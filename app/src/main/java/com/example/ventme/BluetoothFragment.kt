package com.example.ventme

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_second.*
import kotlin.collections.ArrayList


private const val TAG="VentMeBluetooth search"

class BluetoothFragment : Fragment() {

    //private lateinit var bluetoothManager: BluetoothManager

    private lateinit var btDevicesView: RecyclerView
    private lateinit var btDeviceViewAdapter: BtDeviceAdapter
    private lateinit var btDeviceViewManager: RecyclerView.LayoutManager
    private var bluetoothLeService: BluetoothLeService? = null

    private var deviceAddress : String? = null
    private var serviceUUID: String = "some long string"
    private var characteristicUUID: String = "some long string"

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun findAvailableDevices() : ArrayList<BluetoothDevice>{
        if(activity != null ) {
            val bluetoothManager =
                activity!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if( !checkBluetoothSupport(bluetoothAdapter) ) {
                bluetoothHeadline.text = getString(R.string.no_bt)
            }
            if( bluetoothAdapter != null ) {
                if (!bluetoothAdapter.isEnabled) {
                    Log.d(TAG, "Bluetooth is currently disabled...enabling")
                    bluetoothAdapter.enable()
                } else {
                    Log.d(TAG, "Bluetooth enabled...starting services")
                }
                val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
                ActivityCompat.requestPermissions(
                    activity!!, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1 )
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

    private val serviceConnection : ServiceConnection = object : ServiceConnection {
        @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).service
            if (!bluetoothLeService!!.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth")
                return
            }
            // Automatically connects to the device upon successful start-up initialization.
            bluetoothLeService!!.connect(deviceAddress, serviceUUID, characteristicUUID)
        }

        override fun onServiceDisconnected(componentName : ComponentName?) {
            bluetoothLeService = null
        }
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
        btDeviceViewAdapter = BtDeviceAdapter(devices)

        btDevicesView = view.findViewById<RecyclerView>(R.id.available_vents).apply {
            setHasFixedSize(true)
            layoutManager = btDeviceViewManager
            adapter = btDeviceViewAdapter
        }

        view.findViewById<Button>(R.id.button_second).setOnClickListener {
            findNavController().navigate(R.id.action_BluetoothFragment_to_DisplayFragment)
        }
    }


    fun foundDevice( device : BluetoothDevice ) {
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
                iview.setOnClickListener(this)
            }
            override fun onClick(view: View) {
                val dev : BluetoothDevice = devices[layoutPosition]
                deviceAddress = dev.address
                val gattServiceIntent = Intent( activity, BluetoothLeService::class.java)
                activity?.bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE)
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
            val textView = holder.iview.findViewById(R.id.bt_device_name) as TextView
            //holder.d = myDataset[position]
            textView.text = devices[position].name + "\n" + devices[position].address
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
}



