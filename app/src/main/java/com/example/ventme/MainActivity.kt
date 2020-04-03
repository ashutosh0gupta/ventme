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
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


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

    var packetCounter : Long = 0
    //todo develop a good data interface
    private fun insertSamples( pressureSamples : List<Number>, airflowSamples : List<Number> ) {
        val navHostFragment: NavHostFragment? = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val fgs = navHostFragment!!.childFragmentManager.fragments
        if( fgs.size > 0 ) {
            val currentFragment = navHostFragment.childFragmentManager.fragments[0]
            if( currentFragment is FirstFragment ) {
                //val plotFrag = currentFragment as FirstFragment
                currentFragment.insertSample(
                    packetCounter, 400,
                    pressureSamples,airflowSamples,
                    (0..100).random(),
                    (0..100).random(),
                    (0..100).random() )
            }
            packetCounter += 1
        }
    }

    private fun foundBtDevice( device : BluetoothDevice ) {
        val navHostFragment: NavHostFragment? = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val fgs = navHostFragment!!.childFragmentManager.fragments
        if( fgs.size > 0 ) {
            val currentFragment = navHostFragment.childFragmentManager.fragments[0]
            if( currentFragment is SecondFragment ) {
                currentFragment.foundDevice(device)
            }
        }
        //val currentFragment = navHostFragment!!.childFragmentManager.fragments[0] as SecondFragment
        //currentFragment.foundDevice(device)
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
                fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorOff)) )
                bluetoothAdapter.enable()
            } else {
                Log.d(TAG, "Bluetooth enabled...starting services")
                //fab.setBackgroundTintList(0xFF00FF00)
                fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorDisconnected)) )
                //fab.setBackgroundColor(0xFF00FF00)
            }
        }

        fab.setOnClickListener { view ->
                var status : String
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
        val s1 = listOf<Number>(1, 4, 2, 8, 4, 16, 8, 32, 16, 64, 3)
        val s2 = listOf<Number>(5, 2, 10, 5, 20, 10, 40, 20, 80, 40, 20)
        Log.d(TAG, "Running insert sample")
        Timer().scheduleAtFixedRate( object : TimerTask() {
            override fun run() { insertSamples(s1,s2) }
        }, 1000,1000)

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
                //Navigation.findNavController( this, R.id.nav_host_fragment).navigate(R.id.action_FirstFragment_to_SecondFragment)
                Navigation.findNavController( this, R.id.nav_host_fragment).navigate(R.id.action_to_SecondFragment)
                true
            }
            R.id.action_find_vent_wifi -> {
                Navigation.findNavController( this, R.id.nav_host_fragment).navigate(R.id.action_to_WifiFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorSearching)) )
                    Log.d(TAG, "Bluetooth discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    fab.setBackgroundTintList( ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorConnected)) )
                    Log.d(TAG, "Bluetooth discovery stopped")
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    Log.d(TAG, "Found a Bluetooth device :" + device.name)
                    foundBtDevice( device )
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
