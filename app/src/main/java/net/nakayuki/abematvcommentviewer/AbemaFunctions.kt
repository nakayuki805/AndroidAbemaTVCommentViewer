package net.nakayuki.abematvcommentviewer

import android.content.Context
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by yuki on 17/07/22.
 */

fun fetchTimetable(context: Context, callback: (JSONObject) -> Unit, errorCallback: (String) -> Unit){
    val df = SimpleDateFormat("yyyyMMdd", Locale.JAPAN)
    val dateStr = df.format(Date(System.currentTimeMillis()))
    val mediaPath = "/v1/media?dateFrom="+dateStr+"&dateTo="+dateStr
    object : MyAsyncTask() {
        override fun doInBackground(vararg params: Void): String? {
            return abemaGet(context, mediaPath)
        }
        override fun onPostExecute(retstr: String) {
            val jsonObj = JSONObject(retstr)
            if (jsonObj.has("version")){
                val fileOut = context.openFileOutput("timetable.json", AppCompatActivity.MODE_PRIVATE);
                fileOut.write(retstr.toByteArray());
                callback(jsonObj)
            } else{
                var errorMsg = ""
                if(jsonObj.has("error")) errorMsg = jsonObj.getString("error")
                if(jsonObj.has("message")) errorMsg = jsonObj.getString("message")
                errorCallback(errorMsg)
            }
        }
    }.execute()
    //Log.d("get", "fetchTimetable")
}
fun fetchComment(context: Context, slotid: String, callback: (JSONObject?) -> Unit){
    val commentPath = "/v1/slots/"+slotid+"/comments?limit=100"
    object : MyAsyncTask() {
        override fun doInBackground(vararg params: Void): String? {
            return abemaGet(context, commentPath)
        }
        override fun onPostExecute(retstr: String) {
            val jsonObj = JSONObject(retstr)
            if (jsonObj.has("comments") && jsonObj.has("count")){
                if (!jsonObj.isNull("comments") && jsonObj.has("count")){
                    callback(jsonObj)
                }else{
                    callback(null)
                }
            } else{
                Log.d("debug", "fetchComment failed slotid:"+slotid+"retstr:"+retstr)
                callback(null)
            }
        }
    }.execute()
}
fun postComment(context: Context, slotid: String, message: String, callback: (JSONObject?) -> Unit){
    val abemaAPIbase = "https://api.abema.io"
    val postPath = "/v1/slots/"+slotid+"/comments"
    val postJson = JSONObject()
    postJson.accumulate("message", message)
    postJson.accumulate("share", JSONObject.NULL)
    val url = abemaAPIbase+postPath
    try {
        object : MyAsyncTask() {
            override fun doInBackground(vararg params: Void): String? {
                val pref = PreferenceManager.getDefaultSharedPreferences(context)
                val token = pref.getString("abm_token", "")
                val client = OkHttpClient()
                val postBytes = postJson.toString().toByteArray(charset = Charsets.UTF_8)
                val postBody = RequestBody.create(MediaType.parse("application/json"), postBytes)
                val request = Request.Builder().url(url).post(postBody).header("Authorization", "bearer "+token).build()
                val response = client.newCall(request).execute()
                val respBody = response.body()
                if (response != null && respBody != null){
                    return respBody.string()
                }else{
                    return "{\"error\":\"http error\"}"
                }
            }
            override fun onPostExecute(retstr: String) {
                val jsonObj = JSONObject(retstr)
                if (jsonObj.has("id")){
                    callback(jsonObj)
                } else{
                    Log.d("debug", "postcomment failed slotid:"+slotid+" retstr:"+retstr+" postJson:"+postJson.toString())
                    callback(null)
                }
            }
        }.execute()
    }catch (e: Exception){
        e.printStackTrace()
        callback(null)
    }
}

fun getCurrentSlot(context: Context, chid: String): JSONObject?{
    val chSchedules = localTimetableJson(context).getJSONArray("channelSchedules")
    var slots = JSONArray()
    for(i in 0..chSchedules.length()-1){
        val schedule = chSchedules.getJSONObject(i)
        if (schedule.getString("channelId") == chid){
            slots = schedule.getJSONArray("slots")
            break
        }
    }
    val currentTime = System.currentTimeMillis()/1000
    for(i in 0..slots.length()-1){
        val slot = slots.getJSONObject(i)
        if(slot.getLong("startAt")<=currentTime && currentTime<=slot.getLong("endAt")){
            return slot
        }

    }
    if(slots.getJSONObject(slots.length()-1).getLong("endAt")<currentTime){
        //番組が全て過去->新しい番組表を取得
        fetchTimetable(context,{jsonObj->

        }, {errorMsg ->

        })
    }
    return null
}
fun localTimetableJson(context: Context): JSONObject{
    val fileIn = context.openFileInput("timetable.json")
    val readBytes = ByteArray(fileIn.available())
    fileIn.read(readBytes)
    return JSONObject(String(readBytes))
}

fun httpGet(url: String): String {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    val response = client.newCall(request).execute()
    val respBody = response.body()
    if (response != null && respBody != null){
        return respBody.string()
    }else{
        return "{\"error\":\"http error\"}"
    }
}
fun abemaGet(context: Context, path: String): String{
    try {
        //Log.d("get", "abemaGet path:"+path)
        val abemaAPIbase = "https://api.abema.io"
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val token = pref.getString("abm_token", "")
        val client = OkHttpClient()
        val request = Request.Builder().url(abemaAPIbase+path).header("Authorization", "bearer "+token).build()
        val response = client.newCall(request).execute()
        val respBody = response.body()
        if (response != null && respBody != null){
            return respBody.string()
        }else{
            return "{\"error\":\"http error\"}"
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return "{\"error\":\"exception occured\"}"
    }
}