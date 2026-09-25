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
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private var service: IGamepadService? = null
    private lateinit var statusView: MaterialTextView
    private val port = Protocol.DEFAULT_PORT
    private val requestCode = 9001
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == requestCode) {
            if (grant == PackageManager.PERMISSION_GRANTED) bindService()
            else statusView.text = getString(R.string.status_shizuku_denied)
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
            } catch (_: RemoteException) {
                service = null
            }
            refreshStatus()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            statusView.text = getString(R.string.status_disconnected)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface
                )
            )
            setPadding(dp(20), dp(12), dp(20), dp(20))
        }

        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
            elevation = 0f
        }

        val statusCard = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOutlineVariant
            )
            setCardBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurfaceContainer
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        }

        statusView = MaterialTextView(this).apply {
            text = getString(R.string.status_press_start)
            gravity = Gravity.CENTER
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
            )
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        statusCard.addView(statusView)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val startButton = MaterialButton(this).apply {
            text = getString(R.string.start)
            isAllCaps = false
            cornerRadius = dp(18)
            minHeight = dp(60)
            layoutParams = LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                marginEnd = dp(6)
            }
        }

        val stopButton = MaterialButton(this).apply {
            text = getString(R.string.stop)
            isAllCaps = false
            cornerRadius = dp(18)
            minHeight = dp(60)
            layoutParams = LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                marginStart = dp(6)
            }
        }

        startButton.setOnClickListener { requestShizuku() }
        stopButton.setOnClickListener {
            try {
                service?.stop()
            } catch (_: RemoteException) {
                service = null
            }
            refreshStatus()
        }

        actions.addView(startButton)
        actions.addView(stopButton)

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )
        root.addView(statusCard)
        root.addView(actions)
        setContentView(root)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        statusView.text = getString(
            R.string.status_press_start_with_network,
            localIp(),
            port
        )

        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                refreshStatus()
                mainHandler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            statusView.text = getString(R.string.status_shizuku_not_connected)
            return
        }
        if (Shizuku.isPreV11()) {
            statusView.text = getString(R.string.status_shizuku_too_old)
            return
        }
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bindService()
            Shizuku.shouldShowRequestPermissionRationale() ->
                statusView.text = getString(R.string.status_shizuku_denied_before)
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
                getString(
                    R.string.status_listening,
                    localIp(),
                    port,
                    s.packetsReceived()
                )
            } else {
                getString(
                    R.string.status_not_running,
                    localIp(),
                    port,
                    s?.lastError().orEmpty()
                )
            }
        } catch (_: RemoteException) {
            service = null
            getString(R.string.status_service_died, localIp(), port)
        }
    }

    private fun localIp(): String {
        return try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifi.connectionInfo.ipAddress
            if (ip == 0) "unknown"
            else String.format(
                "%d.%d.%d.%d",
                ip and 0xFF,
                (ip shr 8) and 0xFF,
                (ip shr 16) and 0xFF,
                (ip shr 24) and 0xFF
            )
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
