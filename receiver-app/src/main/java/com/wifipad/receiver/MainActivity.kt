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
import android.widget.LinearLayout
import android.widget.LinearLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import android.content.res.ColorStateList
import kotlin.math.roundToInt
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
            } catch (e: RemoteException) {
                // Remote (Shizuku user service) process died/never came up before this
                // call landed. Drop the stale binder instead of crashing the caller —
                // see developer.android.com AIDL guidance: always trap RemoteException
                // from calls on a bound service.
                service = null
            }
            refreshStatus()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            statusView.text = "Service disconnected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val surface = color(com.google.android.material.R.attr.colorSurface)
        val surfaceContainer = color(com.google.android.material.R.attr.colorSurfaceContainer)
        val outline = color(com.google.android.material.R.attr.colorOutlineVariant)
        val onSurface = color(com.google.android.material.R.attr.colorOnSurface)
        val secondaryContainer = color(com.google.android.material.R.attr.colorSecondaryContainer)
        val onSecondaryContainer = color(com.google.android.material.R.attr.colorOnSecondaryContainer)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
            setPadding(dp(16), 0, dp(16), dp(24))
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "WiFiPad Receiver"
            setTitleTextColor(onSurface)
            setBackgroundColor(surfaceContainer)
            elevation = 0f
            contentInsetStartWithNavigation = 0
        }
        layout.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(64)
        ))

        val statusCard = MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = outline
            setCardBackgroundColor(surfaceContainer)
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        val heading = MaterialTextView(this).apply {
            text = "Receiver status"
            setTextColor(onSurface)
            textSize = 18f
        }

        statusView = MaterialTextView(this).apply {
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            textSize = 15f
            setPadding(0, dp(10), 0, 0)
        }

        cardContent.addView(heading)
        cardContent.addView(statusView)
        statusCard.addView(cardContent)

        layout.addView(statusCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
        })

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val startBtn = MaterialButton(this).apply {
            text = "Start"
            minHeight = dp(52)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(18)
        }

        val stopBtn = MaterialButton(this).apply {
            text = "Stop"
            minHeight = dp(52)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(18)
            backgroundTintList = ColorStateList.valueOf(secondaryContainer)
            setTextColor(onSecondaryContainer)
        }

        buttonRow.addView(startBtn, LinearLayout.LayoutParams(0, dp(52), 1f))
        buttonRow.addView(stopBtn, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginStart = dp(12)
        })

        layout.addView(buttonRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(16)
        })

        val hint = MaterialTextView(this).apply {
            text = "This app listens for WiFiPad packets and injects them through the Shizuku user service."
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            textSize = 13f
            setPadding(dp(4), dp(18), dp(4), 0)
        }
        layout.addView(hint)

        startBtn.setOnClickListener { requestShizuku() }
        stopBtn.setOnClickListener {
            try {
                service?.stop()
            } catch (e: RemoteException) {
                service = null
            }
            refreshStatus()
        }

        setContentView(layout)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        statusView.text = "IP: " + localIp() + "   Port: " + port + "
Press Start."
        refreshStatus()

        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                refreshStatus()
                mainHandler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun color(attr: Int): Int =
        MaterialColors.getColor(this, attr, 0)

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
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
