package com.adrian.circleprogressbarlib

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.support.annotation.IntDef
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * author:RanQing
 * date:2018/9/1 0001 22:57
 * description:继承自LinearLayout，可设置环形进度或者响应进度变化。响应自身布局触摸事件时，clickable需要设置为true
 **/
class CircleProgressFrameLayout : FrameLayout {

    companion object {
        const val DEFAULT_MAX = 100
        const val MAX_DEGREE = 360f
        const val LINEAR_START_DEGREE = 90f

        //表盘式刻度线进度条
        const val LINE = 0L
        //实心扇形进度条
        const val SOLID = 1L
        //实心线形进度条
        const val SOLID_LINE = 2L

        //线性渐变
        const val LINEAR = 0L
        //径向渐变
        const val RADIAL = 1L
        //扫描渐变
        const val SWEEP = 2L

        const val STOP_ANIM_SIMPLE = 0L
        const val STOP_ANIM_REVERSE = 1L

        const val DEFAULT_START_DEGREE = -90f
        const val DEFAULT_LINE_COUNT = 45
        const val DEFAULT_LINE_WIDTH = 4f
        const val DEFAULT_PROGRESS_STROKE_WIDTH = 1f

        const val COLOR_FFF2A670 = "#fff2a670"
        const val COLOR_FFD3D3D5 = "#ffe3e3e5"
    }

    private val mProgressBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mProgressCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mProgressRectF = RectF()

    private var mRadius = 0f
    private var mCenterX = 0f
    private var mCenterY = 0f

    //进度
    var mProgress = 0
        set(value) {
            field = value
            invalidate()
        }
    //进度条最大值
    var mMax = DEFAULT_MAX
        set(value) {
            field = value
            invalidate()
        }

    //仅表盘式进度条有用，表示表盘刻度数量
    var mLineCount = 0
        set(value) {
            field = value
            invalidate()
        }
    //仅表盘式进度条有用，表示刻度线宽度
    var mLineWidth = 0f
        set(value) {
            field = value
            invalidate()
        }

    //进度条宽度
    var mProgressStrokeWidth = 0f
        set(value) {
            field = value
            mProgressRectF.inset(value / 2, value / 2)
            mProgressPaint.strokeWidth = value
            mProgressBackgroundPaint.strokeWidth = value
            invalidate()
        }

    //进度条进度开始颜色
    var mProgressStartColor = Color.BLACK
        set(value) {
            field = value
            updateProgressShader()
            invalidate()
        }
    //进度条进度结束颜色
    var mProgressEndColor = Color.BLACK
        set(value) {
            field = value
            updateProgressShader()
            invalidate()
        }

    //进度条背景色
    var mProgressBackgroundColor = Color.WHITE
        set(value) {
            field = value
            mProgressBackgroundPaint.color = value
            invalidate()
        }

    //控件中间填充色（仅表盘式及线形进度有效，扇形未填充）
    var mCenterColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            mProgressCenterPaint.color = value
            invalidate()
        }

    //进度条旋转起始角度.默认-90
    var mStartDegree = -90f
        set(value) {
            field = value
            invalidate()
        }
    //是否只在进度条之外绘制背景色
    var mDrawBackgroundOutsideProgress = false
        set(value) {
            field = value
            invalidate()
        }

    //动画是否已停止,此判断防止多次响应停止接口方法
    private var isStopedAnim = true

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(STOP_ANIM_SIMPLE, STOP_ANIM_REVERSE)
    annotation class StopAnimType

    //停止动画类型
    @StopAnimType
    var mStopAnimType = STOP_ANIM_SIMPLE

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(LINE, SOLID, SOLID_LINE)
    annotation class Style

    //进度条颜色样式
    @Style
    var mStyle = LINE
        set(value) {
            field = value
            mProgressPaint.style = if (value == SOLID) Paint.Style.FILL else Paint.Style.STROKE
            mProgressBackgroundPaint.style = if (value == SOLID) Paint.Style.FILL else Paint.Style.STROKE
            invalidate()
        }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(LINEAR, RADIAL, SWEEP)
    annotation class ShaderMode

    //画笔着色器
    @ShaderMode
    var mShader = LINEAR
        set(value) {
            field = value
            updateProgressShader()
            invalidate()
        }
    //进度及背景画笔绘制两端形状.Cap.ROUND(圆形线帽)、Cap.SQUARE(方形线帽)、Paint.Cap.BUTT(无线帽)
    var mCap: Paint.Cap = Paint.Cap.BUTT
        set(value) {
            field = value
            mProgressPaint.strokeCap = value
            mProgressBackgroundPaint.strokeCap = value
            invalidate()
        }

    //按下监听
    var mOnPressedListener: OnPressedListener? = null

    //进度条动画
    private var mAnimator: ValueAnimator? = null

    //是否支持连续进度加载
    var isContinuable: Boolean = false
        set(value) {
            field = value
            if (value) {
                mAnimator?.repeatCount = 0
            }
        }

    //是否关联响应子控件。即此值为true时按下子控件，进度条同步响应.此设置需要clickable值为true才有效
    var isLinkChildTouchEvent = false

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        if (context == null) return

        val a = context.obtainStyledAttributes(attrs, R.styleable.CircleProgressLayout, defStyleAttr, 0)

        mLineCount = a.getInt(R.styleable.CircleProgressLayout_cpl_line_count, CircleProgressLinearLayout.DEFAULT_LINE_COUNT)
        mStopAnimType = a.getInt(R.styleable.CircleProgressLayout_cpl_stop_anim_type, CircleProgressLinearLayout.STOP_ANIM_SIMPLE.toInt()).toLong()
        mStyle = a.getInt(R.styleable.CircleProgressLayout_cpl_style, CircleProgressLinearLayout.LINE.toInt()).toLong()
        mShader = a.getInt(R.styleable.CircleProgressLayout_cpl_shader, CircleProgressLinearLayout.LINEAR.toInt()).toLong()
        mCap = if (a.hasValue(R.styleable.CircleProgressLayout_cpl_stroke_cap)) Paint.Cap.values()[a.getInt(R.styleable.CircleProgressLayout_cpl_stroke_cap, 0)] else Paint.Cap.BUTT
        mLineWidth = a.getDimension(R.styleable.CircleProgressLayout_cpl_line_width, Utils.dip2px(context, CircleProgressLinearLayout.DEFAULT_LINE_WIDTH))
        mProgressStrokeWidth = a.getDimension(R.styleable.CircleProgressLayout_cpl_stroke_width, CircleProgressLinearLayout.DEFAULT_PROGRESS_STROKE_WIDTH)
        mProgressStartColor = a.getColor(R.styleable.CircleProgressLayout_cpl_start_color, Color.parseColor(CircleProgressLinearLayout.COLOR_FFF2A670))
        mProgressEndColor = a.getColor(R.styleable.CircleProgressLayout_cpl_end_color, Color.parseColor(CircleProgressLinearLayout.COLOR_FFF2A670))
        mProgressBackgroundColor = a.getColor(R.styleable.CircleProgressLayout_cpl_background_color, Color.parseColor(CircleProgressLinearLayout.COLOR_FFD3D3D5))
        mStartDegree = a.getFloat(R.styleable.CircleProgressLayout_cpl_start_degree, CircleProgressLinearLayout.DEFAULT_START_DEGREE)
        mDrawBackgroundOutsideProgress = a.getBoolean(R.styleable.CircleProgressLayout_cpl_drawBackgroundOutsideProgress, false)
        mCenterColor = a.getColor(R.styleable.CircleProgressLayout_cpl_center_color, Color.TRANSPARENT)
        isLinkChildTouchEvent = a.getBoolean(R.styleable.CircleProgressLayout_cpl_isLinkChildTouchEvent, false)
        isContinuable = a.getBoolean(R.styleable.CircleProgressLayout_cpl_continuable, false)

        a.recycle()

        initPaint()

        //必须加背景，不然显示不了动画
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun initPaint() {
        mProgressPaint.style = if (mStyle == SOLID) Paint.Style.FILL else Paint.Style.STROKE
        mProgressPaint.strokeWidth = mProgressStrokeWidth
        mProgressPaint.color = mProgressStartColor
        mProgressPaint.strokeCap = mCap

        mProgressBackgroundPaint.style = if (mStyle == SOLID) Paint.Style.FILL else Paint.Style.STROKE
        mProgressBackgroundPaint.strokeWidth = mProgressStrokeWidth
        mProgressBackgroundPaint.color = mProgressBackgroundColor
        mProgressBackgroundPaint.strokeCap = mCap

        mProgressCenterPaint.style = Paint.Style.FILL
        mProgressCenterPaint.color = mCenterColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mCenterX = w / 2f
        mCenterY = h / 2f

        mRadius = Math.min(mCenterX, mCenterY)
        mProgressRectF.top = mCenterY - mRadius
        mProgressRectF.bottom = mCenterY + mRadius
        mProgressRectF.left = mCenterX - mRadius
        mProgressRectF.right = mCenterX + mRadius

        updateProgressShader()

        //防止进度条被裁剪
        mProgressRectF.inset(mProgressStrokeWidth / 2, mProgressStrokeWidth / 2)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.save()
        canvas?.rotate(mStartDegree, mCenterX, mCenterY)
        drawProgress(canvas)
        canvas?.restore()

        drawCenterColor(canvas)
    }

    /**
     * 中间未填充颜色时，绘制居中颜色
     */
    private fun drawCenterColor(canvas: Canvas?) {
        if (mStyle == LINE || mStyle == SOLID_LINE) {
            canvas?.drawCircle(mCenterX, mCenterY, mRadius - mProgressStrokeWidth, mProgressCenterPaint)
        }
    }

    /**
     * 绘制进度样式
     */
    private fun drawProgress(canvas: Canvas?) {
        when (mStyle) {
            SOLID -> drawSolidProgress(canvas)
            SOLID_LINE -> drawSolidLineProgress(canvas)
            else -> drawLineProgress(canvas)
        }
    }

    /**
     * 居中绘制表盘式线状圆环
     */
    private fun drawLineProgress(canvas: Canvas?) {
        val unitDegrees: Float = (2f * Math.PI / mLineCount).toFloat()
        val outerCircleRadius = mRadius
        val interCircleRadius = mRadius - mLineWidth

        val progressLineCount = mProgress.toFloat() / mMax * mLineCount

        for (i in 0 until mLineCount) {
            val rotateDegrees = i * -unitDegrees

            val startX: Float = (mCenterX + Math.cos(rotateDegrees.toDouble()) * interCircleRadius).toFloat()
            val startY: Float = (mCenterY - Math.sin(rotateDegrees.toDouble()) * interCircleRadius).toFloat()

            val stopX: Float = (mCenterX + Math.cos(rotateDegrees.toDouble()) * outerCircleRadius).toFloat()
            val stopY: Float = (mCenterY - Math.sin(rotateDegrees.toDouble()) * outerCircleRadius).toFloat()

            if (mDrawBackgroundOutsideProgress) {
                if (i >= progressLineCount) canvas?.drawLine(startX, startY, stopX, stopY, mProgressBackgroundPaint)
            } else {
                canvas?.drawLine(startX, startY, stopX, stopY, mProgressBackgroundPaint)
            }

            if (i < progressLineCount) canvas?.drawLine(startX, startY, stopX, stopY, mProgressPaint)
        }
    }

    /**
     * 绘制实心扇形圆弧
     */
    private fun drawSolidProgress(canvas: Canvas?) {
        if (mDrawBackgroundOutsideProgress) {
            val startAngle: Float = MAX_DEGREE * mProgress / mMax
            val sweepAngle: Float = MAX_DEGREE - startAngle
            canvas?.drawArc(mProgressRectF, startAngle, sweepAngle, true, mProgressBackgroundPaint)
        } else {
            canvas?.drawArc(mProgressRectF, 0f, MAX_DEGREE, true, mProgressBackgroundPaint)
        }
        canvas?.drawArc(mProgressRectF, 0f, MAX_DEGREE * mProgress / mMax, true, mProgressPaint)
    }

    /**
     * 绘制实心线形圆弧
     */
    private fun drawSolidLineProgress(canvas: Canvas?) {
        if (mDrawBackgroundOutsideProgress) {
            val startAngle: Float = MAX_DEGREE * mProgress / mMax
            val sweepAngle: Float = MAX_DEGREE - startAngle
            canvas?.drawArc(mProgressRectF, startAngle, sweepAngle, false, mProgressBackgroundPaint)
        } else {
            canvas?.drawArc(mProgressRectF, 0f, MAX_DEGREE, false, mProgressBackgroundPaint)
        }
        val sweepAngle = MAX_DEGREE * mProgress / mMax
        Log.e("FantasticLL", "sweepAngle:$sweepAngle")
        canvas?.drawArc(mProgressRectF, 0f, sweepAngle, false, mProgressPaint)
    }

    /**
     * 更新着色器
     * 需要在onSizeChanged中执行{@link #onSizeChanged(int, int, int, int)}
     */
    private fun updateProgressShader() {
        if (mProgressStartColor != mProgressEndColor) {
            var shader: Shader? = null
            when (mShader) {
                LINEAR -> { //线性渐变
                    shader = LinearGradient(mProgressRectF.left, mProgressRectF.top, mProgressRectF.left, mProgressRectF.bottom, mProgressStartColor, mProgressEndColor, Shader.TileMode.CLAMP)
                    val matrix = Matrix()
                    matrix.setRotate(LINEAR_START_DEGREE, mCenterX, mCenterY)
                    shader.getLocalMatrix(matrix)
                }
                RADIAL -> { //径向渐变
                    if (mRadius <= 0) return
                    shader = RadialGradient(mCenterX, mCenterY, mRadius, mProgressStartColor, mProgressEndColor, Shader.TileMode.CLAMP)
                }
                SWEEP -> {  //扫描渐变
                    if (mRadius <= 0) return
                    val radian = mProgressStrokeWidth / Math.PI * 2f / mRadius
                    val rotateDegrees: Float = -(if (mCap == Paint.Cap.BUTT && mStyle == SOLID_LINE) 0f else Math.toDegrees(radian).toFloat())
                    shader = SweepGradient(mCenterX, mCenterY, intArrayOf(mProgressStartColor, mProgressEndColor), floatArrayOf(0f, 1f))
                    val matrix = Matrix()
                    matrix.setRotate(rotateDegrees, mCenterX, mCenterY)
                    shader.setLocalMatrix(matrix)
                }
            }
            mProgressPaint.shader = shader
        } else {    //无渐变
            mProgressPaint.shader = null
            mProgressPaint.color = mProgressStartColor
        }
    }

    /**
     * 开始动画
     * @param duration 动画执行时长.默认1s
     * @param start 动画开始时进度.默认0
     * @param end 动画终止时进度.默认最大值
     * @param repeatCount 动画重复执行次数.默认为0不重复，ValueAnimator.INFINITE(无限循环)
     */
    @JvmOverloads
    fun startAnimator(duration: Long = 1000, start: Int = 0, end: Int = mMax, repeatCount: Int = 0) {
        if (mAnimator == null) {
            mAnimator = ValueAnimator()
            mAnimator?.addUpdateListener {
                mProgress = it.animatedValue as Int
                mOnPressedListener?.onPressProcess(mProgress)
                if (mProgress == end && !isStopedAnim) {
                    mOnPressedListener?.onPressEnd()
                    isStopedAnim = true
                    mAnimator?.cancel()
                }
            }
        }
        val s = if (isContinuable) {
            if (mProgress >= mMax) 0 else mProgress
        } else if (start < 0) 0 else if (start > mMax) mMax else start
        val e = if (end > mMax) mMax else if (end < 0) 0 else end
        mAnimator?.setIntValues(s, e)
        mAnimator?.duration = (1f * Math.abs(e - s) / mMax * duration).toLong()
        mAnimator?.repeatCount = if (isContinuable) 0 else repeatCount
        mAnimator?.start()

        mOnPressedListener?.onPressStart()
        isStopedAnim = false
    }

    /**
     * 停止动画
     */
    fun stopAnimator() {
        if (mAnimator != null && mAnimator!!.isRunning && !isStopedAnim) {
            if (!isStopedAnim) {
                mOnPressedListener?.onPressInterrupt(mAnimator!!.animatedValue as Int)
                isStopedAnim = true
            }
            if (isContinuable) {
                mAnimator?.cancel()
            } else {
                if (mStopAnimType == STOP_ANIM_SIMPLE) {    //直接停止动画并恢复到进度0
                    mAnimator?.cancel()
                    mProgress = 0
                } else {    //动画回退到进度0
                    mAnimator?.reverse()
                }
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || !isClickable) return super.dispatchTouchEvent(event)
        if (isLinkChildTouchEvent) {
            executeAnim(event)
        }
        return super.dispatchTouchEvent(event)
    }

    private fun executeAnim(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startAnimator()
            }
            MotionEvent.ACTION_UP -> {
                stopAnimator()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isValid(event.x, event.y)) {
                    stopAnimator()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                stopAnimator()
            }
        }
    }

    /**
     * 触摸点是否在控件范围内
     */
    private fun isValid(touchX: Float, touchY: Float): Boolean {
        return touchX >= 0 && touchX <= width && touchY >= 0 && touchY <= height
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || !isClickable) return super.onTouchEvent(event)
        if (!isLinkChildTouchEvent) {
            executeAnim(event)
        }
        return super.onTouchEvent(event)
    }

    interface OnPressedListener {
        //按下时响应
        fun onPressStart()

        //按下过程中响应，带当前进度值
        fun onPressProcess(progress: Int)

        //中断按下响应，带中断时的进度值
        fun onPressInterrupt(progress: Int)

        //结束按下响应
        fun onPressEnd()
    }
}