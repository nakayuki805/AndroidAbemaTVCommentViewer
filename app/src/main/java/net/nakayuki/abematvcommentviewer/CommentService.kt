package net.nakayuki.abematvcommentviewer

import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.view.*
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Binder
import android.os.Handler
import android.support.v4.app.NotificationCompat
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.RelativeLayout
import java.util.*
import kotlin.collections.ArrayList


/**
 * Created by yuki on 17/07/24.
 */
class CommentService : Service() {

    private var view: View? = null
    private var windowManager: WindowManager? = null //= getApplicationContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var configReceiver: ConfigChangeReceiver? = null
    private var relativeLayout: RelativeLayout? = null
    private var commentview: MovingCommentView? = null
    private var commentFetchTimer: Timer? = null
    private val binder = CommentServiceBinder()
    var handler: Handler? = null
    var isCommentMoving = false
    var channelid = ""
    var channelname = ""
    var slotid = ""
    var currentSlotEndAt = 0L
    var lastCommentId = ""
    var serviceStartId = 1
    var onSlotInfo: ((String, String) -> Unit)? = null
    var onComment: ((ArrayList<CommentListItem>, Int) -> Unit)? = null
    var movingCommentMax = 30
    var movingCommentFps = 30
    var movingCommentSeconds = 6
    var movingCommentSize = 20
    override fun onCreate() {
        super.onCreate()
        windowManager = getApplicationContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        //Log.d("debug", "service oncreate")
        commentFetchTimer = Timer(true)
        commentFetchTimer?.schedule(object : TimerTask() {
            override fun run() {
                handler?.post {

                    //5秒ごとに番組情報更新とコメ取得
                    //Log.d("debug", "service: run 5sec in handler")
                    if((System.currentTimeMillis()/1000)>currentSlotEndAt){
                        //番組情報更新
                        val slot = getCurrentSlot(this@CommentService, channelid)
                        if(slot != null){
                            if(slotid != slot.getString("id")){
                                lastCommentId = ""
                            }
                            slotid = slot.getString("id")
                            currentSlotEndAt = slot.getLong("endAt")
                            onSlotInfo?.invoke(slot.getString("id"), slot.getString("title"))
                        }
                    }
                    if(slotid!=""){
                        fetchComment(this@CommentService, slotid, {jsonObj ->
                            if(jsonObj!=null){
                                val commentNum = jsonObj.getInt("count")
                                val jsonComments = jsonObj.getJSONArray("comments")
                                val comments = ArrayList<CommentListItem>()
                                for (i in 0..jsonComments.length()-1){
                                    val comeObj = jsonComments.getJSONObject(i)
                                    if(comeObj.getString("id")!=lastCommentId){
                                        comments.add(CommentListItem(comeObj.getString("id"), comeObj.getString("message"), comeObj.getLong("createdAtMs")/1000))
                                    }else{
                                        break
                                    }
                                }
                                if (isCommentMoving && comments.size>0 && lastCommentId!=""){
                                    for (i in 0..comments.size-1){
                                        Handler().postDelayed({
                                            commentview?.putComment(comments.get(i).commentText)
                                        }, (5000*(comments.size-1-i)/comments.size).toLong())
                                    }

                                }
                                lastCommentId = if(jsonComments.length()>0){jsonComments.getJSONObject(0).getString("id")}else{"empty-dummy"}
                                onComment?.invoke(comments, commentNum)
                            }
                        })
                    }
                }
            }
        },1000,5000)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        serviceStartId = startId
        //Log.d("debug", "onstartcommand startid:"+startId)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCommentMoving()
        commentFetchTimer?.cancel()
        commentFetchTimer = null
        //Log.d("debug", "ondestroy")
    }

    override fun onBind(intent: Intent): IBinder? {
        //Log.d("debug", "onbind")
        return binder
    }
    override fun onUnbind(intent: Intent): Boolean {
        //Log.d("debug", "onunbind")
        return true
    }

    //画面回転イベント
    open class ConfigChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {}
    }
    open inner class CommentServiceBinder : Binder() {
        fun getService(): CommentService{
            return this@CommentService
        }
    }

    fun startCommentMoving(){
        stopCommentMoving()
        isCommentMoving = true
        // inflaterの生成
        val layoutInflater = LayoutInflater.from(this)
        val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                0,0,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP

        // レイアウトファイルからInfalteするViewを作成
        view = layoutInflater.inflate(R.layout.overlay_comment, null)
        relativeLayout = view?.findViewById(R.id.overlay_relativeLayout) as RelativeLayout
        commentview = view?.findViewById(R.id.overlay_view) as MovingCommentView

        // Viewを画面上に追加
        windowManager?.addView(view, params)
        //Log.d("debug", "start moving.")

        //画面回転検知
        val filter = IntentFilter("android.intent.action.CONFIGURATION_CHANGED")
        configReceiver = object : ConfigChangeReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val wm = getSystemService(
                        Context.WINDOW_SERVICE) as WindowManager
                val dm = DisplayMetrics()
                wm.defaultDisplay.getMetrics(dm)
                setViewSize()
                //Log.d("debug", "onconfigchange")
            }
        }
        registerReceiver(configReceiver, filter)
        //フォアグラウンド通知
        val notification = NotificationCompat.Builder(this)
        val intent = Intent(this, CommentListActivity::class.java)
        intent.putExtra("chid", channelid)
        intent.putExtra("chname", channelname)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        notification.setContentTitle(channelname).setContentText("コメント流し中").setContentIntent(pendingIntent).setSmallIcon(R.mipmap.comeview_icon)
        startForeground(serviceStartId, notification.build())
        //コメ流しスタート
        commentview?.movingCommentMax = movingCommentMax
        commentview?.movingFps = movingCommentFps
        commentview?.movingSeconds = movingCommentSeconds
        commentview?.movingCommentSize = movingCommentSize
        commentview?.startMoving()

    }
    fun stopCommentMoving(){
        // Viewを削除
        if(isCommentMoving){
            isCommentMoving = false
            commentview?.stopMoving()
            windowManager?.removeView(view)
            unregisterReceiver(configReceiver);
            stopForeground(true)
        }
        //Log.d("debug", "stop moving.")
    }

    fun setViewSize(){
        if(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
            //横
            relativeLayout?.gravity = Gravity.CENTER
            val lp = commentview?.layoutParams as ViewGroup.MarginLayoutParams
            lp.topMargin = 0
            commentview?.layoutParams = lp

        }else{
            //縦
            relativeLayout?.gravity = Gravity.TOP
            val lp = commentview?.layoutParams as ViewGroup.MarginLayoutParams
            lp.topMargin = resources.getDimensionPixelSize(R.dimen.comment_header_margin)
            commentview?.layoutParams = lp
        }
    }

    //CommentListActivityからアクセスする関数群
    fun setChannel(chid: String, chname: String){
        val oldchid = channelid
        channelid = chid
        channelname = chname
        val slot = getCurrentSlot(this, chid)
        if(slot != null){
            if(slotid != slot.getString("id")){
                lastCommentId = ""
            }
            slotid = slot.getString("id")
            currentSlotEndAt = slot.getLong("endAt")
            handler?.post {
                onSlotInfo?.invoke(slot.getString("id"), slot.getString("title"))
            }
        }
        //Log.d("debug", "chname set in service. chid:"+chid+",curSlot:"+slot?.getString("id"))
        if (isCommentMoving && oldchid != chid){
            stopCommentMoving()
            startCommentMoving()
        }
    }
    fun getIsCommentMoving(): Boolean{
        return isCommentMoving
    }
    fun setOnCommentFunc(callback: (ArrayList<CommentListItem>, Int) -> Unit){
        onComment = callback
    }
    fun setOnSlotInfoFunc(callback: (String, String) -> Unit){
        onSlotInfo = callback
    }
    fun getCommentAll(lastid: String, callback: (ArrayList<CommentListItem>, Int, Boolean) -> Unit) {
        if (slotid != "") {
            fetchComment(this@CommentService, slotid, { jsonObj ->
                if (jsonObj != null) {
                    val commentNum = jsonObj.getInt("count")
                    val jsonComments = jsonObj.getJSONArray("comments")
                    val comments = ArrayList<CommentListItem>()
                    var continueFlag = false
                    for (i in 0..jsonComments.length() - 1) {
                        val comeObj = jsonComments.getJSONObject(i)
                        if(comeObj.getString("id")!=lastid){
                            comments.add(CommentListItem(comeObj.getString("id"), comeObj.getString("message"), comeObj.getLong("createdAtMs")/1000))
                        }else{
                            continueFlag = true
                            break
                        }
                    }
                    //Log.d("debug", "service: getcommentall comments.size:"+comments.size+",cf:"+continueFlag+",lastid:"+lastid)
                    handler?.post {
                        callback(comments, commentNum, continueFlag)
                    }
                }
            })
        }
    }

}