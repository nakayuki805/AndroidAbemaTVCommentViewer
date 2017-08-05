package net.nakayuki.abematvcommentviewer

import android.widget.ArrayAdapter
import android.widget.TextView
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View


/**
 * Created by yuki on 17/07/27.
 */

class CommentListAdapter(context: Context, resource: Int) : ArrayAdapter<CommentListItem>(context, resource) {
    private val mInflater: LayoutInflater
    private var mItems = ArrayList<CommentListItem>()
    private val mResource = resource
    var delta = 0

    init {
        mInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }
    fun add(comments: ArrayList<CommentListItem>){
        if(comments.size>0){
            var lastid = ""
            if(mItems.size>0){
                lastid = mItems.get(0).id
            }
            var lastIndex = comments.size-1
            for (i in 0..comments.size-1){
                if (comments.get(i).id==lastid){
                    lastIndex = i-1
                    break
                }
            }
            if(lastIndex>=0){
                for(i in (lastIndex) downTo 0){
                    val come = comments.get(i)
                    if(come.id != lastid){
                        mItems.add(0,come)
                    }else{
                        break
                    }
                }
            }
            delta = lastIndex+1
        }else{
            delta = 0
        }
    }
    fun limitAdd(comments: ArrayList<CommentListItem>, max: Int){
        add(comments)
        val over = mItems.size - max
        if(over>0){
            for (i in 0..over-1){
                mItems.removeAt(mItems.size-1)
            }
        }
    }

    override fun getCount(): Int {
        return mItems.size
    }

    override fun getItem(position: Int): CommentListItem {
        return mItems.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun clear() {
        mItems.clear()
    }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View

        if (convertView != null) {
            view = convertView
        } else {
            view = mInflater.inflate(mResource, parent, false)
        }
        //view = mInflater.inflate(mResource, null)

        // リストビューに表示する要素を取得
        val item = mItems.get(position)

        // コメントを設定
        val commentText = view.findViewById(R.id.commentText) as TextView
        commentText.text = item.commentText
        val commentTimeText = view.findViewById(R.id.commentTimeText) as TextView
        commentTimeText.text = item.timeText
        //Log.d("debug", "set each item ct:"+item.commentText+" tt:"+item.timeText+" pos:"+position)
        return view
    }
}