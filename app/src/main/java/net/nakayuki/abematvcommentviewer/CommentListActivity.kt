package net.nakayuki.abematvcommentviewer

import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.annotation.TargetApi
import android.provider.Settings
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.*
import android.os.Handler
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.design.widget.FloatingActionButton
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.*


class CommentListActivity : AppCompatActivity() {

    val OVERLAY_PERMISSION_REQ_CODE = 1000
    var serviceIntent:Intent? = null //Intent(this@CommentListActivity,CommentService::class.java)
    private var commentService: CommentService? = null
    private var isBound: Boolean = false
    private var slotId = ""
    private var commentAdapter: CommentListAdapter? = null
    var pref: SharedPreferences? = null
    var maxComment = 100
    var isClearChChange = true
    var movingCommentMax = 30
    var movingCommentFps = 30
    var movingCommentSeconds = 6
    var movingCommentSize = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceIntent = Intent(this@CommentListActivity,CommentService::class.java)
        setContentView(R.layout.activity_comment_list)

        //back button
        //val actionBar = actionBar
        //actionBar!!.setDisplayHomeAsUpEnabled(true)

        pref = PreferenceManager.getDefaultSharedPreferences(this)
        if (pref!=null){
            maxComment = Math.max(pref!!.getString("maxListComment", "100").toInt(), 100)
            isClearChChange = pref!!.getBoolean("isClearChChange", true)
            movingCommentMax = Math.max(pref!!.getString("movingCommentMax", "30").toInt(), 0)
            movingCommentFps = Math.max(pref!!.getString("movingCommentFps", "30").toInt(), 1)
            movingCommentSeconds = Math.max(pref!!.getString("movingCommentSeconds", "6").toInt(), 1)
            movingCommentSize = Math.max(pref!!.getString("movingCommentSize", "20").toInt(), 1)
        }

        val chid = intent.getStringExtra("chid")
        val chname = intent.getStringExtra("chname")
        setTitle(chname)

        //Toast.makeText(context,"chid:"+chid,Toast.LENGTH_SHORT).show()
        val sendCommentBtn = findViewById(R.id.sendCommentBtn) as FloatingActionButton
        sendCommentBtn.setOnClickListener { view ->
            //コメント送信
            val comeEdit = EditText(this)
            val filters = arrayOf(InputFilter.LengthFilter(50))
            comeEdit.filters = filters
            comeEdit.maxLines = 1
            comeEdit.inputType = InputType.TYPE_CLASS_TEXT
            comeEdit.imeOptions = EditorInfo.IME_ACTION_SEND
            val builder = AlertDialog.Builder(this)
            builder.setTitle("コメントを入力")
            builder.setView(comeEdit)
            builder.setPositiveButton("送信", { dialog, i ->
                // send
                val comment = comeEdit.text.toString()
                if(slotId!="" && comment!=""){
                    postComment(this@CommentListActivity, slotId, comment, {jsonObj ->
                        if(jsonObj==null){
                            Toast.makeText(this, "コメント投稿に失敗しました", Toast.LENGTH_SHORT).show();
                        }else{
                            Toast.makeText(this, "コメントを投稿しました", Toast.LENGTH_SHORT).show();
                        }
                    })
                }
            })
            builder.setNegativeButton("キャンセル",  { dialog, i ->
                // cancel
            })
            val dialog = builder.show()
            comeEdit.setOnEditorActionListener { CEview, i, keyEvent ->
                if (i == EditorInfo.IME_ACTION_SEND){
                    val comment = comeEdit.text.toString()
                    if(slotId!="" && comment!=""){
                        postComment(this@CommentListActivity, slotId, comment, {jsonObj ->
                            if(jsonObj==null){
                                Toast.makeText(this, "コメント投稿に失敗しました", Toast.LENGTH_SHORT).show();
                            }else{
                                Toast.makeText(this, "コメントを投稿しました", Toast.LENGTH_SHORT).show();
                            }
                        })
                    }
                    dialog.dismiss()
                    return@setOnEditorActionListener true
                }
                return@setOnEditorActionListener false
            }
        }
        //list view
        val commentListView = findViewById(R.id.commentList) as ListView
        commentAdapter = CommentListAdapter(this@CommentListActivity, R.layout.comment_list_item)
        commentAdapter?.density = resources.displayMetrics.density
        commentListView.adapter = commentAdapter
        //long tap
        commentListView.setOnItemLongClickListener { adapterView, view, pos, itemid ->
            val commentText = commentAdapter?.getItem(pos)?.commentText
            val comeEdit = EditText(this)
            comeEdit.setText(commentText)
            comeEdit.setSelectAllOnFocus(true)
            val builder = AlertDialog.Builder(this)
            builder.setTitle("選択範囲を...")
            builder.setView(comeEdit)
            builder.setPositiveButton("コピー", { dialog, i ->
                val text = comeEdit.text.toString().substring(comeEdit.selectionStart, comeEdit.selectionEnd)
                val clip = ClipData.newPlainText("selected_text",text)
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.primaryClip = clip
                Toast.makeText(this, "「"+text+"」をコピーしました", Toast.LENGTH_SHORT).show();
            })/*
            builder.setNeutralButton("NGワード", { dialog, i ->
                val text = comeEdit.text.toString().substring(comeEdit.selectionStart, comeEdit.selectionEnd)

            })*/
            builder.setNegativeButton("キャンセル",  { dialog, i ->
                // cancel
            })
            builder.show()
            return@setOnItemLongClickListener true
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val titleLayout = findViewById(R.id.titleLayout) as LinearLayout
        val slotTitleText = findViewById(R.id.slotTitleText) as TextView
        val commentNumText = findViewById(R.id.commentNumText) as TextView
        slotTitleText.width = titleLayout.width - commentNumText.width - (32*resources.displayMetrics.density).toInt()
        //Log.d("debug", "onWFC tlW:"+titleLayout.width+" cntW:"+commentNumText.width)
    }
    override fun onResume() {
        super.onResume()
        //コメ一覧表示スタート
        //Log.d("debug", "onresume isBound:"+isBound)
        if(!isBound) {
            bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
            isBound = true
        }
    }
    override fun onPause() {
        super.onPause()
        //コメ一覧表示ストップ
        //Log.d("debug", "onpause isBound:"+isBound)
        if(isBound){
            unbindService(connection)
            isBound = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_comelist, menu)

        val movingCommentBtn = menu.findItem(R.id.movingCommentBtn)
        if(isCommentMoving()){
            movingCommentBtn.setIcon(R.drawable.moving_comment_on_icon)
        }else{
            movingCommentBtn.setIcon(R.drawable.moving_comment_icon)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.toAbemaBtn -> {
                try {
                    val onAirPage = "https://abema.tv/now-on-air/"+intent.getStringExtra("chid")
                    val uri = Uri.parse(onAirPage)
                    val intent = packageManager.getLaunchIntentForPackage("tv.abema")
                    intent.setData(uri)
                    startActivity(intent)
                }catch (e: Exception){
                    Toast.makeText(this, "AbemaTVアプリを起動できません", Toast.LENGTH_SHORT).show();
                }
            }
            R.id.movingCommentBtn -> {
                var flg = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flg = checkPermission();
                }
                if(flg){
                    val isMoving = isCommentMoving()
                    //Log.d("debug", "mc btn flg=true, ism:"+isMoving)
                    if(isMoving){
                        stopMovingComment()
                    }else{
                        startMovingComment()
                    }
                    invalidateOptionsMenu();
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun startMovingComment(){
        //Log.d("debug", "startMovingComment()")
        startService(serviceIntent)
        commentService?.movingCommentMax = movingCommentMax
        commentService?.movingCommentFps = movingCommentFps
        commentService?.movingCommentSeconds = movingCommentSeconds
        commentService?.movingCommentSize = movingCommentSize
        commentService?.startCommentMoving()
    }
    fun stopMovingComment(){
        //Log.d("debug", "stopMovingComment()")
        commentService?.stopCommentMoving()
        stopService(serviceIntent)
    }

    fun isCommentServiceWorking(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (serviceInfo in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (CommentService::class.java.getName() == serviceInfo.service.className) {
                return true
            }
        }
        return false
    }
    fun isCommentMoving():Boolean {
        if(commentService !== null){
            val ret = commentService?.getIsCommentMoving()
            //Log.d("debug", "iscm() ret:"+ret)
            if(ret != null){
                return ret
            }else return false
        }else return false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // サービスとの接続確立
            //Log.d("debug", "connected servie")
            commentService = (service as CommentService.CommentServiceBinder).getService()
            commentService?.handler = Handler()
            commentService?.setOnSlotInfoFunc { id, title ->
                //番組情報を取得した時(変化した時)
                //Log.d("debug", "comelistActivity:onSlotInfo")
                val titleText = findViewById(R.id.slotTitleText) as TextView
                titleText.text = title
                if (isClearChChange && id != slotId){
                    commentAdapter?.clear()
                    commentAdapter?.notifyDataSetChanged()
                }
                slotId = id
            }
            commentService?.setOnCommentFunc { comments: ArrayList<CommentListItem>, num ->
                //コメントを取得した時
                //Log.d("debug", "comelistActivity:onComment size:"+comments.size+" num:"+num+" ca:"+(commentAdapter!=null))
                if(commentAdapter!=null){
                    val commentListView = findViewById(R.id.commentList) as ListView
                    commentAdapter!!.limitAdd(comments, maxComment)
                    val pos = commentListView.getFirstVisiblePosition()
                    val topView = commentListView.getChildAt(0)
                    if(topView!=null && commentAdapter!!.delta!=0){
                        val top = topView.top
                        commentAdapter!!.notifyDataSetChanged()
                        commentListView.setSelectionFromTop(pos+commentAdapter!!.delta, top)
                        if(pos==0&&top==0){
                            commentListView.smoothScrollToPositionFromTop(pos, 0)
                        }
                    }else{
                        commentAdapter?.notifyDataSetChanged()
                    }

                }
                val comenumText = findViewById(R.id.commentNumText) as TextView
                comenumText.text = num.toString()+"コメント"
            }
            commentService?.setChannel(intent.getStringExtra("chid"), intent.getStringExtra("chname"))
            //コメント再取得
            var lastid = ""
            if (commentAdapter!=null && commentAdapter!!.count>0){
                val lastItem = commentAdapter?.getItem(0)
                if(lastItem!=null){
                    lastid = lastItem.id
                }
            }
            commentService?.getCommentAll(lastid, {comments, commentNum, continueFlag ->
                if(!continueFlag){
                    commentAdapter?.clear()
                }
                commentService?.onComment?.invoke(comments, commentNum)
            })
            invalidateOptionsMenu()

        }

        override fun onServiceDisconnected(className: ComponentName) {
            // サービスとの切断(異常系処理)
            commentService = null
            Toast.makeText(this@CommentListActivity, "コメントサービスとの接続が切れました",
                    Toast.LENGTH_SHORT).show()
            //Log.d("debug", "disconnected servie")
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun checkPermission(): Boolean{ // 許可を得ようとする場合はfalseを返す
        val canOverlay = Settings.canDrawOverlays(this)
        //Log.d("debug", "check perm. can:"+canOverlay)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
            return false
        }else{
            return true
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "画面上にコメントを重ねる許可が得られませんでした", Toast.LENGTH_SHORT).show();
            }else{
                startMovingComment()
            }
        }
    }
}
