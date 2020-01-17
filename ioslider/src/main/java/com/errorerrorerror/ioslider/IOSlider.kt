package com.errorerrorerror.ioslider

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources.NotFoundException
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.util.Property
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.airbnb.lottie.*
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class IOSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.IOSliderStyle) : View(context, attrs, defStyleAttr) {

    /**
     * This callback listens for changes in [IOSlider].
     */
    interface OnSliderChangeListener {
        /**
         * Notifies any changes to the progress level.
         *
         * @param slider The current slider that has been changed.
         * @param progress the current progress level. The value varies
         * between [minValue] and [maxValue].
         * These values can be set by using [minValue]
         * and [maxValue] setters.
         *
         * @param fromUser Alerts the client if the progress was changed by the user.
         */
        fun onProgressChanged(slider: IOSlider, progress: Int, fromUser: Boolean)
    }

    /**
     * This callback listens for changes on touch.
     */
    interface OnSliderTouchListener {
        /**
         * Notifies when user touches the slider.
         * @param slider The slider that was touched.
         */
        fun onStartTrackingTouch(slider: IOSlider)

        /**
         * Notifies when the user stops touching the slider.
         * @param slider The slider that stopped being touched.
         */
        fun onStopTrackingTouch(slider: IOSlider)
    }

    @IntDef(DRAG, TOUCH)
    @Retention(AnnotationRetention.SOURCE)
    annotation class TouchMode

    /**
     * This allows for user to either drag the slider by passing
     * {@value DRAG} of if you want the progress to change on touch you can
     * pass {@value TOUCH}.
     */
    @TouchMode
    var touchMode = 0
        set(value) {
            if (value != TOUCH && value != DRAG) return
            if (field != value) {
                field = value
                invalidate()
            }
        }

    @IntDef(TEXT, ICON, TEXTICON, ICONTEXT, NONE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class IconTextVisibility

    /**
     * This allows for the user to either show the icon or/and the label
     * and the order in which to show.
     */
    @IconTextVisibility
    var iconLabelVisibility = 0
        set(value) {
            if (value != field) {
                field = value
                invalidate()
            }
        }

    /**
     * This is the current progress of the slider
     */
    var progress: Int
        /**
         * Returns the progress of the slider in respect to [minValue]
         * and [maxValue].
         * @see rawProgress
         */
        get() = ((maxValue - minValue) * rawProgress + minValue).roundToInt()

        /**
         * Sets the slider to the specified value without animation.
         *
         * Does not do anything if the value is less than or
         * more than maximum value.
         *
         * @param progress the desired progress the user wants.
         * Must be in between `minValue ` and `maxValue ` inclusive.
         */
        set(progress) {
            setProgressInternal(progress, false)
        }

    /**
     * This method is the raw progress of the slider. This is used
     * to draw the slider's active track and get the value of the progress.
     *
     */
    @get:FloatRange(from = 0.0, to = 1.0)
    @FloatRange(from = 0.0, to = 1.0)
    var rawProgress = 0f
        private set

    /**
     * This is used mainly for animating the progress of the view so
     * it will not interfere with the actual progress.
     */
    @FloatRange(from = 0.0, to = 1.0)
    private var visualProgress = 0f
        /**
         * Sets the progress for the visual. Used with [ObjectAnimator] to animate the progress.
         * @param value the new value to update the visual progress
         */
        set(value) {
            field = value
            updateText()
            updateIconAnimation()
            invalidate()
        }

    var cornerRadius: Int = 0
        /**
         * Sets the corner radius of the view.
         *
         * @param value the new corner radius.
         */
        set(value) {
            if (value != field) {
                field = value
                updateShadowAndOutline(width, height)
                invalidate()
            }
        }

    /**
     * The size of the label.
     */
    var labelSize = 0
    set(value) {
        if (value != field) {
            field = value
            updateTextSize()
            invalidate()
        }
    }

    /**
     * The minimum value the progress can go.
     */
    var minValue = 0
        /**
         * Sets the minimum value to the specified value.
         *
         * @throws IllegalArgumentException if the minimum value is greater than or
         * equal to [maxValue]
         * @param value the desired minimum value for the slider.
         */
        set(value) {
            field = value
            if (canValidate) {
                validateMinValue()
            }
        }

    /**
     * The maximum value the progress can go.
     */
    var maxValue = 100
        /**
         * Sets the maximum value to the specified value.
         *
         * @throws IllegalArgumentException if the maximum value is less than or
         * equal to [minValue]
         * @see .getMaxValue
         * @param value the desired maximum value for the slider
         */
        set(value) {
            field = value
            if (canValidate) {
                validateMaxValue()
            }
        }

    /**
     * The width of the stroke.
     */
    var strokeWidth = 0
        /**
         * Sets the width of the stroke.
         *
         * @param value the new width of the stroke.
         */
        set(value) {
            if (value != field) {
                field = value
                strokePaint.strokeWidth = field.toFloat()
                invalidate()
            }
        }

    /**
     * Sets the text for the label. By default, the label shows the percentage
     * in between [minValue] and [maxValue].
     */
    var labelText = ""

    /**
     * The size of the icon.
     */
    @Dimension
    var iconSize = 0
        /**
         * Sets the size of the icon.

         * @param value the new size for the icon.
         */
        set(value) {
            if (value != field) {
                field = value
                updateIconSize()
                invalidate()
            }
        }

    /**
     * Blend the icon and label based on the progress.
     */
    var iconLabelBlending = false
        /**
         * This method blends the icon and label based on the progress. If the progress
         * draws under the icon or label, it colors them with the inactive color.
         *
         * @param value either blend or not blend the color.
         */
        set(value) {
            if (value != field) {
                field = value
                invalidate()
            }
        }

    /**
     * Whether or not it should set the text to show the percentage of
     * the progress.
     */
    var labelAsPercentage = true
        /**
         * This method  sets the label to show the percentage of the progress.
         *
         */
        set(value) {
            if (value != field) {
                field = value
                updateText()
                invalidate()
            }
        }

    var labelColor: ColorStateList = ColorStateList.valueOf(Color.TRANSPARENT)
        /**
         * Sets the label text to the specified color list. Does not do anything if
         * the color state list is the same.
         *
         * Note: If you have blending mode enabled it will still update the color but
         * depending on the [.mProgress] it might be showing the inverted color.

         * @param value the new color state list.
         */
        set(value) {
            if (value != field) {
                field = value
                textPaint.color = getColorForState(field)
                invalidate()
            }
        }

    var inactiveTrackColor: ColorStateList = ColorStateList.valueOf(Color.TRANSPARENT)
        /**
         * Sets the new color state list for the inactive track.
         * This cannot be null.
         *
         * @param value the new color state list.
         */
        set(value) {
            if (value != field) {
                field = value
                inactiveTrackPaint.color = getColorForState(field)
                invalidate()
            }
        }

    private var activeTrackColor: ColorStateList = ColorStateList.valueOf(Color.TRANSPARENT)
        /**
         * Sets the new color state list for the active track.
         * This cannot be null.
         *
         * @param value the new color state list.
         */
        set(value) {
            if (value != field) {
                field = value
                activeTrackPaint.color = getColorForState(field)
                invalidate()
            }
        }

    private var iconColor: ColorStateList? = null
        /**
         * Sets the icon color. It uses [PorterDuffColorFilter] with
         * [PorterDuff.Mode.SRC_IN] to set the color state list.
         * It may be null to remove the color overlay.
         *
         * @see .setIconColor
         * @see .getIconColor
         * @param value the new color state list or null
         */
        set(value) {
            if (value != field) {
                field = value
                updateIconDrawableColor(value)
                invalidate()
            }
        }

    private var strokeColor: ColorStateList = ColorStateList.valueOf(Color.TRANSPARENT)
        /**
         * Sets the new color state list for the stroke. This cannot be null.
         *
         * @param value the new color state list.
         */
        set(value) {
            if (value != field) {
                field = value
                invalidate()
            }
        }

    var iconDrawable: Drawable? = null
        /**
         * Sets the icon drawable. Can pass null to remove the icon.
         *
         * @param value the new icon drawable.
         */
        set(value) {
            if (value != field) {
                // Used if there was a LottieDrawable in use.
                cancelLoaderTask()
                clearComposition()
                field = value
                if (field != null) {
                    updateIconDrawableColor(iconColor)
                    updateIconAnimation()
                    updateIconSize()
                    invalidate()
                }
            }
        }

    private val inactiveTrackPaint: Paint
    private val activeTrackPaint: Paint
    private val textPaint: Paint
    private val strokePaint: Paint

    private val activeTrackRect: Rect
    private val inactiveTrackRect: Rect
    private val drawableRect: Rect
    private val labelBounds: Rect
    private val strokePath: Path

    private var canValidate = false

    @AnyRes
    private var iconResource = 0
    private var cancelAnimator = false
    private var lastTouchY = 0f
    private val onSliderChangeListener: MutableList<OnSliderChangeListener> = ArrayList()
    private val onSliderTouchListener: MutableList<OnSliderTouchListener> = ArrayList()
    private var animator: ObjectAnimator? = null
    private var lottieCompositionTask: LottieTask<LottieComposition>? = null

    init {
        isSaveEnabled = true
        inactiveTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        inactiveTrackPaint.style = Paint.Style.FILL
        activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        activeTrackPaint.style = Paint.Style.FILL
        textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = labelSize.toFloat()
        strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = strokeWidth.toFloat()
        activeTrackRect = Rect()
        inactiveTrackRect = Rect()
        labelBounds = Rect()
        drawableRect = Rect()
        strokePath = Path()

        getResources(context, attrs, defStyleAttr)

        val typeResource: String = try {
            context.resources.getResourceTypeName(iconResource)
        } catch (e: NotFoundException) {
            TYPE_NOT_FOUND
        }
        when (typeResource) {
            TYPE_RAW -> {
                setIconAnimation(iconResource)
            }
            TYPE_DRAWABLE -> {
                iconDrawable = ContextCompat.getDrawable(context, iconResource)
            }
            else -> {
                Log.d(TAG, "IOSlider: Did not set an Icon Drawable.")
            }
        }
    }

    private fun getResources(context: Context, attrs: AttributeSet?, style: Int) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.IOSlider, style, DEF_STYLE_RES)
        cornerRadius = ta.getDimensionPixelSize(R.styleable.IOSlider_cornerRadius, context.resources.getDimensionPixelSize(R.dimen.corner_radius))
        iconResource = ta.getResourceId(R.styleable.IOSlider_icon, 0)
        iconColor = getColorStateList(context, ta, R.styleable.IOSlider_iconColor, R.color.slider_icon_color)
        iconSize = ta.getDimensionPixelSize(R.styleable.IOSlider_iconSize, getContext().resources.getDimensionPixelSize(R.dimen.icon_size))
        if (ta.hasValue(R.styleable.IOSlider_labelText)) {
            labelText = ta.getString(R.styleable.IOSlider_labelText)!!
            labelAsPercentage = false
        }
        labelColor = getColorStateList(context, ta, R.styleable.IOSlider_labelColor, R.color.slider_text_color)
        labelSize = ta.getDimensionPixelSize(R.styleable.IOSlider_labelSize, context.resources.getDimensionPixelSize(R.dimen.text_size))
        activeTrackColor = getColorStateList(context, ta, R.styleable.IOSlider_activeTrackColor, R.color.slider_active_track_color)
        inactiveTrackColor = getColorStateList(context, ta, R.styleable.IOSlider_inactiveTrackColor, R.color.slider_inactive_track_color)
        minValue = ta.getInt(R.styleable.IOSlider_min, 0)
        maxValue = ta.getInt(R.styleable.IOSlider_max, 100)
        strokeWidth = ta.getDimensionPixelSize(R.styleable.IOSlider_strokeWidth, 0)
        strokeColor = getColorStateList(context, ta, R.styleable.IOSlider_strokeColor, android.R.color.transparent)
        iconLabelBlending = ta.getBoolean(R.styleable.IOSlider_blendIconLabel, false)
        touchMode = ta.getInt(R.styleable.IOSlider_touchMode, DRAG)
        progress = ta.getInt(R.styleable.IOSlider_progress, 50)
        iconLabelVisibility = ta.getInt(R.styleable.IOSlider_iconTextVisibility, ICONTEXT)

        ta.recycle()
        validateMinValue()
        validateMaxValue()
        canValidate = true
    }

    /**
     * This is basically [.setProgress] but has the option to
     * animate the progress. For interpolation it uses [FastOutSlowInInterpolator]
     * to animate the progress with {@value #ANIMATION_DURATION} duration.
     *
     * Note: It does not manipulate [progress] since for the animation
     * to occur, it requires to have the value to change. Instead, this animation
     * occurs in `mVisualProgress`. It will only dispatch on change once and
     * that's the new progress value.
     *
     * @see progress
     * @param newProgress the progress to animate.
     * @param animate sets whether or not to animate the new progress.
     * @param cancelOnTouch if the animation is running, you can set whether or not
     * you want the user to cancel the animation on {@value TOUCH}
     * or {@value DRAG}.
     */
    fun setProgress(newProgress: Int, animate: Boolean, cancelOnTouch: Boolean) {
        if (cancelAnimator != cancelOnTouch) {
            cancelAnimator = cancelOnTouch
        }

        setProgressInternal(newProgress, animate)
    }

    /**
     * This internal method sets the new progress. If the user wants to animate the sliding
     * progress, it will use [ObjectAnimator] to change the [.mVisualProgress].
     * [progress] will only be dispatched once.
     *
     * @see setProgress
     */
    private fun setProgressInternal(newProgress: Int, animate: Boolean) {
        if (!isValueValid(newProgress)) return
        val scaled = (newProgress - minValue).toFloat() / (maxValue - minValue)
        if (scaled == rawProgress) return
        if (animate) {
            animator = ObjectAnimator.ofFloat(this, propertyVisual, scaled)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                animator?.setAutoCancel(true)
            }
            animator?.duration = ANIMATION_DURATION.toLong()
            animator?.interpolator = TIME_INTERPOLATOR
            animator?.start()
        } else {
            visualProgress = scaled
        }

        // Dispatch the new value
        rawProgress = scaled
        dispatchProgressChanged(false)
    }

    /**
     * This method is basically [.setIconColor] but with
     * [Color] parameters.
     *
     * @see .setIconColor
     * @see .getIconColor
     * @param color the new color filter.
     */
    fun setIconColor(@ColorInt color: Int) {
        iconColor = ColorStateList.valueOf(color)
    }

    /**
     * This method is basically [.setStrokeColor] but with
     * [Color] parameters.
     *
     * @see .setStrokeColor
     * @see .getStrokeColor
     * @param color the new color.
     */
    fun setStrokeColor(@ColorInt color: Int) {
        strokeColor = ColorStateList.valueOf(color)
    }

    /**
     * This method is basically [inactiveTrackColor] but with
     * [Color] parameters.
     *
     * @see inactiveTrackColor
     * @param color the new color.
     */
    fun setInactiveTrackColor(@ColorInt color: Int) {
        inactiveTrackColor = ColorStateList.valueOf(color)
    }

    /**
     * This method is basically [activeTrackColor] but with
     * [Color] parameters.
     *
     * @see activeTrackColor
     * @param color the new color.
     */
    fun setActiveTrackColor(@ColorInt color: Int) {
        activeTrackColor = ColorStateList.valueOf(color)
    }

    /**
     * This is basically [labelColor] but with a [Color] int
     * instead.
     * @param color the new color.
     */
    fun setLabelColor(@ColorInt color: Int) {
        labelColor = ColorStateList.valueOf(color)
    }

    /**
     *
     * @see OnSliderChangeListener
     */
    fun addOnSliderChangeListener(listener: OnSliderChangeListener) {
        onSliderChangeListener.add(listener)
    }

    fun removeOnSliderChangeListener(listener: OnSliderChangeListener) {
        onSliderChangeListener.remove(listener)
    }

    private fun dispatchProgressChanged(fromUser: Boolean) {
        for (changeListener in onSliderChangeListener) {
            changeListener.onProgressChanged(this, progress, fromUser)
        }
    }

    fun addOnSliderTouchListener(listener: OnSliderTouchListener) {
        onSliderTouchListener.add(listener)
    }

    fun removeOnSliderTouchListener(listener: OnSliderTouchListener) {
        onSliderTouchListener.remove(listener)
    }

    private fun dispatchOnStartTrackingTouch() {
        for (listener in onSliderTouchListener) {
            listener.onStartTrackingTouch(this)
        }
    }

    private fun dispatchOnStopTrackingTouch() {
        for (listener in onSliderTouchListener) {
            listener.onStopTrackingTouch(this)
        }
    }

    private fun updateText() {
        if (labelAsPercentage) {
            val value = visualProgress * 100
            labelText = String.format(Locale.ENGLISH, "%.0f", value) + "%"
        }
    }

    private fun updateIconAnimation() {
        if (iconDrawable != null) {
            if (iconDrawable is LottieDrawable) {
                val lottieDrawable = iconDrawable as LottieDrawable
                lottieDrawable.progress = visualProgress
            } else {
                val didChange = iconDrawable!!.setLevel((visualProgress * (maxValue - minValue)).toInt())
                if (didChange) {
                    iconDrawable!!.invalidateSelf()
                }
            }
        }
    }

    private fun updateIconDrawableColor(colorStateList: ColorStateList?) {
        if (iconDrawable != null) {
            var colorFilter: PorterDuffColorFilter? = null
            if (colorStateList != null) {
                colorFilter = PorterDuffColorFilter(getColorForState(colorStateList), PorterDuff.Mode.SRC_IN)
            }

            if (iconDrawable is LottieDrawable) {
                val keyPath = KeyPath("**")
                val callback = LottieValueCallback<ColorFilter>(colorFilter)
                (iconDrawable as LottieDrawable).addValueCallback(keyPath, LottieProperty.COLOR_FILTER, callback)
            } else {
                DrawableCompat.setTintList(iconDrawable!!, colorStateList)
            }
        }
    }

    private fun updateIconSize() {
        if (iconDrawable != null) {
            if (iconDrawable is LottieDrawable) {
                /// Workaround since LottieDrawable handles width/height on setScale
                var previousWidth = iconDrawable!!.bounds.width()
                if (previousWidth == 0) {
                    previousWidth = iconSize
                }
                val scale: Float = (iconSize.toFloat()/previousWidth.toFloat())

                (iconDrawable as LottieDrawable).scale = scale
            } else {
                iconDrawable!!.setBounds(0, 0, iconSize, iconSize)
            }
        }
    }

    private fun updateTextSize() {
        if (textPaint.textSize != labelSize.toFloat()) {
            textPaint.textSize = labelSize.toFloat()
        }
    }

    private fun updateShadowAndOutline(width: Int, height: Int) {
        val left = paddingStart
        val top = paddingTop
        val right = width - paddingEnd
        val bottom = height - paddingBottom
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(left, top, right, bottom, cornerRadius.toFloat())
                }
            }
            strokePath.reset()
            strokePath.addRoundRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), cornerRadius.toFloat(), cornerRadius.toFloat(), Path.Direction.CW)
        } else {
            strokePath.reset()
            strokePath.addPath(createRoundRect(left, top, right, bottom, cornerRadius.toFloat()))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || animator != null && animator!!.isRunning && !cancelAnimator) return false
        if (animator != null) {
            animator!!.cancel()
            visualProgress = animator!!.animatedValue as Float
            rawProgress = visualProgress
            animator = null
        }

        val dY: Float
        var handledTouch = false
        var needsUpdate = false
        val height = height - (paddingTop + paddingBottom) - strokeWidth
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                lastTouchY = event.y - paddingTop - (strokeWidth / 2.toFloat()).roundToInt()
                if (touchMode == TOUCH) {
                    visualProgress = max(0.0f, min(1.0f, 1.0f - lastTouchY / height))
                    rawProgress = visualProgress
                    needsUpdate = true
                }
                dispatchOnStartTrackingTouch()
                isPressed = true
                handledTouch = true
            }

            MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                dY = lastTouchY - event.y + paddingTop + (strokeWidth / 2.toFloat()).roundToInt()
                needsUpdate = calculateValueFromEvent(dY, height)
                lastTouchY -= dY
                isPressed = true
                handledTouch = true
            }

            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                dispatchOnStopTrackingTouch()
                isPressed = false
                handledTouch = true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                isPressed = false
                handledTouch = true
                needsUpdate = true
            }
        }
        if (needsUpdate) {
            dispatchProgressChanged(true)
            updateText()
            updateIconAnimation()
            invalidate()
        }
        return handledTouch
    }

    private fun calculateValueFromEvent(distanceYY: Float, height: Int): Boolean {
        var updatedValue = false
        val distance = distanceYY / height
        var newProgress = rawProgress + distance
        newProgress = max(0.0f, min(1.0f, newProgress))
        if (rawProgress != newProgress) {
            visualProgress = newProgress
            rawProgress = visualProgress
            updatedValue = true
        }
        return updatedValue
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        /// Sets shadow and the outline
        updateShadowAndOutline(w, h)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        textPaint.color = getColorForState(labelColor)
        activeTrackPaint.color = getColorForState(activeTrackColor)
        inactiveTrackPaint.color = getColorForState(inactiveTrackColor)
        strokePaint.color = getColorForState(strokeColor)
        updateIconDrawableColor(iconColor)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingStart
        val top = paddingTop
        val right = width - paddingEnd
        val bottom = height - paddingBottom
        /// Clips view to stroke path
        canvas.clipPath(strokePath)
        /// Draws inactive track
        drawInactiveTrack(canvas, right, bottom)
        /// Draws active track
        drawActiveTrack(canvas, right, bottom)
        /// Draws the text
        drawLabel(canvas)
        /// Draws the icon
        drawIcon(canvas)
        // Draws the stroke
        // We have to use round rect since path causes the view to draw another layer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.drawRoundRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), cornerRadius.toFloat(), cornerRadius.toFloat(), strokePaint)
        } else {
            canvas.drawPath(strokePath, strokePaint)
        }
    }

    private fun drawInactiveTrack(canvas: Canvas, widthPadding: Int, heightPadding: Int) {
        var bottomPadding = heightPadding
        val inactiveTrackRange = 1f - visualProgress
        val height = bottomPadding - paddingTop - strokeWidth
        bottomPadding = (inactiveTrackRange * height).roundToInt() + paddingTop + strokeWidth / 2
        inactiveTrackRect[paddingStart + strokeWidth / 2, paddingTop + strokeWidth / 2, widthPadding - strokeWidth / 2] = bottomPadding
        canvas.drawRect(inactiveTrackRect, inactiveTrackPaint)
    }

    private fun drawActiveTrack(canvas: Canvas, widthPadding: Int, heightPadding: Int) {
        var topPadding = paddingTop
        val height = heightPadding - topPadding - strokeWidth
        topPadding = (height - visualProgress * height).roundToInt() + paddingTop + strokeWidth / 2
        activeTrackRect[paddingStart + strokeWidth / 2, topPadding, widthPadding - strokeWidth / 2] = heightPadding - strokeWidth / 2
        canvas.drawRect(activeTrackRect, activeTrackPaint)
    }

    private fun drawLabel(canvas: Canvas) {

        if (!canDrawLabel()) return
        textPaint.getTextBounds(labelText, 0, labelText.length, labelBounds)
        labelBounds.right = textPaint.measureText(labelText).toInt()
        val width = width - (paddingStart + paddingEnd)
        val height = height - (paddingBottom + paddingBottom)
        val position = if (iconLabelVisibility == TEXT) 0.5f else if (iconLabelVisibility == TEXTICON) 0.25f else 0.75f

        val textLeftPadding = paddingStart + (width - labelBounds.width()) / 2
        val textTopPadding = paddingTop + (height * position).toInt() - labelBounds.height() / 2

        if (iconLabelBlending) {
            val isInBounds = activeTrackRect.contains(textLeftPadding, textTopPadding + labelBounds.height() / 2, textLeftPadding + labelBounds.width(), textTopPadding + labelBounds.height())
            if (isInBounds) {
                if (textPaint.color != getColorForState(inactiveTrackColor)) {
                    textPaint.color = getColorForState(inactiveTrackColor)
                }
            } else {
                if (textPaint.color != getColorForState(labelColor)) {
                    textPaint.color = getColorForState(labelColor)
                }
            }
        } else {
            if (textPaint.color != getColorForState(labelColor)) {
                textPaint.color = getColorForState(labelColor)
            }
        }

        canvas.save()
        canvas.rotate(-rotation, (paddingStart + width / 2).toFloat(), height * position + paddingTop)
        canvas.drawText(labelText, 0, labelText.length, textLeftPadding.toFloat(), textTopPadding + labelBounds.height().toFloat(), textPaint)
        canvas.restore()
    }

    private fun drawIcon(canvas: Canvas) {
        if (iconDrawable == null || !canDrawIcon()) return

        val iconWidth = iconDrawable!!.bounds.width()
        val iconHeight = iconDrawable!!.bounds.height()
        val width = width - (paddingStart + paddingEnd)
        val height = height - (paddingTop + paddingBottom)
        val position = if (iconLabelVisibility == ICON) 0.5f else if (iconLabelVisibility == TEXTICON) 0.75f else 0.25f

        val iconLeftPadding = (width - iconWidth) / 2 + paddingStart
        val iconTopPadding = (height * position).toInt() - iconHeight / 2 + paddingTop

        if (iconLabelBlending) {
            val isInBounds = activeTrackRect.contains(iconLeftPadding, iconTopPadding + iconHeight / 2, iconLeftPadding + iconWidth, iconTopPadding + iconHeight)
            if (isInBounds) {
                updateIconDrawableColor(inactiveTrackColor)
            } else {
                updateIconDrawableColor(iconColor)
            }
        } else {
            updateIconDrawableColor(iconColor)
        }

        canvas.save()
        canvas.rotate(-rotation, (paddingStart + width / 2).toFloat(),
                paddingTop + height * position)
        canvas.translate(iconLeftPadding.toFloat(), iconTopPadding.toFloat())
        iconDrawable!!.draw(canvas)
        canvas.restore()
    }

    @ColorInt
    private fun getColorForState(colorStateList: ColorStateList): Int {
        return colorStateList.getColorForState(drawableState, colorStateList.defaultColor)
    }

    private fun isValueValid(value: Int): Boolean {
        var isValid = false
        if (value < minValue || value > maxValue) {
            Log.e(TAG, "Value must be in between min value and max value inclusive")
        } else {
            isValid = true
        }
        return isValid
    }

    private fun validateMinValue() {
        if (minValue >= maxValue) {
            Log.e(TAG, "Minimum value must be less than max value.")
            throw IllegalArgumentException("Minimum value must be less than max value.")
        }
    }

    private fun validateMaxValue() {
        if (maxValue <= minValue) {
            Log.e(TAG, "Max value must be greater than min value.")
            throw IllegalArgumentException("Max value must be greater than min value.")
        }
    }

    private fun canDrawIcon(): Boolean {
        return iconLabelVisibility == ICON || iconLabelVisibility == ICONTEXT || iconLabelVisibility == TEXTICON
    }

    private fun canDrawLabel(): Boolean {
        return iconLabelVisibility == TEXT || iconLabelVisibility == TEXTICON || iconLabelVisibility == ICONTEXT
    }

    /////////////// LottieDrawable Settings /////////////////
    /**
     * Sets the animation from a file in the raw directory. This is used for animating
     * [LottieDrawable] on progress changed.
     * For more information on how this works check [com.airbnb.lottie.LottieAnimationView.setAnimation]
     */
    fun setIconAnimation(@RawRes res: Int) {
        if (iconDrawable !is LottieDrawable) {
            iconDrawable = null
            iconDrawable = LottieDrawable()
            (iconDrawable as LottieDrawable).callback = this
        }
        val task = LottieCompositionFactory.fromRawRes(context, res, null)
        setCompositionTask(task)
    }

    private fun setComposition(composition: LottieComposition) {
        if (iconDrawable !is LottieDrawable) return
        val isNewComposition = (iconDrawable as LottieDrawable).setComposition(composition)
        if (isNewComposition) {
            updateIconAnimation()
            updateIconSize()
            updateIconDrawableColor(iconColor)
            invalidate()
        }
    }

    private fun setCompositionTask(compositionTask: LottieTask<LottieComposition>) {
        cancelLoaderTask()
        clearComposition()
        compositionTask
                .addListener { result -> setComposition(result) }
                .addFailureListener { result -> throw IllegalStateException("Unable to parse composition", result) }
    }

    private fun cancelLoaderTask() {
        if (lottieCompositionTask == null) return
        lottieCompositionTask!!
                .removeListener { result -> setComposition(result) }
                .removeFailureListener { result -> throw IllegalStateException("Unable to parse composition", result) }
    }

    private fun clearComposition() {
        lottieCompositionTask = null
        if (iconDrawable is LottieDrawable) {
            (iconDrawable as LottieDrawable).clearComposition()
        }
    }

    /**
     * This is a wrapper for [visualProgress] so it can be used
     * with ObjectAnimator.
     */
    private val propertyVisual: Property<IOSlider, Float> = object : Property<IOSlider, Float>(Float::class.java, VISUAL_PROGRESS) {
        override fun set(`object`: IOSlider, value: Float) {
            `object`.visualProgress = value
        }

        override fun get(`object`: IOSlider): Float {
            return `object`.visualProgress
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val state = IOSliderSaveState(superState)
        state.minValue = minValue
        state.maxValue = maxValue
        state.mProgress = rawProgress
        state.mVisualProgress = visualProgress
        state.labelSize = labelSize
        state.iconSize = iconSize
        state.labelAsPercentage = labelAsPercentage
        state.iconLabelBlend = iconLabelBlending
        state.touchMode = touchMode
        state.iconTextVisible = iconLabelVisibility
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as IOSliderSaveState
        super.onRestoreInstanceState(savedState.superState)
        minValue = savedState.minValue
        maxValue = savedState.maxValue
        rawProgress = savedState.mProgress
        visualProgress = savedState.mVisualProgress
        iconSize = savedState.iconSize
        labelSize = savedState.labelSize
        labelText = savedState.labelText!!
        labelAsPercentage = savedState.labelAsPercentage
        iconLabelBlending = savedState.iconLabelBlend
        touchMode = savedState.touchMode
        iconLabelVisibility = savedState.iconTextVisible
        updateIconSize()
        updateText()
        updateTextSize()
        invalidate()
        dispatchProgressChanged(false)
    }

    internal class IOSliderSaveState : BaseSavedState {
        var minValue = 0
        var maxValue = 0
        var mProgress = 0f
        var mVisualProgress = 0f
        var iconSize = 0
        var labelSize = 0
        var labelText: String? = null
        var labelAsPercentage = false
        var iconLabelBlend = false
        @TouchMode
        var touchMode = 0
        @IconTextVisibility
        var iconTextVisible = 0

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            minValue = source.readInt()
            maxValue = source.readInt()
            mProgress = source.readFloat()
            mVisualProgress = source.readFloat()
            iconSize = source.readInt()
            labelSize = source.readInt()
            labelText = source.readString()
            labelAsPercentage = source.readByte().toInt() == 1
            iconLabelBlend = source.readByte().toInt() == 1
            touchMode = source.readInt()
            iconTextVisible = source.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(minValue)
            out.writeInt(maxValue)
            out.writeFloat(mProgress)
            out.writeFloat(mVisualProgress)
            out.writeInt(iconSize)
            out.writeInt(labelSize)
            out.writeString(labelText)
            out.writeByte(if (labelAsPercentage) 1 else 0)
            out.writeByte(if (iconLabelBlend) 1 else 0)
            out.writeInt(touchMode)
            out.writeInt(iconTextVisible)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<IOSliderSaveState> {
            override fun createFromParcel(parcel: Parcel): IOSliderSaveState {
                return IOSliderSaveState(parcel)
            }

            override fun newArray(size: Int): Array<IOSliderSaveState?> {
                return arrayOfNulls(size)
            }
        }
    }

    companion object {
        private val TAG = IOSlider::class.java.name
        private val DEF_STYLE_RES = R.style.Widget_IOSlider
        private const val TYPE_RAW = "raw"
        private const val TYPE_DRAWABLE = "drawable"
        private const val TYPE_NOT_FOUND = "not_found"

        /**
         * Sets the slider to change on drag.
         */
        const val DRAG = 0
        /**
         * Sets the slider to change on touch.
         */
        const val TOUCH = 1
        /**
         * Shows only the icon.
         */
        const val ICON = 0
        /**
         * Shows only the label.
         */
        const val TEXT = 1
        /**
         * Shows the Icon first, then the label.
         */
        const val ICONTEXT = 2
        /**
         * Shows the label first then the icon.
         */
        const val TEXTICON = 3
        /**
         * Does not show the icon and the label.
         */
        const val NONE = 4

        private const val VISUAL_PROGRESS = "VisualProgress"
        private val TIME_INTERPOLATOR = FastOutSlowInInterpolator()
        private const val ANIMATION_DURATION = 1000

        /// For api lower than 21
        fun createRoundRect(left: Int, top: Int, right: Int, bottom: Int, radius: Float): Path {
            val path = Path()
            path.moveTo(left + radius, top.toFloat())
            path.lineTo(right - radius, top.toFloat())
            path.quadTo(right.toFloat(), top.toFloat(), right.toFloat(), top + radius)
            path.lineTo(right.toFloat(), bottom - radius)
            path.quadTo(right.toFloat(), bottom.toFloat(), right - radius, bottom.toFloat())
            path.lineTo(left + radius, bottom.toFloat())
            path.quadTo(left.toFloat(), bottom.toFloat(), left.toFloat(), bottom - radius)
            path.lineTo(left.toFloat(), top + radius)
            path.quadTo(left.toFloat(), top.toFloat(), left + radius, top.toFloat())
            path.close()
            return path
        }

        /**
         * Helper method to get color state list.
         */
        private fun getColorStateList(context: Context, attributes: TypedArray, @StyleableRes index: Int, @ColorRes defaultValue: Int): ColorStateList {
            var setDefaultValue = true
            var colorStateList: ColorStateList? = null
            if (attributes.hasValue(index)) {
                val resourceId = attributes.getResourceId(index, 0)
                if (resourceId != 0) {
                    colorStateList = AppCompatResources.getColorStateList(context, resourceId)
                    if (colorStateList != null) {
                        setDefaultValue = false
                    }
                }
            }

            if (setDefaultValue) {
                colorStateList = AppCompatResources.getColorStateList(context, defaultValue)
            }
            return colorStateList!!
        }
    }
}