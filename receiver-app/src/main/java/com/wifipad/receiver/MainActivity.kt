package com.wifipad.receiver

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import rikka.shizuku.Shizuku
import java.net.Inet4Address
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE = 9001
        private const val BLACK = Color.BLACK
        private const val PANEL = 0xFF101010.toInt()
        private const val RAISED = 0xFF171717.toInt()
        private const val LINE = 0xFF303030.toInt()
        private const val MUTED = 0xFF9B9B9B.toInt()
        private const val WHITE = Color.WHITE
        private const val ERROR = 0xFFFF7777.toInt()
        private const val GREEN = 0xFF9BD3A9.toInt()
        private const val GREEN_PANEL = 0xFF16231B.toInt()
        private const val GREEN_LINE = 0xFF345640.toInt()
    }

    private val port = Protocol.DEFAULT_PORT
    private val handler = Handler(Looper.getMainLooper())
    private var service: IGamepadService? = null
    private var pendingStart: ((String?) -> Unit)? = null
    private var isBinding = false
    private var busy = false

    private lateinit var statusTitle: MaterialTextView
    private lateinit var statusPill: MaterialTextView
    private lateinit var addressValue: MaterialTextView
    private lateinit var portValue: MaterialTextView
    private lateinit var packetsValue: MaterialTextView
    private lateinit var shizukuInfo: MaterialTextView
    private lateinit var receiverError: MaterialTextView
    private lateinit var messageView: MaterialTextView
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, GamepadUserService::class.java.name)
    ).daemon(false).processNameSuffix("gamepad").debuggable(false).version(1)

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == REQUEST_CODE) {
            if (grant == PackageManager.PERMISSION_GRANTED) bindService()
            else finishStart("Shizuku permission was denied.")
        }
    }

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IGamepadService.Stub.asInterface(binder)
            isBinding = false
            val callback = pendingStart
            if (callback != null) {
                var error: String? = null
                try {
                    val current = service ?: throw IllegalStateException("Receiver service unavailable.")
                    if (!current.start(port)) {
                        error = current.lastError().ifBlank { "The receiver could not start." }
                    }
                } catch (failure: Exception) {
                    error = failure.message ?: "The receiver service stopped."
                }
                pendingStart = null
                busy = false
                callback(error)
            }
            refreshStatus()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            isBinding = false
            refreshStatus()
        }

        override fun onBindingDied(name: ComponentName) {
            service = null
            isBinding = false
            finishStart("The Shizuku user service stopped unexpectedly.")
            refreshStatus()
        }

        override fun onNullBinding(name: ComponentName) {
            service = null
            isBinding = false
            finishStart("The Shizuku user service returned no binder.")
            refreshStatus()
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            refreshStatus()
            handler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        val screen = createScreen()
        setContentView(screen)
        screen.applySystemBarInsets()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun createScreen(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BLACK)
            isFillViewport = true
            clipToPadding = false
        }
        val width = (resources.displayMetrics.widthPixels - dp(48)).coerceAtMost(dp(560)).coerceAtLeast(dp(280))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, dp(32))
            layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        content.addView(createBrandHeader())
        addSpace(content, 36)

        val panel = card()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        statusTitle = label("Receiver stopped", 20f, WHITE, true)
        header.addView(statusTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusPill = label("OFFLINE", 10f, MUTED, true).apply {
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(7), dp(11), dp(7))
            background = rounded(RAISED, LINE, 40)
        }
        header.addView(statusPill)
        body.addView(header)

        addSpace(body, 20)
        addressValue = addMetric(body, "TV address", "Checking network…")
        addSpace(body, 14)
        portValue = addMetric(body, "UDP port", port.toString())
        addSpace(body, 14)
        packetsValue = addMetric(body, "Packets received", "0")
        addSpace(body, 18)
        val divider = View(this).apply { setBackgroundColor(LINE) }
        body.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        addSpace(body, 16)

        shizukuInfo = label("Shizuku is not connected.", 12f, MUTED)
        body.addView(shizukuInfo)
        receiverError = label("", 13f, ERROR).apply { visibility = View.GONE }
        addSpace(body, 10)
        body.addView(receiverError)
        messageView = label("", 13f, ERROR).apply { visibility = View.GONE }
        addSpace(body, 8)
        body.addView(messageView)
        panel.addView(body)
        content.addView(panel)

        addSpace(content, 16)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        startButton = MaterialButton(this).apply {
            text = "Start receiver"
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            cornerRadius = dp(16)
            setTextColor(BLACK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(WHITE)
            setOnClickListener { startReceiver() }
        }
        actions.addView(startButton, LinearLayout.LayoutParams(0, dp(54), 1f))
        addSpaceHorizontal(actions, 12)
        stopButton = MaterialButton(this).apply {
            text = "Stop receiver"
            isAllCaps = false
            textSize = 14f
            cornerRadius = dp(16)
            setTextColor(WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(RAISED)
            strokeWidth = dp(1)
            strokeColor = android.content.res.ColorStateList.valueOf(LINE)
            setOnClickListener { stopReceiver() }
        }
        actions.addView(stopButton, LinearLayout.LayoutParams(0, dp(54), 1f))
        content.addView(actions)

        addSpace(content, 18)
        val note = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        note.addView(label("◈", 18f, MUTED))
        addSpaceHorizontal(note, 10)
        note.addView(label(
            "Starting requires Shizuku permission to create the virtual gamepad.",
            12f,
            MUTED
        ))
        content.addView(note)
        scroll.addView(content)
        return scroll
    }

    private fun createBrandHeader(): LinearLayout {
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val iconCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = LINE
            setCardBackgroundColor(RAISED)
        }
        iconCard.addView(MaterialTextView(this).apply {
            text = "▣"
            gravity = Gravity.CENTER
            setTextColor(WHITE)
            textSize = 27f
        }, FrameLayout.LayoutParams(dp(58), dp(58)))
        header.addView(iconCard, LinearLayout.LayoutParams(dp(58), dp(58)))
        addSpace(header, 22)
        header.addView(label("ANDROID TV RECEIVER", 11f, MUTED, true).apply { letterSpacing = 0.16f })
        addSpace(header, 7)
        header.addView(label("WifiPad", 36f, WHITE, true).apply { letterSpacing = -0.035f })
        addSpace(header, 7)
        header.addView(label("Receive controller input over your local network.", 14f, MUTED))
        return header
    }

    private fun addMetric(parent: LinearLayout, title: String, initial: String): MaterialTextView {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(title, 13f, MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val value = label(initial, 14f, WHITE, true).apply { gravity = Gravity.END }
        row.addView(value, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        parent.addView(row)
        return value
    }

    private fun refreshStatus() {
        if (!::statusTitle.isInitialized) return
        val snapshot = statusSnapshot()
        statusTitle.text = if (snapshot.running) "Receiver active" else "Receiver stopped"
        statusPill.text = if (snapshot.running) "LISTENING" else "OFFLINE"
        statusPill.setTextColor(if (snapshot.running) GREEN else MUTED)
        statusPill.background = if (snapshot.running) rounded(GREEN_PANEL, GREEN_LINE, 40)
            else rounded(RAISED, LINE, 40)
        addressValue.text = snapshot.address
        portValue.text = NumberFormat.getIntegerInstance(Locale.getDefault()).format(snapshot.port)
        packetsValue.text = NumberFormat.getIntegerInstance(Locale.getDefault()).format(snapshot.packets)
        shizukuInfo.text = when {
            !snapshot.shizukuConnected -> "Shizuku is not connected."
            snapshot.permissionGranted -> "Shizuku connected and permission granted."
            else -> "Shizuku connected; permission required."
        }
        receiverError.text = snapshot.lastError
        receiverError.visibility = if (snapshot.lastError.isBlank()) View.GONE else View.VISIBLE
        startButton.isEnabled = !busy && !snapshot.running
        stopButton.isEnabled = !busy && snapshot.running
        startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
        stopButton.alpha = if (stopButton.isEnabled) 1f else 0.55f
    }

    private data class Snapshot(
        val running: Boolean,
        val address: String,
        val port: Int,
        val packets: Long,
        val lastError: String,
        val shizukuConnected: Boolean,
        val permissionGranted: Boolean
    )

    private fun statusSnapshot(): Snapshot {
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
        val connected = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        val permission = try {
            connected && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
        return Snapshot(running, localIp(), port, packets, lastError, connected, permission)
    }

    private fun startReceiver() {
        if (busy) return
        busy = true
        messageView.visibility = View.GONE
        refreshStatus()
        val current = service
        if (current != null) {
            try {
                if (current.isRunning()) {
                    busy = false
                    refreshStatus()
                    return
                }
            } catch (_: RemoteException) {
                service = null
            }
        }
        if (!Shizuku.pingBinder()) {
            finishStart("Connect Shizuku on this TV first.")
            return
        }
        if (Shizuku.isPreV11()) {
            finishStart("The installed Shizuku version is too old.")
            return
        }
        pendingStart = { error ->
            busy = false
            if (!error.isNullOrBlank()) {
                messageView.text = error
                messageView.visibility = View.VISIBLE
            } else {
                messageView.visibility = View.GONE
            }
            refreshStatus()
        }
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bindService()
            Shizuku.shouldShowRequestPermissionRationale() ->
                finishStart("Grant WifiPad permission from the Shizuku app.")
            else -> Shizuku.requestPermission(REQUEST_CODE)
        }
        refreshStatus()
    }

    private fun bindService() {
        if (isBinding) return
        isBinding = true
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (error: Exception) {
            isBinding = false
            finishStart(error.message ?: "Could not bind the Shizuku service.")
        }
    }

    private fun finishStart(error: String) {
        val callback = pendingStart
        pendingStart = null
        isBinding = false
        busy = false
        if (callback != null) callback(error) else {
            messageView.text = error
            messageView.visibility = View.VISIBLE
        }
        refreshStatus()
    }

    private fun stopReceiver() {
        if (busy) return
        busy = true
        refreshStatus()
        try {
            service?.stop()
            messageView.visibility = View.GONE
        } catch (error: RemoteException) {
            service = null
            messageView.text = error.message ?: "Could not stop the receiver."
            messageView.visibility = View.VISIBLE
        } finally {
            busy = false
            refreshStatus()
        }
    }

    private fun localIp(): String = try {
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

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        pendingStart = null
        super.onDestroy()
    }

    private fun card(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(22).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = LINE
        setCardBackgroundColor(PANEL)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        MaterialTextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        setCornerRadius(dp(radius).toFloat())
        setStroke(dp(1), stroke)
    }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun addSpaceHorizontal(parent: LinearLayout, width: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(dp(width), 1))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
