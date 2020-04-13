package com.example.ventme

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.*


private const val TAG="VentMeBluetoothService"

class BluetoothLeService : Service() {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDeviceAddress: String? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // maintain service state
    private var serviceState: Int = STATE_DISCONNECTED

    // Intent names
    companion object {
        private val STATE_DISCONNECTED = 0
        private val STATE_CONNECTING = 1
        private val STATE_CONNECTED = 2
        val ACTION_GATT_CONNECTED = "com.ventme.ACTION_GATT_CONNECTED"
        val ACTION_GATT_DISCONNECTED = "com.ventme.ACTION_GATT_DISCONNECTED"
        val ACTION_GATT_SERVICES_DISCOVERED = "com.ventme.ACTION_GATT_SERVICES_DISCOVERED"
        val ACTION_DATA_AVAILABLE = "com.ventme.BLUETOOTH_ACTION_DATA_AVAILABLE"
        val READ_DATA = "com.ventme.BLUETOOTH_READ_DATA"
    }

    var lastMessageRead: ByteArray? = null
    private var gattService: BluetoothGattService? = null
    private var serviceUUID: UUID? = null
    // May we have a list of characteristic UUIDs that we are listening
    private var characteristicUUID: UUID? = null
    private var controlUUID: UUID? = null

    inner class LocalBinder : Binder() {
        val service: BluetoothLeService = this@BluetoothLeService
    }

    val localBind : IBinder? = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        return localBind
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onUnbind(intent: Intent?): Boolean {
        if (bluetoothGatt != null) {
            bluetoothGatt!!.close()
            bluetoothGatt = null
        }
        return super.onUnbind(intent)
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if ( newState == BluetoothProfile.STATE_CONNECTED) {
                serviceState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED )
                Log.i(TAG, "Connected to GATT server.")
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +  gatt!!.discoverServices())
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                serviceState = STATE_DISCONNECTED
                Log.i(TAG, "Disconnected from GATT server.")
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
                //List all services on the GATT services
                if (gatt != null) {
                    for (service in gatt.services) {
                        Log.d(TAG, "Found service: " + service.uuid.toString())
                    }
                    gattService = gatt.getService(serviceUUID)
                }
                if (gattService != null) {
                    for( ch in gattService!!.characteristics) {
                        Log.d(TAG, "Found characteristics: " + ch.uuid.toString())
                    }
                    val characteristic = gattService!!.getCharacteristic(characteristicUUID)
                    bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
                    val rs = gatt!!.readCharacteristic( characteristic )
                    if (!rs) {
                        Log.i(TAG, "Can't read mGattMiFloraFwCharacteristic")
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicWrite( gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?, status: Int ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
        }
        override fun onCharacteristicChanged( gatt: BluetoothGatt?,
                                              characteristic: BluetoothGattCharacteristic? ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if( characteristic == gattService!!.getCharacteristic(characteristicUUID) ) {
                lastMessageRead = characteristic!!.value
                broadcastUpdate( ACTION_DATA_AVAILABLE )
            }
        }

        override fun onCharacteristicRead( gatt: BluetoothGatt?,
                                           characteristic: BluetoothGattCharacteristic?, status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if( status != BluetoothGatt.GATT_SUCCESS ) {
                return
            }
            //Log.d(TAG, "characteristic: " + characteristic?.uuid.toString())
            if( characteristic == gattService!!.getCharacteristic(characteristicUUID) ) {
                val readValue = characteristic!!.value
                lastMessageRead = readValue
                broadcastUpdate( ACTION_DATA_AVAILABLE )
                //todo figureout why do need the following call
                //sendControlMsg( gatt )
            }

        }

        //why this code
        fun sendControlMsg( gatt: BluetoothGatt?) {
            //enable read data
            val control: BluetoothGattCharacteristic = gattService!!.getCharacteristic(controlUUID)
            val mValue = byteArrayOf(0xA0.toByte(), 0x1F.toByte())
            control.value = mValue
            val rs = gatt!!.writeCharacteristic(control)
            if (!rs) {
                Log.i(TAG, "Can't write mGattMiFloraEnableDataCharacteristic")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun initialize(): Boolean {
        if (bluetoothManager == null) {
            bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }
        bluetoothAdapter = bluetoothManager!!.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun connect(address: String?, localServiceUUID: String, localCharacteristicUUID: String): Boolean {
        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }

        // initialize UUIDs
        serviceUUID = UUID.fromString(localServiceUUID)
        characteristicUUID = UUID.fromString(localCharacteristicUUID)
        //controlUUID = UUID.fromString(localControlUUID)

        // Previously connected device.  Try to reconnect.
        //if (bluetoothDeviceAddress != null && address == bluetoothDeviceAddress && bluetoothGatt != null) {
        //    Log.d(TAG, "Trying to use an existing bluetoothGatt for connection.")
        //    if (bluetoothGatt.connect()) {
        //        serviceState = STATE_CONNECTING
        //    }
        //}
        val device: BluetoothDevice? = bluetoothAdapter!!.getRemoteDevice(address)
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.")
            //msg("Device Not Found,Please go to device lis and reconnect")
            return false
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback )
        Log.d(TAG, "Trying to create a new connection.")
        bluetoothDeviceAddress = address
        serviceState = STATE_CONNECTING
        return true
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun disconnect() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        bluetoothGatt!!.disconnect()
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        if( action == ACTION_DATA_AVAILABLE ) {
            intent.putExtra( READ_DATA, lastMessageRead)
        }
        sendBroadcast(intent)
    }

}

