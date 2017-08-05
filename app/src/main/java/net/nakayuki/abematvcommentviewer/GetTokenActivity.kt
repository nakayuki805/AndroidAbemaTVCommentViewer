package net.nakayuki.abematvcommentviewer

import android.app.ProgressDialog
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class GetTokenActivity : AppCompatActivity() {

    private var isPageFinished: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_get_token)
        this.isPageFinished = false
        //プログレスダイアログ
        val progressDialog = ProgressDialog(this)
        progressDialog.setTitle("トークン取得中")
        progressDialog.setMessage("AbemaTVのページからトークンを取得しています...")
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progressDialog.show()
        //WebViewでAbemaTVトップにアクセス
        val getTokenWebview = findViewById(R.id.getTokenWebview) as WebView
        getTokenWebview.setWebViewClient(object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                //読み込み完了
                if (!isPageFinished) {
                    view.loadUrl("javascript:var i=setInterval(function(){var t=localStorage.getItem('abm_token');if(t){alert('abm_token='+t);clearInterval(i);}},1000);")
                    isPageFinished = true
                }
            }

        })
        //tokenを取得できたときの処理(alert経由で受け取る)
        getTokenWebview.setWebChromeClient(object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                if (message.startsWith("abm_token=")) {
                    try {
                        val context = applicationContext
                        val startPoint = message.indexOf("=") + 1
                        val token = message.substring(startPoint)
                        Toast.makeText(context, "トークン取得完了", Toast.LENGTH_SHORT).show()
                        progressDialog.dismiss()
                        //設定に保存
                        val pref = PreferenceManager.getDefaultSharedPreferences(context)
                        pref.edit().putString("abm_token", token).apply()
                        finish()// Activityを終了し前に戻る
                        return true
                    } finally {
                        result.confirm()
                    }
                } else {
                    return false
                }
            }
        })
        //AbemaTVトップを読み込み
        getTokenWebview.loadUrl("https://abema.tv")
        //js,localStorageを許可する
        getTokenWebview.settings.javaScriptEnabled = true
        getTokenWebview.settings.domStorageEnabled = true

    }
}
