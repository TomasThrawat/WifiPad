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
            service?.start(port)
            refreshStatus()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            statusView.text = "Service disconnected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        statusView = TextView(this).apply { textSize = 20f; setPadding(0, 0, 0, 32) }
        val startBtn = Button(this).apply { text = "Start" }
        val stopBtn = Button(this).apply { text = "Stop" }

        startBtn.setOnClickListener { requestShizuku() }
        stopBtn.setOnClickListener { service?.stop(); refreshStatus() }

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

    private fun requestShizuku() {
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
        statusView.text = if (s != null && s.isRunning()) {
            "Listening on ${localIp()}:$port\nPackets received: ${s.packetsReceived()}"
        } else {
            "IP: ${localIp()}   Port: $port\nNot running. ${s?.lastError().orEmpty()}"
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
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
