package com.wifipad.receiver

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private var service: IGamepadService? = null
    private lateinit var statusView: TextView
    private val port = Protocol.DEFAULT_PORT
    private val requestCode = 9001
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == requestCode) {
            if (grant == PackageManager.PERMISSION_GRANTED) bindService()
            else statusView.text = "Shizuku permission was denied"
        }
    }

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, GamepadUserService::class.java.name)
    ).daemon(false).processNameSuffix("gamepad").debuggable(false).version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IGamepadService.Stub.asInterface(binder)
            try {
                service?.start(port)
                FileLogger.log(this@MainActivity, "START — gamepad service running on port $port")
            } catch (e: RemoteException) {
                // Remote (Shizuku user service) process died/never came up before this
                // call landed. Drop the stale binder instead of crashing the caller —
                // see developer.android.com AIDL guidance: always trap RemoteException
                // from calls on a bound service.
                service = null
                FileLogger.log(this@MainActivity, "START FAILED — ${e.message}")
            }
            refreshStatus()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            statusView.text = "Service disconnected"
            FileLogger.log(this@MainActivity, "STOP — service disconnected unexpectedly")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        FileLogger.log(this, "ENTER — receiver app opened")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        statusView = TextView(this).apply { textSize = 20f; setPadding(0, 0, 0, 32) }
        val startBtn = Button(this).apply { text = "Start" }
        val stopBtn = Button(this).apply { text = "Stop" }

        startBtn.setOnClickListener { requestShizuku() }
        stopBtn.setOnClickListener {
            try {
                service?.stop()
                FileLogger.log(this, "STOP — user pressed Stop")
            } catch (e: RemoteException) {
                service = null
                FileLogger.log(this, "STOP CALL FAILED — ${e.message}")
            }
            refreshStatus()
        }

        layout.addView(statusView)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        setContentView(layout)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        statusView.text = "IP: ${localIp()}   Port: $port\nPress Start."

        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                refreshStatus()
                mainHandler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun installCrashLogger() {
        // Java/Android standard chained-handler pattern: wrap the existing
        // default handler so an uncaught exception on any thread gets written
        // to the same Download log before the normal crash/kill proceeds.
        // A crash bypasses onDestroy, which is why EXIT never gets logged for
        // a crashed session — this call is what records the reason instead.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            FileLogger.log(appContext, "CRASH — ${throwable.stackTraceToString()}")
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun requestShizuku() {
        // Shizuku.checkSelfPermission()/requireService() throw IllegalStateException
        // ("binder haven't been received") if called before the Shizuku binder is
        // connected — this is what crashed the app. pingBinder() is the library's
        // documented way to check that first instead of relying on a listener.
        if (!Shizuku.pingBinder()) {
            statusView.text = "Shizuku not connected — open the Shizuku app, start it, then try again"
            return
        }
        if (Shizuku.isPreV11()) {
            statusView.text = "Shizuku is too old, update it from its own app"
            return
        }
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bindService()
            Shizuku.shouldShowRequestPermissionRationale() ->
                statusView.text = "Permission was denied before — grant it from the Shizuku app"
            else -> Shizuku.requestPermission(requestCode)
        }
    }

    private fun bindService() {
        Shizuku.bindUserService(userServiceArgs, connection)
    }

    private fun refreshStatus() {
        val s = service
        statusView.text = try {
            if (s != null && s.isRunning()) {
                "Listening on ${localIp()}:$port\nPackets received: ${s.packetsReceived()}"
            } else {
                "IP: ${localIp()}   Port: $port\nNot running. ${s?.lastError().orEmpty()}"
            }
        } catch (e: RemoteException) {
            // The polling tick below runs every second; if the remote process died
            // between the previous tick and this one every one of these calls
            // (isRunning/packetsReceived/lastError) throws RemoteException on the
            // main thread. Without this guard that crashed the whole activity once
            // a second until the process was rebound.
            service = null
            FileLogger.log(this, "STOP — service process died unexpectedly (${e.message})")
            "IP: ${localIp()}   Port: $port\nService process died. Press Start to retry."
        }
    }

    private fun localIp(): String {
        return try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifi.connectionInfo.ipAddress
            if (ip == 0) "unknown" else
                "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
        } catch (e: Exception) {
            "unknown"
        }
    }

    override fun onDestroy() {
        FileLogger.log(this, "EXIT — receiver app closed")
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
