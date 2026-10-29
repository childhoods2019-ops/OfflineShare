package com.offlineshare.app.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.offlineshare.app.model.PeerDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiP2pController(private val context: Context) {
    companion object {
        private const val TAG = "WifiP2pController"
        const val DEFAULT_PORT = 8888
    }

    private val p2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    init {
        initChannel()
    }

    private fun initChannel() {
        channel = p2pManager?.initialize(context, Looper.getMainLooper()) {
            Log.w(TAG, "Wi-Fi Direct channel was disconnected. Re-initializing...")
            initChannel()
        }
    }

    fun register() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        _isWifiP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        p2pManager?.requestPeers(channel) { peersList: WifiP2pDeviceList? ->
                            peersList?.deviceList?.let { devices ->
                                _discoveredPeers.value = devices.map { dev ->
                                    PeerDevice(
                                        id = dev.deviceAddress,
                                        name = dev.deviceName.ifBlank { "Android Device (${dev.deviceAddress.takeLast(5)})" },
                                        address = dev.deviceAddress,
                                        port = DEFAULT_PORT,
                                        isGroupOwner = dev.isGroupOwner
                                    )
                                }
                            }
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        if (networkInfo?.isConnected == true) {
                            p2pManager?.requestConnectionInfo(channel) { info ->
                                _connectionInfo.value = info
                            }
                        } else {
                            _connectionInfo.value = null
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, intentFilter)
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() { p2pManager?.stopPeerDiscovery(channel, object: WifiP2pManager.ActionListener { override fun onSuccess(){}; override fun onFailure(reason:Int){} }); _isDiscovering.value=false }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        _isDiscovering.value = true
        p2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Discovery started") }
            override fun onFailure(reason: Int) { _isDiscovering.value = false }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectByAddress(address: String, onSuccess:()->Unit = {}, onFailure:(Int)->Unit = {}) {
        val config = WifiP2pConfig().apply { deviceAddress = address }
        p2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { onSuccess() }
            override fun onFailure(reason: Int) { onFailure(reason) }
        })
    }

    @SuppressLint("MissingPermission")
    fun createAutonomousGroup(onSuccess: (WifiP2pGroup?) -> Unit, onFailure: (Int) -> Unit) {
        p2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                p2pManager?.requestGroupInfo(channel) { group -> onSuccess(group) }
            }
            override fun onFailure(reason: Int) { onFailure(reason) }
        })
    }
}