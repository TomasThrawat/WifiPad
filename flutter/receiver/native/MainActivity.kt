package com.wifipad.wifipad_receiver

import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.IBinder
import android.os.RemoteException
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import rikka.shizuku.Shizuku
import java.net.Inet4Address

class MainActivity : FlutterActivity() {
    private val channelName = "wifipad/receiver"
    private val port = 27191
    private val requestCode = 9001
    @Volatile private var service: IGamepadService? = null
    @Volatile private var lastError = ""
    @Volatile private var permissionPending = false
    private val args by lazy { Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, GamepadUserService::class.java.name)).daemon(false).processNameSuffix("gamepad").debuggable(false).version(1) }
    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IGamepadService.Stub.asInterface(binder)
            try { if (service?.start(port) != true) lastError = service?.lastError().orEmpty() } catch (e: RemoteException) { lastError = e.message ?: "Could not start UDP receiver" }
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; lastError = "Shizuku service disconnected" }
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == requestCode) { permissionPending = false; if (grant == PackageManager.PERMISSION_GRANTED) bindReceiver() else lastError = "Shizuku permission was denied" }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName).setMethodCallHandler { call, result ->
            when (call.method) {
                "start" -> { startReceiver(); result.success(true) }
                "stop" -> { try { service?.stop(); lastError = "" } catch (e: RemoteException) { lastError = e.message ?: "Stop failed" }; result.success(true) }
                "status" -> result.success(status())
                else -> result.notImplemented()
            }
        }
    }
    private fun startReceiver() {
        lastError = ""
        if (!Shizuku.pingBinder()) { lastError = "Shizuku is not running. Open Shizuku and tap Start first."; return }
        if (Shizuku.isPreV11()) { lastError = "Shizuku 11 or newer is required."; return }
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bindReceiver()
            permissionPending -> Unit
            else -> { permissionPending = true; Shizuku.requestPermission(requestCode) }
        }
    }
    private fun bindReceiver() { try { Shizuku.bindUserService(args, connection) } catch (e: Exception) { lastError = e.message ?: "Could not bind Shizuku service" } }
    private fun status(): Map<String, Any> {
        val s = service
        var running = false; var packets = 0L
        try { running = s?.isRunning() == true; packets = s?.packetsReceived() ?: 0L; if (!s?.lastError().isNullOrBlank()) lastError = s?.lastError().orEmpty() } catch (e: RemoteException) { lastError = e.message ?: "Service status unavailable" }
        return mapOf("running" to running, "packets" to packets, "ip" to localIp(), "error" to lastError, "shizuku" to if (Shizuku.pingBinder()) "connected" else "not running")
    }
    private fun localIp(): String = try {
        val cm = getSystemService(ConnectivityManager::class.java); val network = cm.activeNetwork
        network?.let(cm::getLinkProperties)?.linkAddresses?.asSequence()?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull { !it.isLoopbackAddress }?.hostAddress ?: "unknown"
    } catch (_: Exception) { "unknown" }
    override fun onDestroy() { Shizuku.removeRequestPermissionResultListener(permissionListener); try { if (service != null) Shizuku.unbindUserService(args, connection, true) } catch (_: Exception) {}; super.onDestroy() }
}
