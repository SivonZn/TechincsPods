package cn.martinkay.technicspods.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import cn.martinkay.technicspods.R
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val VALUE_START_ANGLE = 30f
private const val VALUE_END_ANGLE = 330f
private const val SWEEP_ANGLE = VALUE_END_ANGLE - VALUE_START_ANGLE
private const val DRAW_START_ANGLE = 90f + VALUE_START_ANGLE
private const val KNOB_SCALE = 0.704f

class PowerampRoundKnobView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var value: Int = 0
        set(newValue) {
            val coerced = newValue.coerceIn(0, 100)
            if (field != coerced) {
                field = coerced
                invalidate()
            }
        }

    var onValueChange: ((Int) -> Unit)? = null
    var onValueCommit: ((Int) -> Unit)? = null

    private var dragValue = value
    private var dragValueFloat = value.toFloat()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var hasDragged = false
    private var touchedGap = false
    private val knobDrawable = ContextCompat.getDrawable(context, R.drawable.poweramp_round_knob)?.mutate()
    private val indicatorDrawable =
        ContextCompat.getDrawable(context, R.drawable.poweramp_round_knob_indicator)?.mutate()
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(3f)
    }
    private val arcBounds = RectF()
    private val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    init {
        isClickable = true
        isFocusable = true
        knobDrawable?.callback = this
        indicatorDrawable?.callback = this
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        knobDrawable?.state = drawableState
        indicatorDrawable?.state = drawableState
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(108f).roundToInt()
        val width = resolveSize(desired, widthMeasureSpec)
        val height = resolveSize(desired, heightMeasureSpec)
        val side = min(width, height)
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height).toFloat()
        val center = side / 2f
        val knobRadius = side * KNOB_SCALE / 2f
        val sweep = SWEEP_ANGLE * value / 100f
        val arcInset = ((side - knobRadius * 2f) / 2f) - dp(3f)

        arcBounds.set(arcInset, arcInset, side - arcInset, side - arcInset)
        arcPaint.shader = null
        arcPaint.color = Color.rgb(0x77, 0x77, 0x77)
        canvas.drawArc(arcBounds, DRAW_START_ANGLE, SWEEP_ANGLE, false, arcPaint)

        arcPaint.shader = LinearGradient(
            arcBounds.left,
            arcBounds.top,
            arcBounds.right,
            arcBounds.bottom,
            if (isPressed) pressedHiliteStart() else hiliteStart(),
            if (isPressed) pressedHiliteEnd() else hiliteEnd(),
            Shader.TileMode.CLAMP
        )
        canvas.drawArc(arcBounds, DRAW_START_ANGLE, sweep, false, arcPaint)
        arcPaint.shader = null

        val left = (center - knobRadius).roundToInt()
        val top = (center - knobRadius).roundToInt()
        val right = (center + knobRadius).roundToInt()
        val bottom = (center + knobRadius).roundToInt()
        knobDrawable?.setBounds(left, top, right, bottom)
        knobDrawable?.draw(canvas)

        val indicatorWidth = dp(5f)
        val indicatorHeight = dp(10f)
        val indicatorTop = center + knobRadius - dp(if (isPressed) 28f else 30f)
        indicatorDrawable?.setBounds(
            (center - indicatorWidth / 2f).roundToInt(),
            indicatorTop.roundToInt(),
            (center + indicatorWidth / 2f).roundToInt(),
            (indicatorTop + indicatorHeight).roundToInt()
        )
        canvas.save()
        canvas.rotate(VALUE_START_ANGLE + sweep, center, center)
        indicatorDrawable?.draw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isPressed = true
                dragValue = value
                dragValueFloat = value.toFloat()
                lastTouchX = event.x
                lastTouchY = event.y
                hasDragged = false
                touchedGap = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromDrag(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!hasDragged) {
                    updateFromTap(event.x, event.y)
                }
                isPressed = false
                touchedGap = false
                hasDragged = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onValueCommit?.invoke(dragValue)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                hasDragged = false
                touchedGap = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTap(x: Float, y: Float) {
        val next = pointToLevelOrNull(x, y) ?: run {
            touchedGap = true
            return
        }
        if (touchedGap && abs(next - dragValue) > 50) return
        touchedGap = false
        applyDragValue(next.toFloat())
    }

    private fun updateFromDrag(x: Float, y: Float) {
        val dx = x - lastTouchX
        val dy = y - lastTouchY
        lastTouchX = x
        lastTouchY = y
        if (abs(dx) < 0.5f && abs(dy) < 0.5f) return

        hasDragged = true
        val effectiveDeltaPx = if (abs(dx) > abs(dy) * 1.15f) {
            dx
        } else {
            projectToCurrentTangent(dx, dy)
        }
        val fullRangePx = width.coerceAtLeast(1) * 0.9f
        val next = dragValueFloat + (effectiveDeltaPx / fullRangePx) * 100f
        applyDragValue(next)
    }

    private fun applyDragValue(next: Float) {
        dragValueFloat = next.coerceIn(0f, 100f)
        val nextInt = dragValueFloat.roundToInt().coerceIn(0, 100)
        if (nextInt != dragValue) {
            dragValue = nextInt
            value = nextInt
            onValueChange?.invoke(nextInt)
        }
    }

    private fun projectToCurrentTangent(dx: Float, dy: Float): Float {
        val drawAngle = DRAW_START_ANGLE + (SWEEP_ANGLE * dragValueFloat / 100f)
        val radians = Math.toRadians(drawAngle.toDouble())
        val tangentX = -sin(radians).toFloat()
        val tangentY = cos(radians).toFloat()
        return dx * tangentX + dy * tangentY
    }

    private fun pointToLevelOrNull(x: Float, y: Float): Int? {
        val centerX = width / 2f
        val centerY = height / 2f
        val rawDegrees = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()
        val degrees = (rawDegrees + 360f) % 360f
        val normalized = ((degrees - DRAW_START_ANGLE + 360f) % 360f)
        if (normalized > SWEEP_ANGLE) return null
        return ((normalized / SWEEP_ANGLE) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun hiliteStart() = if (isDark) Color.rgb(0xA5, 0xB0, 0xC6) else Color.rgb(0x00, 0xFF, 0xFF)
    private fun hiliteEnd() = if (isDark) Color.rgb(0x74, 0x6C, 0xDA) else Color.BLACK
    private fun pressedHiliteStart() = if (isDark) Color.rgb(0xA8, 0xB9, 0xDD) else Color.rgb(0x88, 0xFF, 0xFF)
    private fun pressedHiliteEnd() = if (isDark) Color.rgb(0x81, 0x87, 0xFF) else Color.rgb(0x88, 0x88, 0x88)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
