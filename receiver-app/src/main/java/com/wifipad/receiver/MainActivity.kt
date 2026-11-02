package com.wifipad.receiver

import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import rikka.shizuku.Shizuku
import java.net.Inet4Address

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.wifipad/receiver"
        private const val REQUEST_CODE = 9001
    }

    private val port = Protocol.DEFAULT_PORT
    private var service: IGamepadService? = null
    private var pendingStartResult: MethodChannel.Result? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, GamepadUserService::class.java.name)
    ).daemon(false).processNameSuffix("gamepad").debuggable(false).version(1)

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == REQUEST_CODE) {
            if (grant == PackageManager.PERMISSION_GRANTED) {
                bindService()
            } else {
                failPendingStart("SHIZUKU_PERMISSION_DENIED", "Shizuku permission was denied.")
            }
        }
    }

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IGamepadService.Stub.asInterface(binder)
            completeStart()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }

        override fun onBindingDied(name: ComponentName) {
            service = null
            failPendingStart("SERVICE_DIED", "The Shizuku user service stopped unexpectedly.")
        }

        override fun onNullBinding(name: ComponentName) {
            service = null
            failPendingStart("SERVICE_UNAVAILABLE", "The Shizuku user service returned no binder.")
        }
    }

    override fun getInitialRoute(): String = "/receiver"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getStatus" -> result.success(statusSnapshot())
                    "startReceiver" -> startReceiver(result)
                    "stopReceiver" -> stopReceiver(result)
                    else -> result.notImplemented()
                }
            }
    }

    private fun startReceiver(result: MethodChannel.Result) {
        val current = service
        if (current != null) {
            try {
                if (current.isRunning()) {
                    result.success(statusSnapshot())
                    return
                }
            } catch (_: RemoteException) {
                service = null
            }
        }

        if (!Shizuku.pingBinder()) {
            result.error("SHIZUKU_UNAVAILABLE", "Connect Shizuku on this TV first.", null)
            return
        }
        if (Shizuku.isPreV11()) {
            result.error("SHIZUKU_TOO_OLD", "The installed Shizuku version is too old.", null)
            return
        }

        pendingStartResult = result
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bindService()
            Shizuku.shouldShowRequestPermissionRationale() ->
                failPendingStart("SHIZUKU_PERMISSION_DENIED", "Grant WifiPad permission from the Shizuku prompt.")
            else -> Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    private fun bindService() {
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (error: Exception) {
            failPendingStart("BIND_FAILED", error.message ?: "Could not bind the Shizuku service.")
        }
    }

    private fun completeStart() {
        val current = service ?: return
        val result = pendingStartResult ?: return
        try {
            if (current.start(port)) {
                pendingStartResult = null
                result.success(statusSnapshot())
            } else {
                val message = current.lastError().ifBlank { "The receiver could not start." }
                pendingStartResult = null
                result.error("START_FAILED", message, null)
            }
        } catch (error: RemoteException) {
            pendingStartResult = null
            result.error("START_FAILED", error.message ?: "The receiver service stopped.", null)
        }
    }

    private fun failPendingStart(code: String, message: String) {
        val result = pendingStartResult ?: return
        pendingStartResult = null
        result.error(code, message, null)
    }

    private fun stopReceiver(result: MethodChannel.Result) {
        try {
            service?.stop()
            result.success(statusSnapshot())
        } catch (error: RemoteException) {
            service = null
            result.error("STOP_FAILED", error.message ?: "Could not stop the receiver.", null)
        }
    }

    private fun statusSnapshot(): Map<String, Any> {
        var running = false
        var packets = 0L
        var lastError = ""
        try {
            service?.let {
                running = it.isRunning()
                packets = it.packetsReceived()
                lastError = it.lastError()
            }
        } catch (_: RemoteException) {
            service = null
            lastError = "Receiver service disconnected."
        }

        val shizukuConnected = try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
        val permissionGranted = try {
            shizukuConnected && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }

        return mapOf(
            "running" to running,
            "address" to localIp(),
            "port" to port,
            "packetsReceived" to packets,
            "lastError" to lastError,
            "shizukuConnected" to shizukuConnected,
            "permissionGranted" to permissionGranted
        )
    }

    private fun localIp(): String {
        return try {
            val manager = getSystemService(ConnectivityManager::class.java)
            val network = manager.activeNetwork
            network?.let(manager::getLinkProperties)
                ?.linkAddresses
                ?.asSequence()
                ?.map { it.address }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler(null)
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        pendingStartResult?.error("ACTIVITY_CLOSED", "The receiver screen was closed.", null)
        pendingStartResult = null
        super.onDestroy()
    }
}
