package com.example.ventme

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


private const val TAG = "VentMeMainActivity"

class MainActivity : AppCompatActivity() {

    //private lateinit var bluetoothManager: BluetoothManager

    private var ventilatorState : Int = Ventilator_DISCONNECTED
    private var ventID : String? = null
    private var dataHandler : VentilatorDataHandler = VentilatorDataHandler()

    companion object {
        const val Ventilator_CONNECTED_VIA_BT = 1
        const val Ventilator_CONNECTED_VIA_WIFI = 2
        const val Ventilator_DISCONNECTED = 0
    }

    private fun insertSamples( pack: VentilatorDataHandler.DataPack ) {
        val navHostFragment: NavHostFragment? = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val fgs = navHostFragment!!.childFragmentManager.fragments
        if( fgs.size > 0 ) {
            val currentFragment = navHostFragment.childFragmentManager.fragments[0]
            if( currentFragment is DisplayFragment ) {
                //val plotFrag = currentFragment as DisplayFragment
                currentFragment.insertPack( pack )
            }
        }
    }

    private fun foundBtDevice( device : BluetoothDevice ) {
        val navHostFragment: NavHostFragment? = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val fgs = navHostFragment!!.childFragmentManager.fragments
        if( fgs.size > 0 ) {
            val currentFragment = navHostFragment.childFragmentManager.fragments[0]
            if( currentFragment is BluetoothFragment ) {
                currentFragment.foundDevice(device)
            }
        }
        //val currentFragment = navHostFragment!!.childFragmentManager.fragments[0] as BluetoothFragment
        //currentFragment.foundDevice(device)
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Register for system Bluetooth events
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorOff))
        fab.setOnClickListener { view ->
                var status : String = "This message should not be visible!"
                if( ventilatorState == Ventilator_DISCONNECTED ) {
                    status = "No ventilator connected!"
                }else if( ventilatorState == Ventilator_CONNECTED_VIA_BT ) {
                    status = "Ventilator $ventID is connected via bluetooth"
                } else if( ventilatorState == Ventilator_CONNECTED_VIA_WIFI ) {
                    status = "Ventilator $ventID is connected via bluetooth"
                }
                Snackbar.make(view, status, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        Timer().scheduleAtFixedRate( object : TimerTask() {
            override fun run() { insertSamples( dataHandler.dummyPack() ) }
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
                //Navigation.findNavController( this, R.id.nav_host_fragment).navigate(R.id.action_DisplayFragment_to_BluetoothFragment)
                Navigation.findNavController( this, R.id.nav_host_fragment).navigate(R.id.action_to_BluetoothFragment)
                true
            }
            R.id.action_find_vent_wifi -> {
                Navigation.findNavController( this, R.id.nav_host_fragment).navigate(R.id.action_to_WifiFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun updateFab( colorId: Int ) {
        fab.backgroundTintList =  ColorStateList.valueOf(ContextCompat.getColor(applicationContext, colorId ))
        when(ventilatorState) {
            Ventilator_CONNECTED_VIA_BT -> {
                fab.setImageDrawable(ContextCompat.getDrawable(applicationContext, android.R.drawable.stat_sys_data_bluetooth))
            }
            Ventilator_CONNECTED_VIA_WIFI -> {
                // todo wifi ICON needs to be added
                fab.setImageDrawable(ContextCompat.getDrawable(applicationContext, android.R.drawable.presence_offline))
            }
            Ventilator_DISCONNECTED ->{
                fab.setImageDrawable(ContextCompat.getDrawable(applicationContext, android.R.drawable.presence_offline))
            }
        }
        //fab.setImageDrawable(ContextCompat.getDrawable(applicationContext, android.R.drawable.))
    }

    // todo : workout all cases
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    updateFab( R.color.colorSearching )
                    Log.d(TAG, "Bluetooth discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    updateFab( R.color.colorConnected )
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
                            fab.backgroundTintList =
                                ColorStateList.valueOf(ContextCompat.getColor(applicationContext, R.color.colorConnected));
                            // what to do if on
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            if(ventilatorState == Ventilator_CONNECTED_VIA_BT )
                                ventilatorState = Ventilator_DISCONNECTED
                            updateFab(R.color.colorOff)
                        }
                    }
                    Log.d(TAG, "Bluetooth state change")
                }
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    Log.d(TAG, "GATT connected")
                    ventilatorState = Ventilator_CONNECTED_VIA_BT
                    updateFab(R.color.colorConnected)
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected")
                    if(ventilatorState == Ventilator_CONNECTED_VIA_BT )
                        ventilatorState = Ventilator_DISCONNECTED
                    updateFab(R.color.colorDisconnected)
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    //todo why this event exists
                    Log.d(TAG, "service discovered")
                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {

                    Log.d(TAG, "Ventilator data received")
                    val packet = intent.getSerializableExtra(BluetoothLeService.READ_DATA) as ByteArray
                    val pack = dataHandler.addRawPacket( packet )
                    insertSamples(pack)
                }
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}
