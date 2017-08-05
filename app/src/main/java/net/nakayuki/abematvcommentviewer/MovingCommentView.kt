package net.nakayuki.abematvcommentviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.util.*


open class MovingCommentView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var aspectRatio: Float = 0f
    private val commentPaint = Paint()
    private val commentEdgePaint = Paint()
    private val rnd = Random()
    private var thread: Thread? = null
    private var movingComments = ArrayList<MovingComment>()
    private var isMoving = false
    var movingCommentMax = 30
    var movingFps = 30
    var movingSeconds = 6
    var movingCommentSize = 20
    var padding = 5
    var density = 1f

    init {
        val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.MovingCommentView,
                0, 0)

        try {
            aspectRatio = a.getFloat(R.styleable.MovingCommentView_aspectRatio, 0f)
        } finally {
            a.recycle()
        }

        if (aspectRatio < 0) aspectRatio = 0f

        commentPaint.isAntiAlias = true
        commentEdgePaint.isAntiAlias = true
        commentPaint.typeface = Typeface.DEFAULT_BOLD
        commentEdgePaint.typeface = Typeface.DEFAULT_BOLD
        commentPaint.textSize = 32f
        commentEdgePaint.textSize = 32f
        commentPaint.color = Color.WHITE
        commentEdgePaint.color = Color.BLACK
        commentPaint.style = Paint.Style.FILL
        commentEdgePaint.style = Paint.Style.STROKE
        commentPaint.strokeWidth = 0f
        commentEdgePaint.strokeWidth = 1f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthMeasureSpec = widthMeasureSpec
        var heightMeasureSpec = heightMeasureSpec
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = Math.round(widthSize * aspectRatio)

        setMeasuredDimension(widthSize, heightSize)

        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(widthSize, widthMode)
        heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(heightSize, heightMode)

        density = resources.displayMetrics.density

        commentPaint.textSize = density * movingCommentSize
        commentEdgePaint.textSize = density * movingCommentSize
        //Log.d("debug", "onMeasure density:"+density)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val padX = paddingLeft
        val padY = paddingTop
        if(canvas!=null){
            var i = 0
            while (i<movingComments.size){
                val mc = movingComments.get(i)
                canvas.drawText(mc.text, padX+mc.left, padY+mc.top, commentPaint)
                canvas.drawText(mc.text, padX+mc.left, padY+mc.top, commentEdgePaint)
                i++
            }
        }
    }
    fun startMoving(){
        if (!isMoving){
            isMoving = true
            thread = Thread(object: Runnable {
                override fun run() {
                    //コメ流しの座標計算処理
                    var beforeTime = System.currentTimeMillis()
                    var afterTime = beforeTime
                    val frameTime: Long = (1000/movingFps).toLong()
                    while (isMoving){
                        //val viewWidth = measuredWidth
                        var i = 0
                        while (i < movingComments.size){
                            val mc = movingComments.get(i)
                            mc.left = mc.left - mc.frameX
                            if (mc.left<-1*mc.width){
                                movingComments.removeAt(i)
                            }
                            i++
                        }
                        postInvalidate()

                        afterTime = System.currentTimeMillis()
                        val tookTime: Long = afterTime - beforeTime
                        val sleepTime: Long = frameTime - tookTime
                        if (sleepTime > 0){
                            try {
                                Thread.sleep(sleepTime)
                            }catch (e: Exception){

                            }
                        }
                        beforeTime = System.currentTimeMillis()
                    }
                }
            })
            thread?.start()
        }
    }
    fun stopMoving(){
        isMoving = false
        movingComments.clear()
        thread = null
    }
    fun putComment(text: String){
        val textWidth = commentPaint.measureText(text)
        val textMetrics = commentPaint.getFontMetrics()
        val viewHeight = measuredHeight
        val viewWidth = measuredWidth
        val textTop = rnd.nextInt((viewHeight + textMetrics.top - textMetrics.bottom - padding*2).toInt()) - textMetrics.top + padding
        val frameX = (viewWidth + textWidth) / (movingSeconds * movingFps)

        movingComments.add(MovingComment(text, textWidth, textTop, viewWidth.toFloat(), frameX))
        if(movingComments.size > movingCommentMax){
            movingComments.removeAt(movingComments.size-1)
        }
    }

    class MovingComment(val text: String, val width: Float, val top: Float, var left: Float, val frameX: Float)
}