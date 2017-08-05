package net.nakayuki.abematvcommentviewer

import android.os.AsyncTask

/**
 * Created by yuki on 17/07/22.
 */
open class MyAsyncTask : AsyncTask<Void, Void, String>() {
    override fun doInBackground(vararg params: Void): String? {
        return null
    }
    override fun onPostExecute(retstr: String) {}
}