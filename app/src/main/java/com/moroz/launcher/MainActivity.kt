package com.moroz.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.setBackgroundColor(android.graphics.Color.BLACK)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AppBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* Блокируем кнопку назад */ }

    inner class AppBridge {

        /** Возвращает JSON-массив ВСЕХ установленных приложений с иконками */
        @JavascriptInterface
        fun getApps(): String {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val jsonArray = JSONArray()

            for (app in apps) {
                // Исключаем сам лаунчер
                if (app.packageName == packageName) continue
                if (pm.getLaunchIntentForPackage(app.packageName) == null) continue

                val obj = JSONObject()
                obj.put("name", pm.getApplicationLabel(app).toString())
                obj.put("package", app.packageName)
                
                try {
                    val icon = pm.getApplicationIcon(app.packageName)
                    val bitmap = Bitmap.createBitmap(
                        icon.intrinsicWidth.coerceAtLeast(48),
                        icon.intrinsicHeight.coerceAtLeast(48),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    icon.setBounds(0, 0, canvas.width, canvas.height)
                    icon.draw(canvas)
                    
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    obj.put("icon", "data:image/png;base64,$base64")
                } catch (e: Exception) {
                    obj.put("icon", "")
                }
                jsonArray.put(obj)
            }
            return jsonArray.toString()
        }

        /** Запуск приложения */
        @JavascriptInterface
        fun launchApp(packageName: String) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        /** Открытие системных настроек Android */
        @JavascriptInterface
        fun openSettings() {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        /**
         * Получение уровня заряда Bluetooth-пульта.
         * Возвращает процент (0-100) или -1, если не удалось получить.
         */
        @JavascriptInterface
        fun getRemoteBattery(): Int {
            try {
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter ?: return -1
                
                // Если Bluetooth выключен
                if (!bluetoothAdapter.isEnabled) return -1
                
                // Получаем список сопряженных устройств
                val pairedDevices = bluetoothAdapter.bondedDevices ?: return -1
                
                for (device in pairedDevices) {
                    // Ищем пульт по классу устройства (обычно это PERIPHERAL или REMOTE_CONTROL)
                    val deviceClass = device.bluetoothClass?.deviceClass
                    if (deviceClass == android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL) {
                        // Пытаемся получить заряд через скрытый API (работает на большинстве ТВ)
                        val method = device.javaClass.getMethod("getBatteryLevel")
                        val batteryLevel = method.invoke(device) as Int
                        if (batteryLevel in 0..100) {
                            return batteryLevel
                        }
                    }
                }
            } catch (e: Exception) {
                // Если произошла ошибка (нет разрешений, API не поддерживается), возвращаем -1
                return -1
            }
            return -1
        }
    }
}
