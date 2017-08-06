package net.nakayuki.abematvcommentviewer

import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.*
import android.view.MenuItem

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction()
                .replace(android.R.id.content, PrefsFragment()).commit()
        //back button
        //val actionBar = actionBar
        //actionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    class PrefsFragment : PreferenceFragment() {
        fun onSharedPreferenceChanged() {
            val maxListCommentPref = preferenceScreen.findPreference("maxListComment") as EditTextPreference
            maxListCommentPref.summary = maxListCommentPref.text
            val maxMovingCommentPref = preferenceScreen.findPreference("maxMovingComment") as EditTextPreference
            maxMovingCommentPref.summary = maxMovingCommentPref.text
            val movingCommentFpsPref = preferenceScreen.findPreference("movingCommentFps") as EditTextPreference
            movingCommentFpsPref.summary = movingCommentFpsPref.text
            val movingCommentSecondsPref = preferenceScreen.findPreference("movingCommentSeconds") as EditTextPreference
            movingCommentSecondsPref.summary = movingCommentSecondsPref.text
            val movingCommentSizePref = preferenceScreen.findPreference("movingCommentSize") as EditTextPreference
            movingCommentSizePref.summary = movingCommentSizePref.text

        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener{ sharedPreferences, key ->
            onSharedPreferenceChanged()
        }


        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.preferences)
            onSharedPreferenceChanged()
        }



        override fun onResume() {
            super.onResume()
            //token自動取得画面から戻ってきたときに設定画面のtokenを更新
            val pref = PreferenceManager.getDefaultSharedPreferences(activity)
            val tokenPref = preferenceScreen.findPreference("abm_token") as EditTextPreference
            tokenPref.text = pref.getString("abm_token", "")
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener)
        }
        override fun onPause() {
            super.onPause()
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

}
