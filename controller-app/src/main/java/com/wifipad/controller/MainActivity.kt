package com.wifipad.controller

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.result.ActivityResultLauncher
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PREFS = "wifipad"
        private const val PREF_HOST = "tv_ip"
        private const val BLACK = Color.BLACK
        private const val PANEL = 0xFF101010.toInt()
        private const val RAISED = 0xFF171717.toInt()
        private const val LINE = 0xFF303030.toInt()
        private const val MUTED = 0xFF9B9B9B.toInt()
        private const val WHITE = Color.WHITE
        private const val ERROR = 0xFFFF7777.toInt()
    }

    private lateinit var hostInput: TextInputEditText
    private lateinit var connectButton: MaterialButton
    private lateinit var messageView: MaterialTextView
    private lateinit var discoveryButton: MaterialButton
    private lateinit var discoveryList: LinearLayout
    private var selectedTvId: String? = null
    private var discovery: TvDiscovery? = null
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) acceptPairingQr(contents)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val screen = createScreen()
        setContentView(screen)
        screen.applySystemBarInsets()
        hostInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_HOST, "").orEmpty())
        val saved = TvPairingStore.all(this)
        if (saved.isNotEmpty()) {
            selectedTvId = saved.first().tvId
            saved.forEach(::addPairedTvButton)
        }
    }

    private fun createScreen(): android.view.View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BLACK)
            isFillViewport = true
            clipToPadding = false
        }
        val width = (resources.displayMetrics.widthPixels - dp(48)).coerceAtMost(dp(520)).coerceAtLeast(dp(280))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, dp(32))
            layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        content.addView(createBrandHeader(
            getString(R.string.phone_controller),
            getString(R.string.phone_controller_subtitle),
            "⌘"
        ))
        addSpace(content, 44)
        content.addView(label(getString(R.string.connect_tv_title), 25f, WHITE, true))
        addSpace(content, 8)
        content.addView(label(getString(R.string.connect_tv_description), 14f, MUTED))
        addSpace(content, 22)

        val panel = card()
        val panelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.tv_ip_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
            boxBackgroundColor = RAISED
            setBoxCornerRadii(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
            setBoxStrokeColor(WHITE)
            setDefaultHintTextColor(ColorStateList.valueOf(MUTED))
        }
        hostInput = TextInputEditText(this).apply {
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            hint = "192.168.1.20"
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    connect()
                    true
                } else false
            }
        }
        inputLayout.addView(hostInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panelContent.addView(inputLayout)
        addSpace(panelContent, 12)
        messageView = label("", 13f, ERROR).apply { visibility = android.view.View.GONE }
        panelContent.addView(messageView)
        addSpace(panelContent, 18)

        connectButton = MaterialButton(this).apply {
            text = getString(R.string.open_controller)
            isAllCaps = false
            textSize = 15f
            setTextColor(BLACK)
            typeface = Typeface.DEFAULT_BOLD
            cornerRadius = dp(16)
            backgroundTintList = ColorStateList.valueOf(WHITE)
            minimumHeight = dp(56)
            setOnClickListener { connect() }
        }
        panelContent.addView(connectButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ))
        panel.addView(panelContent)
        content.addView(panel)

        addSpace(content, 14)
        val scanButton = MaterialButton(this).apply {
            text = getString(R.string.scan_tv_qr)
            isAllCaps = false
            cornerRadius = dp(16)
            setOnClickListener {
                val options = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Scan the QR shown by WifiPad Receiver")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                }
                qrLauncher.launch(options)
            }
        }
        content.addView(scanButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        addSpace(content, 8)
        discoveryButton = MaterialButton(this).apply {
            text = getString(R.string.discover_tvs)
            isAllCaps = false
            cornerRadius = dp(16)
            setOnClickListener { startDiscovery() }
        }
        content.addView(discoveryButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        discoveryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addSpace(content, 8)
        content.addView(discoveryList)

        addSpace(content, 18)
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        info.addView(label("ⓘ", 18f, MUTED))
        addSpaceHorizontal(info, 10)
        info.addView(label(getString(R.string.network_note), 12f, MUTED))
        content.addView(info)

        scroll.addView(content)
        return scroll
    }

    private fun connect() {
        val host = hostInput.text?.toString()?.trim().orEmpty()
        val pairing = selectedTvId?.let { TvPairingStore.get(this, it) }
            ?: TvPairingStore.all(this).firstOrNull { it.host == host }
        if (pairing == null) {
            messageView.text = getString(R.string.pair_tv_first)
            messageView.visibility = android.view.View.VISIBLE
            return
        }
        startController(pairing.copy(host = host.ifBlank { pairing.host }))
    }

    private fun acceptPairingQr(raw: String) {
        val pairing = runCatching {
            val json = JSONObject(raw)
            require(json.getString("app") == "WifiPad" && json.getInt("version") == 2)
            TvPairingStore.fromJson(json) ?: error("invalid pairing")
        }.getOrNull()
        if (pairing == null) {
            messageView.text = getString(R.string.invalid_pairing_qr)
            messageView.visibility = android.view.View.VISIBLE
            return
        }
        TvPairingStore.save(this, pairing)
        selectedTvId = pairing.tvId
        addPairedTvButton(pairing)
        startController(pairing)
    }

    private fun startController(pairing: TvPairing) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit { putString(PREF_HOST, pairing.host) }
        selectedTvId = pairing.tvId
        hostInput.setText(pairing.host)
        startActivity(Intent(this, GamepadActivity::class.java).putExtra(GamepadActivity.EXTRA_TV_ID, pairing.tvId))
    }

    private fun addPairedTvButton(pairing: TvPairing) {
        if (!::discoveryList.isInitialized) return
        val old = (0 until discoveryList.childCount).map { discoveryList.getChildAt(it) }
            .filterIsInstance<MaterialButton>().firstOrNull { it.tag == pairing.tvId }
        if (old != null) discoveryList.removeView(old)
        val button = MaterialButton(this).apply {
            tag = pairing.tvId
            text = getString(R.string.paired_tv_label, pairing.name, pairing.host)
            isAllCaps = false
            cornerRadius = dp(14)
            setOnClickListener { startController(pairing) }
        }
        discoveryList.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
    }

    private fun startDiscovery() {
        if (android.os.Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"), 3703)
            return
        }
        discovery?.stop()
        discoveryList.removeAllViews()
        discoveryButton.text = getString(R.string.searching_tvs)
        discovery = TvDiscovery(this) { device ->
            runOnUiThread {
                val paired = TvPairingStore.get(this, device.tvId)
                if (paired != null) {
                    val updated = paired.copy(host = device.host, port = device.port, name = device.name)
                    TvPairingStore.save(this, updated)
                    addPairedTvButton(updated)
                } else {
                    val found = MaterialTextView(this).apply {
                        text = getString(R.string.discovered_tv_label, device.name, device.host, getString(R.string.scan_tv_qr))
                        setTextColor(WHITE)
                        textSize = 13f
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        contentDescription = "${getString(R.string.pair_tv_first)} ${device.name} ${device.host}"
                    }
                    discoveryList.addView(found)
                }
            }
        }.also { it.start() }
        discoveryButton.postDelayed({
            discovery?.stop()
            discoveryButton.text = getString(R.string.discover_tvs)
        }, 12_000L)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 3703 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startDiscovery()
        } else if (requestCode == 3703) {
            messageView.text = getString(R.string.local_network_permission_denied_retry)
            messageView.visibility = android.view.View.VISIBLE
        }
    }

    override fun onDestroy() {
        discovery?.stop()
        discovery = null
        super.onDestroy()
    }

    private fun createBrandHeader(eyebrow: String, subtitle: String, symbol: String): LinearLayout {
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val iconCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = LINE
            setCardBackgroundColor(RAISED)
        }
        iconCard.addView(MaterialTextView(this).apply {
            text = symbol
            gravity = Gravity.CENTER
            setTextColor(WHITE)
            textSize = 29f
        }, FrameLayout.LayoutParams(dp(58), dp(58)))
        header.addView(iconCard, LinearLayout.LayoutParams(dp(58), dp(58)))
        addSpace(header, 22)
        header.addView(label(eyebrow, 11f, MUTED, true).apply { letterSpacing = 0.16f })
        addSpace(header, 7)
        header.addView(label("WifiPad", 36f, WHITE, true).apply { letterSpacing = -0.035f })
        addSpace(header, 7)
        header.addView(label(subtitle, 14f, MUTED))
        return header
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
            includeFontPadding = true
        }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun addSpaceHorizontal(parent: LinearLayout, width: Int) {
        parent.addView(android.view.View(this), LinearLayout.LayoutParams(dp(width), 1))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
