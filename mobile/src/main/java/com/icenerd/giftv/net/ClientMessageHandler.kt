package com.icenerd.giftv.net


import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Message
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.icenerd.giftv.BuildConfig
import com.icenerd.giftv.MobileTVActivity
import com.icenerd.giftv.data.GIFTVDB
import com.icenerd.giftv.data.model.StatusModel
import com.icenerd.giftv.data.orm.StatusORM
import com.icenerd.giphy.data.model.GifModel
import com.icenerd.giphy.data.orm.GifORM
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference

class ClientMessageHandler(ctx: Context) : Handler() {
    private val context = WeakReference<Context>(ctx)
    override fun handleMessage(msg: Message) {
        if (msg.data.containsKey("json")) { // command to change state
            try {
                val json = JSONObject(msg.data.getString("json"))
                handleJSON(json)
            } catch (err: JSONException) {
                if(BuildConfig.DEBUG) err.printStackTrace()
            }
        }
    }
    @Throws(JSONException::class)
    private fun handleJSON(json: JSONObject) {
        var bSignal = false
        var statusModel: StatusModel? = null
        if (json.has(StatusORM.TABLE)) {
            var db: SQLiteDatabase? = null
            try {
                db = GIFTVDB(context.get()!!).writableDatabase
                statusModel = StatusModel(json.getJSONObject(StatusORM.TABLE))
                val orm = StatusORM(db)
                if (orm.save(statusModel)) {
                    db!!.close()
                    bSignal = true
                }
            } catch (err: JSONException) {
                if(BuildConfig.DEBUG) err.printStackTrace()
            } finally {
                if(db?.isOpen==true) db.close()
            }
        }

        if (statusModel != null && json.has(GifORM.TABLE)) { // this has gif data, save it
            if (BuildConfig.DEBUG) Log.d(TAG, "Saving Gifs")
            var db: SQLiteDatabase? = null
            try {
                db = GIFTVDB(context.get()!!).writableDatabase
                val current_time = System.currentTimeMillis()
                val terms = if (json.isNull("terms")) null else json.getString("terms")
                val jsonGifs = json.getJSONArray(GifORM.TABLE)
                val orm = GifORM(db)
                for (i in 0 until jsonGifs.length()) {
                    val model = GifModel(jsonGifs.getJSONObject(i))
                    orm.save(model)
                    orm.tv_log(current_time, terms?:"", model)
                    if (BuildConfig.DEBUG) Log.d(TAG, "gif added to television log")
                }
            } catch (err: JSONException) {
                if(BuildConfig.DEBUG) err.printStackTrace()
            } finally {
                if(db?.isOpen==true) db.close()
            }
        }

        if (bSignal) LocalBroadcastManager.getInstance(context.get()!!).sendBroadcast(Intent(MobileTVActivity.SIGNAL_CHANGE_CHANNEL))
    }

}