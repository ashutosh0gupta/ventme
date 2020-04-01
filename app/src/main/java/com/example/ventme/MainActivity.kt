package com.example.ventme

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*


private const val TAG = "VentMeMainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothManager: BluetoothManager


    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported")
            return false
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported")
            return false
        }

        return true
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        // We can't continue without proper Bluetooth support
        if ( checkBluetoothSupport(bluetoothAdapter)) {
            // Register for system Bluetooth events
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            filter.addAction(BluetoothDevice.ACTION_FOUND)
            registerReceiver(bluetoothReceiver, filter)
            if (!bluetoothAdapter.isEnabled) {
                Log.d(TAG, "Bluetooth is currently disabled...enabling")
                fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorOff)) );
                bluetoothAdapter.enable()
            } else {
                Log.d(TAG, "Bluetooth enabled...starting services")
                //fab.setBackgroundTintList(0xFF00FF00)
                fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorDisconnected)) );
                //fab.setBackgroundColor(0xFF00FF00)
            }
        }

        fab.setOnClickListener { view ->
                var status = "No ventilator connected!"
                if( bluetoothAdapter == null ) {
                    status = "No Bluetooth in the system"
                }else if( bluetoothAdapter.isEnabled ) {
                    if( 1 == 1 ) {
                        status = "No ventilator is connected"
                    }else {
                        status = "Ventilator " + "ventid" +  " is connected"
                    }
                } else {
                    status = "Bluetooth is disabled."
                }
                Snackbar.make(view, status, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_find_vent_bt -> {
                Navigation.findNavController( this, R.id.nav_host_fragment).navigate(R.id.action_FirstFragment_to_SecondFragment)
                true
            }
            R.id.action_find_vent_wifi -> {
                Navigation.findNavController( this, R.id.nav_host_fragment).navigate(R.id.action_FirstFragment_to_WifiFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorSearching)) );
                    Log.d(TAG, "Bluetooth discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorConnected)) );
                    Log.d(TAG, "Bluetooth discovery stopped")
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    Log.d(TAG, "Found a Bluetooth device :" + device.name)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)

                    when (state) {
                        BluetoothAdapter.STATE_ON -> {
                            fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorConnected)) );
                            // what to do if on
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            // what to do if blue tooth is off
                            fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorOff)) );
                        }
                    }
                    Log.d(TAG, "Bluetooth state change")
                }
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onDestroy() {
        super.onDestroy()
        val bluetoothAdapter = bluetoothManager.adapter

        if( bluetoothAdapter != null ) {
            if (bluetoothAdapter.isEnabled) {
            }

            unregisterReceiver(bluetoothReceiver)
        }
    }
}
