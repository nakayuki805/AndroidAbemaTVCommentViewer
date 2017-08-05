package net.nakayuki.abematvcommentviewer

import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by yuki on 17/07/27.
 */
class CommentListItem(commentid:String, comment: String, time: Long) {
    val commentText = comment
    val id = commentid
    val df = SimpleDateFormat("HH:mm:ss", Locale.JAPAN)
    val timeText = df.format(Date(time*1000))

}