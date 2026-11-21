package com.moroz.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AppBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    // Отключаем кнопку "Назад", чтобы не выходить из лаунчера
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Ничего не делаем — лаунчер не должен закрываться
    }

    inner class AppBridge {

        /** Возвращает JSON-массив ВСЕХ установленных приложений с launcher-интентом */
        @JavascriptInterface
        fun getApps(): String {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val jsonArray = JSONArray()

            for (app in apps) {
                // Только приложения, которые можно запустить
                if (pm.getLaunchIntentForPackage(app.packageName) == null) continue

                val obj = JSONObject()
                obj.put("name", pm.getApplicationLabel(app).toString())
                obj.put("package", app.packageName)
                // Иконка как base64 PNG
                try {
                    val icon = pm.getApplicationIcon(app.packageName)
                    val drawable = icon
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                    obj.put("icon", "data:image/png;base64,$base64")
                } catch (e: Exception) {
                    obj.put("icon", "")
                }
                jsonArray.put(obj)
            }
            return jsonArray.toString()
        }

        /** Запускает приложение по package name */
        @JavascriptInterface
        fun launchApp(packageName: String) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        /** Открывает настройки Android (на случай если лаунчер нужно сменить) */
        @JavascriptInterface
        fun openSettings() {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
}
