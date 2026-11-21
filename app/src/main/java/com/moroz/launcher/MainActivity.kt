package com.moroz.launcher

import android.annotation.SuppressLint
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

        // Устанавливаем черный фон для WebView, чтобы не было белой вспышки при старте
        webView.setBackgroundColor(android.graphics.Color.BLACK)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            
            // 👇 КРИТИЧЕСКИ ВАЖНЫЕ НАСТРОЙКИ ДЛЯ ТВ (решают проблему с левым краем)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
        }

        // Чтобы ссылки открывались внутри приложения
        webView.webViewClient = WebViewClient()

        // Подключаем наш "мост" для JS
        webView.addJavascriptInterface(AppBridge(), "AndroidBridge")
        
        // Загружаем HTML из ресурсов
        webView.loadUrl("file:///android_asset/index.html")
    }

    // Отключаем кнопку "Назад", чтобы лаунчер не закрывался случайно
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Ничего не делаем
    }

    inner class AppBridge {

        /** Возвращает JSON-массив ВСЕХ установленных приложений с иконками */
        @JavascriptInterface
        fun getApps(): String {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val jsonArray = JSONArray()

            for (app in apps) {
                // Исключаем сам лаунчер из списка
                if (app.packageName == packageName) continue
                
                // Берем только те, что можно запустить
                if (pm.getLaunchIntentForPackage(app.packageName) == null) continue

                val obj = JSONObject()
                obj.put("name", pm.getApplicationLabel(app).toString())
                obj.put("package", app.packageName)
                
                // Конвертация иконки в Base64 PNG
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

        /** Запуск приложения по packageName */
        @JavascriptInterface
        fun launchApp(packageName: String) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        /** Открывает системные настройки Android */
        @JavascriptInterface
        fun openSettings() {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
}
