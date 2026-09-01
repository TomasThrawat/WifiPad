package com.wifipad.controller

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var padView: GamepadView
    private lateinit var statusView: TextView
    private lateinit var ipField: EditText
    private lateinit var sender: UdpSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("wifipad", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 8)
        }
        ipField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "TV IP address"
            setText(prefs.getString("tv_ip", ""))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val connectBtn = Button(this).apply { text = "Connect" }
        statusView = TextView(this).apply { setPadding(16, 0, 16, 0) }

        bar.addView(ipField)
        bar.addView(connectBtn)
        bar.addView(statusView)

        padView = GamepadView(this, null)

        root.addView(bar)
        root.addView(padView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        sender = UdpSender(padView.state)
        sender.onError = { msg -> runOnUiThread { statusView.text = "Error: $msg" } }

        connectBtn.setOnClickListener {
            val host = ipField.text.toString().trim()
            if (host.isEmpty()) {
                statusView.text = "Enter the TV's IP"
                return@setOnClickListener
            }
            prefs.edit().putString("tv_ip", host).apply()
            sender.start(host, Protocol.DEFAULT_PORT)
            statusView.text = "Sending to $host:${Protocol.DEFAULT_PORT}"
        }
    }

    override fun onDestroy() {
        sender.stop()
        super.onDestroy()
    }
}
