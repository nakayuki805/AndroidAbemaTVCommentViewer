package net.nakayuki.abematvcommentviewer

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import org.json.JSONObject
import android.app.AlertDialog
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.DialogInterface
import android.net.Uri
import android.support.v4.widget.SwipeRefreshLayout
import android.util.Log
import android.widget.*
import org.json.JSONArray
import java.io.IOException


class MainActivity : AppCompatActivity() {

    var timetableVersion = ""
    var resumeFlag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val context = applicationContext
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val token = pref.getString("abm_token", "")
        if (token === "") {
            //tokenが未セットなので設定画面に飛ばす
            timetableVersion = "not_fetched" //トークン設定後メイン画面に戻ってきたときに取得するように
            Toast.makeText(context, "最初にトークンを設定してください", Toast.LENGTH_LONG).show()
            val intent = Intent(application, SettingsActivity::class.java)
            startActivity(intent)
        } else {
            createChannelsList()

            //バージョン確認
            try {

                val versionCheckUrl = "https://abema.nakayuki.net/android-comeviewer/version.json"
                object : MyAsyncTask() {
                    override fun doInBackground(vararg params: Void): String? {
                        return httpGet(versionCheckUrl)
                    }
                    override fun onPostExecute(retstr: String) {
                        val jsonObj = JSONObject(retstr)
                        val newVersionCode = jsonObj.getInt("newVersionCode")
                        val pkgInfo: PackageInfo
                        try {
                            pkgInfo = packageManager.getPackageInfo(packageName, 0)
                            val versionCode = pkgInfo.versionCode
                            if(versionCode < newVersionCode){
                                //Toast.makeText(context, "新しいバージョンがあります。現:"+versionCode+",新:"+newVersionCode, Toast.LENGTH_SHORT).show()
                                AlertDialog.Builder(this@MainActivity)
                                        .setTitle("新しいバージョンがあります")
                                        .setMessage("新しいバージョンをインストールするためにダウンロードページを開きますか？")
                                        .setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
                                            // OK button pressed
                                            val updatePageUrl = "https://abema.nakayuki.net/android-comeviewer/"
                                            val uri = Uri.parse(updatePageUrl)
                                            val intent = Intent(Intent.ACTION_VIEW, uri)
                                            startActivity(intent)
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show()
                            }
                        } catch (e: PackageManager.NameNotFoundException) {
                            e.printStackTrace()
                        }

                    }
                }.execute()
            }catch (e: Exception){
                Toast.makeText(context, "アップデート確認失敗", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }

        }

        val chListView = findViewById(R.id.chList) as ListView
        chListView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, pos, id ->
            // 選択アイテムを取得
            val intent = Intent(application, CommentListActivity::class.java)
            val channel = localTimetableJson(context).getJSONArray("channels").getJSONObject(pos)
            intent.putExtra("chid", channel.getString("id"))
            intent.putExtra("chname", channel.getString("name"))
            startActivity(intent)
        }
        val chListRefresh = findViewById(R.id.chListRefresh) as SwipeRefreshLayout
        chListRefresh.setColorSchemeResources(R.color.material_blue_grey_800, R.color.material_blue_grey_900, R.color.material_blue_grey_950, R.color.material_grey_900)
        chListRefresh.setOnRefreshListener { ->
            createChannelsList()
            //Toast.makeText(context, "チャンネル一覧を更新しています", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onResume() {
        super.onResume()
        //ローカルファイルから再読み込み
        if (timetableVersion == "not_fetched" && resumeFlag) {
            createChannelsList()
        } else if(timetableVersion!=""){
            try {
                val jsonObj = localTimetableJson(this@MainActivity)
                val currentVersion = jsonObj.getString("version")
                //Toast.makeText(this@MainActivity, "read local file, curver:"+currentVersion, Toast.LENGTH_SHORT).show()
                if(timetableVersion != currentVersion){
                    setListChannels(jsonObj.getJSONArray("channels"))
                }
            }catch (e: IOException) {
            }
        }
        resumeFlag = true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId


        if (id == R.id.settingsMenu) {
            val settingIntent = android.content.Intent(this, SettingsActivity::class.java)
            startActivity(settingIntent)
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun createChannelsList(){
        //チャンネル一覧を読み込む
        val context = applicationContext
        val chListRefresh = findViewById(R.id.chListRefresh) as SwipeRefreshLayout
        try {
            chListRefresh.isRefreshing = true
            fetchTimetable(context, {jsonObj ->
                //success
                timetableVersion = jsonObj.getString("version")
                setListChannels(jsonObj.getJSONArray("channels"))
                chListRefresh.isRefreshing = false
            }, {errorMsg ->
                //error
                if(errorMsg == "authorization header must exist"){
                    Toast.makeText(context, "設定画面からトークンを取得してください", Toast.LENGTH_LONG).show()
                }else{
                    Toast.makeText(context, "エラー:" + errorMsg, Toast.LENGTH_SHORT).show()
                }
                chListRefresh.isRefreshing = false
            })
        } catch (e: Exception){
            Toast.makeText(context, "チャンネル一覧取得時に例外が発生しました。", Toast.LENGTH_SHORT).show()
            chListRefresh.isRefreshing = false
            Log.e("debug","createChannelList exception", e)
        }

    }
    fun setListChannels(channels: JSONArray){
        val chListView = findViewById(R.id.chList) as ListView
        val arrayAdapter = ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_list_item_1)
        chListView.adapter = arrayAdapter

        arrayAdapter.clear()
        for (i in 0..channels.length() - 1) {
            val chname = channels.getJSONObject(i).getString("name")
            arrayAdapter.add(chname)
        }
        arrayAdapter.notifyDataSetChanged()
    }

}
