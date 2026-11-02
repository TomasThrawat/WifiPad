package com.wifipad.controller

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.wifipad/controller"
        private const val PREFS = "wifipad"
        private const val PREF_HOST = "tv_ip"
    }

    override fun getInitialRoute(): String = "/controller"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getSavedHost" -> result.success(
                        getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_HOST, "")
                    )
                    "connect" -> {
                        val host = call.argument<String>("host")?.trim().orEmpty()
                        if (host.isBlank()) {
                            result.error("INVALID_HOST", "Enter the TV IP address or host name.", null)
                        } else {
                            getSharedPreferences(PREFS, MODE_PRIVATE)
                                .edit().putString(PREF_HOST, host).apply()
                            startActivity(
                                Intent(this, GamepadActivity::class.java).apply {
                                    putExtra(GamepadActivity.EXTRA_HOST, host)
                                }
                            )
                            result.success(true)
                        }
                    }
                    "openSettings" -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}
