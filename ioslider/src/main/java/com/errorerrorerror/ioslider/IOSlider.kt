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
import com.errorerrorerror.ioslider.IOSlider
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

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
         * between [.minValue] and [.maxValue].
         * These values can be set by using [.setMinValue]
         * and [.setMaxValue]
         * @param fromUser Alerts the client if the progress was changed by the user.
         */
        fun onProgressChanged(slider: IOSlider?, progress: Int, fromUser: Boolean)
    }

    /**
     * This callback listens for changes on touch.
     */
    interface OnSliderTouchListener {
        /**
         * Notifies when user touches the slider.
         * @param slider The slider that was touched.
         */
        fun onStartTrackingTouch(slider: IOSlider?)

        /**
         * Notifies when the user stops touching the slider.
         * @param slider The slider that stopped being touched.
         */
        fun onStopTrackingTouch(slider: IOSlider?)
    }

    @IntDef(DRAG, TOUCH)
    @Retention(RetentionPolicy.SOURCE)
    annotation class TouchMode

    @TouchMode
    private var touchMode = 0

    @IntDef(TEXT, ICON, TEXTICON, ICONTEXT, NONE)
    @Retention(RetentionPolicy.SOURCE)
    annotation class IconTextVisibility

    @IconTextVisibility
    private var iconLabelVisibility = 0
    /**
     * The corner radius of the view.
     */
    private var mRadius = 0
    /**
     * The size of the label.
     */
    private var labelSize = 0
    /**
     * This method returns the actual progress of the slider. This is used
     * to draw the slider's active track and get the value of the progress.
     *
     * @see .getProgress
     */
    /**
     * The actual progress.
     */
    @get:FloatRange(from = 0f, to = 1f)
    @FloatRange(from = 0f, to = 1f)
    var rawProgress = 0f
        private set
    /**
     * This is used mainly for animating the progress of the view so
     * it will not interfere with the actual progress.
     */
    @FloatRange(from = 0f, to = 1f)
    private var mVisualProgress = 0f
    /**
     * The minimum value the progress can go.
     */
    private var minValue = 0
    /**
     * The maximum value the progress can go.
     */
    private var maxValue = 0
    /**
     * The width of the stroke.
     */
    private var strokeWidth = 0
    /**
     * The text to show.
     */
    private var labelText = ""
    /**
     * The size of the icon.
     */
    @Dimension
    private var iconSize = 0
    /**
     * Blend the icon and label based on the progress.
     */
    private var iconLabelBlending = false
    /**
     * Whether or not it should set the text to show the percentage of
     * the progress.
     */
    private var labelAsPercentage = true
    private val inactiveTrackPaint: Paint
    private val activeTrackPaint: Paint
    private val textPaint: Paint
    private val strokePaint: Paint
    private var labelColor: ColorStateList? = null
    private var inactiveTrackColor: ColorStateList? = null
    private var activeTrackColor: ColorStateList? = null
    private var iconColor: ColorStateList? = null
    private var strokeColor: ColorStateList? = null
    private val activeTrackRect: Rect
    private val inactiveTrackRect: Rect
    private val drawableRect: Rect
    private val labelBounds: Rect
    private val strokePath: Path
    @AnyRes
    private var iconResource = 0
    private var iconDrawable: Drawable? = null
    private var cancelAnimator = false
    private var lastTouchY = 0f
    private val onSliderChangeListener: MutableList<OnSliderChangeListener> = ArrayList()
    private val onSliderTouchListener: MutableList<OnSliderTouchListener> = ArrayList()
    private var animator: ObjectAnimator? = null
    private var lottieCompositionTask: LottieTask<LottieComposition>? = null
    private fun getResources(context: Context, attrs: AttributeSet?, style: Int) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.IOSlider, style, DEF_STYLE_RES)
        mRadius = ta.getDimensionPixelSize(R.styleable.IOSlider_cornerRadius, context.resources.getDimensionPixelSize(R.dimen.corner_radius))
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
    }

    /**
     * Sets the minimum value to the specified value.
     *
     * @throws IllegalArgumentException if the minimum value is greater than or
     * equal to [.maxValue]
     * @see .getMinValue
     * @param minValue the desired minimum value for the slider.
     */
    fun setMinValue(minValue: Int) {
        this.minValue = minValue
        validateMinValue()
    }

    /**
     * Returns the minimum value of the progress.
     * @see .setMinValue
     * @return the minimum value.
     */
    fun getMinValue(): Int {
        return minValue
    }

    /**
     * Sets the maximum value to the specified value.
     *
     * @throws IllegalArgumentException if the maximum value is less than or
     * equal to [.minValue]
     * @see .getMaxValue
     * @param maxValue the desired maximum value for the slider
     */
    fun setMaxValue(maxValue: Int) {
        this.maxValue = maxValue
        validateMaxValue()
    }

    /**
     * Returns the maximum value of the progress.
     * @see .setMaxValue
     * @return the maximum value.
     */
    fun getMaxValue(): Int {
        return maxValue
    }

    /**
     * This is basically [.setProgress] but has the option to
     * animate the progress. For interpolation it uses [FastOutSlowInInterpolator]
     * to animate the progress with {@value #ANIMATION_DURATION} duration.
     *
     * Note: It does not manipulate [.mProgress] since for the animation
     * to occur, it requires to have the value to change. Instead, this animation
     * occurs in `mVisualProgress`. It will only dispatch on change once and
     * that's the new progress value.
     *
     * @see .setProgress
     * @see .getProgress
     * @param progress the progress to animate.
     * @param animate sets whether or not to animate the new progress.
     * @param cancelOnTouch if the animation is running, you can set whether or not
     * you want the user to cancel the animation on {@value TOUCH}
     * or {@value DRAG}.
     */
    fun setProgress(progress: Int, animate: Boolean, cancelOnTouch: Boolean) {
        if (cancelAnimator != cancelOnTouch) {
            cancelAnimator = cancelOnTouch
        }
        setProgressInternal(progress, animate)
    }

    /**
     * This internal method sets the new progress. If the user wants to animate the sliding
     * progress, it will use [ObjectAnimator] to change the [.mVisualProgress].
     * [.mProgress] will only be dispatched once.
     *
     * @see .setProgress
     * @see .setProgress
     * @see .getProgress
     * @see .getRawProgress
     */
    private fun setProgressInternal(progress: Int, animate: Boolean) {
        if (!isValueValid(progress.toFloat())) return
        val scaled = (progress - minValue).toFloat() / (maxValue - minValue)
        if (scaled == rawProgress) return
        if (animate) {
            animator = ObjectAnimator.ofFloat(this, PROPERTY_VISUAL, scaled)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                animator.setAutoCancel(true)
            }
            animator.setDuration(ANIMATION_DURATION.toLong())
            animator.setInterpolator(TIME_INTERPOLATOR)
            animator.start()
        } else {
            setVisualProgress(scaled)
        }
        // Dispatch the new value
        rawProgress = scaled
        dispatchProgressChanged(false)
    }

    /**
     * Sets the progress for the visual. Used with [ObjectAnimator] to animate the progress.
     * @param visualProgress the new value to update the visual progress
     */
    private fun setVisualProgress(visualProgress: Float) {
        mVisualProgress = visualProgress
        updateText()
        updateIconAnimation()
        invalidate()
    }

    /**
     * Returns the progress of the slider in respect to [.getMinValue]
     * and [.getMaxValue].
     * @see .setProgress
     * @see .setProgress
     * @see .getRawProgress
     */
    /**
     * Sets the slider to the specified value without animation.
     *
     * Does not do anything if the value is less than or
     * more than maximum value.
     *
     * @see .getProgress
     * @param progress the desired progress the user wants.
     * Must be in between `minValue ` and `maxValue ` inclusive.
     */
    var progress: Int
        get() = Math.round((maxValue - minValue) * rawProgress + minValue)
        set(progress) {
            setProgressInternal(progress, false)
        }

    /**
     * This method allows for user to either drag the slider by passing
     * {@value DRAG} of if you want the progress to change on touch you can
     * pass {@value TOUCH}.
     * @see .getTouchMode
     * @param touchMode either {@value TOUCH} or {@value DRAG}.
     */
    fun setTouchMode(@TouchMode touchMode: Int) {
        if (touchMode != TOUCH && touchMode != DRAG) return
        if (this.touchMode != touchMode) {
            this.touchMode = touchMode
            invalidate()
        }
    }

    fun getTouchMode(): Int {
        return touchMode
    }

    /**
     * Sets the width of the stroke.
     *
     * @see .getStrokeWidth
     * @param width the new width of the stroke.
     */
    fun setStrokeWidth(@DimenRes width: Int) {
        if (strokeWidth != width) {
            strokeWidth = width
            strokePaint.strokeWidth = width.toFloat()
            invalidate()
        }
    }

    /**
     * Returns the stroke width.
     * @see .setStrokeWidth
     */
    fun getStrokeWidth(): Int {
        return strokeWidth
    }

    /**
     * Sets the size of the icon.
     * @see .getIconSize
     * @param iconSize the new size for the icon.
     */
    fun setIconSize(iconSize: Int) {
        if (this.iconSize == iconSize) return
        this.iconSize = iconSize
        updateIconSize()
        invalidate()
    }

    /**
     * Returns the icon size.
     * @see .setIconSize
     */
    fun getIconSize(): Int {
        return iconSize
    }

    /**
     * Sets the text for the label. By default, the label shows the percentage
     * in between [.minValue] and [.maxValue], but using this method
     * overrides the text it shows.
     * @see .getLabelText
     * @param labelText the string to draw on the slider.
     */
    fun setLabelText(labelText: String) {
        if (this.labelText == labelText) return
        this.labelText = labelText
        labelAsPercentage = false
        invalidate()
    }

    /**
     * Returns the current text from the label.
     *
     * @see .setLabelText
     */
    fun getLabelText(): String {
        return labelText
    }

    /**
     * This method  sets the label to show the percentage of the progress.
     *
     * @see .setLabelText
     * @see .getLabelText
     */
    fun setLabelAsPercentage() {
        if (!labelAsPercentage) {
            labelAsPercentage = true
            updateText()
            invalidate()
        }
    }

    /**
     * Returns the current corner radius of the view.
     *
     * @see .setCornerRadius
     */
    /**
     * Sets the corner radius of the view.
     *
     * @see .getCornerRadius
     * @param cornerRadius the new corner radius.
     */
    var cornerRadius: Int
        get() = mRadius
        set(cornerRadius) {
            if (mRadius != cornerRadius) {
                mRadius = cornerRadius
                updateShadowAndOutline(width, height)
                invalidate()
            }
        }

    /**
     * This method allows for the user to either show the icon or/and the label
     * and the order in which to show.
     * @see .getIconLabelVisibility
     * @param iconTextVisibility the new visibility.
     */
    fun setIconLabelVisibility(@IconTextVisibility iconTextVisibility: Int) {
        if (iconLabelVisibility != iconTextVisibility) {
            iconLabelVisibility = iconTextVisibility
            invalidate()
        }
    }

    /**
     * Returns the visibility of icon and label.
     *
     * @see .setIconLabelVisibility
     */
    @IconTextVisibility
    fun getIconLabelVisibility(): Int {
        return iconLabelVisibility
    }

    /**
     * Sets the icon drawable. Can pass null to remove the icon.
     *
     * @see .getIconDrawable
     * @param iconDrawable the new icon drawable.
     */
    fun setIconDrawable(iconDrawable: Drawable?) {
        if (this.iconDrawable === iconDrawable) return
        // Used if there was a LottieDrawable in use.
        cancelLoaderTask()
        clearComposition()
        this.iconDrawable = iconDrawable
        if (this.iconDrawable != null) {
            updateIconDrawableColor(iconColor)
            updateIconAnimation()
            updateIconSize()
            invalidate()
        }
    }

    /**
     * Returns the current icon drawable. May return null.
     *
     * @see .setIconDrawable
     */
    fun getIconDrawable(): Drawable? {
        return iconDrawable
    }

    /**
     * Sets the icon color. It uses [PorterDuffColorFilter] with
     * [PorterDuff.Mode.SRC_IN] to set the color state list.
     * It may be null to remove the color overlay.
     *
     * @see .setIconColor
     * @see .getIconColor
     * @param iconColor the new color state list or null
     */
    fun setIconColor(iconColor: ColorStateList?) {
        if (this.iconColor === iconColor) return
        this.iconColor = iconColor
        refreshColorState()
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
        setIconColor(ColorStateList.valueOf(color))
    }

    /**
     * Retuens the current icon color.
     *
     * @see .setIconColor
     * @see .setIconColor
     */
    fun getIconColor(): ColorStateList? {
        return iconColor
    }

    /**
     * Sets the new color state list for the stroke. This cannot be null.
     *
     * @see .getStrokeColor
     * @param strokeColor the new color state list.
     */
    fun setStrokeColor(strokeColor: ColorStateList) {
        if (this.strokeColor == strokeColor) return
        this.strokeColor = strokeColor
        refreshColorState()
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
        setStrokeColor(ColorStateList.valueOf(color))
    }

    /**
     * Returns the color of the stroke.
     *
     * @see .setStrokeColor
     * @see .setStrokeColor
     */
    fun getStrokeColor(): ColorStateList {
        return strokeColor!!
    }

    /**
     * Sets the new color state list for the inactive track.
     * This cannot be null.
     *
     * @see .setInactiveTrackColor
     * @see .getInactiveTrackColor
     * @param inactiveTrackColor the new color state list.
     */
    fun setInactiveTrackColor(inactiveTrackColor: ColorStateList) {
        if (this.inactiveTrackColor == inactiveTrackColor) return
        this.inactiveTrackColor = inactiveTrackColor
        refreshColorState()
    }

    /**
     * This method is basically [.setInactiveTrackColor] but with
     * [Color] parameters.
     *
     * @see .setInactiveTrackColor
     * @see .getInactiveTrackColor
     * @param color the new color.
     */
    fun setInactiveTrackColor(@ColorInt color: Int) {
        setInactiveTrackColor(ColorStateList.valueOf(color))
    }

    /**
     * Returns the color of the inactive track.
     *
     * @see .setInactiveTrackColor
     * @see .setInactiveTrackColor
     */
    fun getInactiveTrackColor(): ColorStateList {
        return inactiveTrackColor!!
    }

    /**
     * Sets the new color state list for the active track.
     * This cannot be null.
     *
     * @see .setActiveTrackColor
     * @see .getActiveTrackColor
     * @param activeTrackColor the new color state list.
     */
    fun setActiveTrackColor(activeTrackColor: ColorStateList) {
        if (this.activeTrackColor == activeTrackColor) return
        this.activeTrackColor = activeTrackColor
        refreshColorState()
    }

    /**
     * This method is basically [.setActiveTrackColor] but with
     * [Color] parameters.
     *
     * @see .setActiveTrackColor
     * @see .getActiveTrackColor
     * @param color the new color.
     */
    fun setActiveTrackColor(@ColorInt color: Int) {
        setActiveTrackColor(ColorStateList.valueOf(color))
    }

    /**
     * Returns the color of the active track.
     *
     * @see .setActiveTrackColor
     * @see .setActiveTrackColor
     */
    fun getActiveTrackColor(): ColorStateList {
        return activeTrackColor!!
    }

    /**
     * Returns the current label text color state list.
     *
     * @see .setLabelColor
     * @see .setLabelColor
     * @return the current label color.
     */
    fun getLabelColor(): ColorStateList {
        return labelColor!!
    }

    /**
     * Sets the label text to the specified color list. Does not do anything if
     * the color state list is the same.
     *
     * Note: If you have blending mode enabled it will still update the color but
     * depending on the [.mProgress] it might be showing the inverted color.
     * @see .getLabelColor
     * @param labelColor the new color state list.
     */
    fun setLabelColor(labelColor: ColorStateList) {
        if (this.labelColor == labelColor) return
        this.labelColor = labelColor
        refreshColorState()
    }

    /**
     * This is basically [.setLabelColor] but with a [Color] int
     * instead.
     * @see .getLabelColor
     * @param color the new color.
     */
    fun setLabelColor(@ColorInt color: Int) {
        setLabelColor(ColorStateList.valueOf(color))
    }

    /**
     * This method blends the icon and label based on the progress. If the progress
     * draws under the icon or label, it colors them with the inactive color.
     *
     * @see .isIconLabelBlending
     * @param blend either blend or not blend the color.
     */
    fun setIconLabelBlending(blend: Boolean) {
        if (blend != iconLabelBlending) {
            iconLabelBlending = blend
            invalidate()
        }
    }

    /**
     * Returns whether or not blending on icon and text is enabled.
     *
     * @see .setIconLabelBlending
     */
    fun isIconLabelBlending(): Boolean {
        return iconLabelBlending
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
        val progress = progress
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
            val value = mVisualProgress * 100
            labelText = String.format(Locale.ENGLISH, "%.0f", value) + "%"
        }
    }

    private fun updateIconAnimation() {
        if (iconDrawable == null) return
        if (iconDrawable is LottieDrawable) {
            val lottieDrawable = iconDrawable as LottieDrawable
            lottieDrawable.progress = mVisualProgress
        } else {
            val didChange = iconDrawable!!.setLevel((mVisualProgress * (maxValue - minValue)).toInt())
            if (didChange) {
                iconDrawable!!.invalidateSelf()
            }
        }
    }

    private fun updateIconDrawableColor(colorStateList: ColorStateList?) {
        if (iconDrawable == null) return
        var colorFilter: PorterDuffColorFilter? = null
        if (colorStateList != null) {
            colorFilter = PorterDuffColorFilter(getColorForState(colorStateList), PorterDuff.Mode.SRC_IN)
        }
        if (iconDrawable is LottieDrawable) {
            val keyPath = KeyPath("**")
            val callback = LottieValueCallback<ColorFilter>(colorFilter)
            (iconDrawable as LottieDrawable).addValueCallback(keyPath, LottieProperty.COLOR_FILTER, callback)
        } else { //            if (iconDrawable.getColorFilter() != colorFilter) {
//                iconDrawable.setColorFilter(colorFilter);
//            }
//
            DrawableCompat.setTintList(iconDrawable!!, colorStateList)
        }
    }

    private fun updateIconSize() {
        if (iconDrawable == null) return
        if (iconDrawable is LottieDrawable) { /// Workaround since LottieDrawable handles width/height on setScale
            val previousWidth = Math.max(1, iconDrawable.getBounds().width())
            val scale = iconSize.toFloat() / previousWidth
            (iconDrawable as LottieDrawable).scale = scale
        } else {
            iconDrawable!!.setBounds(0, 0, iconSize, iconSize)
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
                    outline.setRoundRect(left, top, right, bottom, mRadius.toFloat())
                }
            }
            strokePath.reset()
            strokePath.addRoundRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), mRadius.toFloat(), mRadius.toFloat(), Path.Direction.CW)
        } else {
            strokePath.reset()
            strokePath.addPath(createRoundRect(left, top, right, bottom, mRadius.toFloat()))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || animator != null && animator!!.isRunning && !cancelAnimator) return false
        if (animator != null) {
            animator!!.cancel()
            mVisualProgress = animator!!.animatedValue as Float
            rawProgress = mVisualProgress
            animator = null
        }
        val dY: Float
        var handledTouch = false
        var needsUpdate = false
        val height = height - (paddingTop + paddingBottom) - strokeWidth
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                lastTouchY = event.y - paddingTop - Math.round(strokeWidth / 2.toFloat())
                if (touchMode == TOUCH) {
                    mVisualProgress = Math.max(0.0f, Math.min(1.0f, 1.0f - lastTouchY / height))
                    rawProgress = mVisualProgress
                    needsUpdate = true
                }
                dispatchOnStartTrackingTouch()
                isPressed = true
                handledTouch = true
            }
            MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                dY = lastTouchY - event.y + paddingTop + Math.round(strokeWidth / 2.toFloat())
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
        newProgress = Math.max(0.0f, Math.min(1.0f, newProgress))
        if (rawProgress != newProgress) {
            mVisualProgress = newProgress
            rawProgress = mVisualProgress
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
        textPaint.color = getColorForState(labelColor!!)
        activeTrackPaint.color = getColorForState(activeTrackColor!!)
        inactiveTrackPaint.color = getColorForState(inactiveTrackColor!!)
        strokePaint.color = getColorForState(strokeColor!!)
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
        drawInactiveTrack(canvas, left, top, right, bottom)
        /// Draws active track
        drawActiveTrack(canvas, left, top, right, bottom)
        /// Draws the text
        drawLabel(canvas, left, top)
        /// Draws the icon
        drawIcon(canvas, left, top)
        // Draws the stroke
// We have to use round rect since path causes the view to draw another layer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.drawRoundRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), mRadius.toFloat(), mRadius.toFloat(), strokePaint)
        } else {
            canvas.drawPath(strokePath, strokePaint)
        }
    }

    private fun drawInactiveTrack(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int) {
        var bottom = bottom
        val inactiveTrackRange = 1f - mVisualProgress
        val height = bottom - top - strokeWidth
        bottom = Math.round(inactiveTrackRange * height) + paddingTop + strokeWidth / 2
        inactiveTrackRect[left + strokeWidth / 2, top + strokeWidth / 2, right - strokeWidth / 2] = bottom
        canvas.drawRect(inactiveTrackRect, inactiveTrackPaint)
    }

    private fun drawActiveTrack(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int) {
        var top = top
        val height = bottom - top - strokeWidth
        top = Math.round(height - mVisualProgress * height) + paddingTop + strokeWidth / 2
        activeTrackRect[left + strokeWidth / 2, top, right - strokeWidth / 2] = bottom - strokeWidth / 2
        canvas.drawRect(activeTrackRect, activeTrackPaint)
    }

    private fun drawLabel(canvas: Canvas, left: Int, top: Int) {
        var left = left
        var top = top
        if (!canDrawLabel()) return
        textPaint.getTextBounds(labelText, 0, labelText.length, labelBounds)
        labelBounds.right = textPaint.measureText(labelText).toInt()
        val width = width - (left + paddingEnd)
        val height = height - (top + paddingBottom)
        val position = if (iconLabelVisibility == TEXT) 0.5f else if (iconLabelVisibility == TEXTICON) 0.25f else 0.75f
        left += (width - labelBounds.width()) / 2
        top += (height * position).toInt() - labelBounds.height() / 2
        if (iconLabelBlending) {
            val isInBounds = activeTrackRect.contains(left, top + labelBounds.height() / 2, left + labelBounds.width(), top + labelBounds.height())
            if (isInBounds) {
                if (textPaint.color != getColorForState(inactiveTrackColor!!)) {
                    textPaint.color = getColorForState(inactiveTrackColor!!)
                }
            } else {
                if (textPaint.color != getColorForState(labelColor!!)) {
                    textPaint.color = getColorForState(labelColor!!)
                }
            }
        } else {
            if (textPaint.color != getColorForState(labelColor!!)) {
                textPaint.color = getColorForState(labelColor!!)
            }
        }
        canvas.save()
        canvas.rotate(-rotation, paddingStart + width / 2.toFloat(), height * position + paddingTop)
        canvas.drawText(labelText, 0, labelText.length, left.toFloat(), top + labelBounds.height().toFloat(), textPaint)
        canvas.restore()
    }

    private fun drawIcon(canvas: Canvas, left: Int, top: Int) {
        var left = left
        var top = top
        if (iconDrawable == null || !canDrawIcon()) return
        val iconWidth = iconDrawable!!.bounds.width()
        val iconHeight = iconDrawable!!.bounds.height()
        val width = width - (left + paddingEnd)
        val height = height - (top + paddingBottom)
        val position = if (iconLabelVisibility == ICON) 0.5f else if (iconLabelVisibility == TEXTICON) 0.75f else 0.25f
        left += (width - iconWidth) / 2
        top += (height * position).toInt() - iconHeight / 2
        drawableRect[left, top, left + iconWidth] = top + iconHeight
        if (iconLabelBlending) {
            val isInBounds = activeTrackRect.contains(left, top + iconHeight / 2, left + iconWidth, top + iconHeight)
            if (isInBounds) {
                updateIconDrawableColor(inactiveTrackColor)
            } else {
                updateIconDrawableColor(iconColor)
            }
        } else {
            updateIconDrawableColor(iconColor)
        }
        canvas.save()
        canvas.rotate(-rotation, paddingStart + width / 2.toFloat(),
                paddingTop + height * position)
        canvas.translate(left.toFloat(), top.toFloat())
        iconDrawable!!.draw(canvas)
        canvas.restore()
    }

    @ColorInt
    private fun getColorForState(colorStateList: ColorStateList): Int {
        return colorStateList.getColorForState(drawableState, colorStateList.defaultColor)
    }

    private fun refreshColorState() {
        drawableStateChanged()
        invalidate()
    }

    private fun isValueValid(value: Float): Boolean {
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
     * This is a wrapper for [.setVisualProgress] so it can be used
     * with ObjectAnimator.
     */
    private val PROPERTY_VISUAL: Property<IOSlider, Float> = object : Property<IOSlider, Float>(Float::class.java, VISUAL_PROGRESS) {
        override fun set(`object`: IOSlider, value: Float) {
            `object`.setVisualProgress(value)
        }

        override fun get(`object`: IOSlider): Float {
            return `object`.mVisualProgress
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val state = IOSliderSaveState(superState)
        state.minValue = minValue
        state.maxValue = maxValue
        state.mProgress = rawProgress
        state.mVisualProgress = mVisualProgress
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
        mVisualProgress = savedState.mVisualProgress
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

    private class IOSliderSaveState : BaseSavedState {
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

        internal constructor(source: Parcel) : super(source) {
            minValue = source.readInt()
            maxValue = source.readInt()
            mProgress = source.readFloat()
            mVisualProgress = source.readFloat()
            iconSize = source.readInt()
            labelSize = source.readInt()
            labelText = source.readString()
            labelAsPercentage = source.readBoolean()
            iconLabelBlend = source.readBoolean()
            touchMode = source.readInt()
            iconTextVisible = source.readInt()
        }

        internal constructor(superState: Parcelable?) : super(superState) {}

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(minValue)
            out.writeInt(maxValue)
            out.writeFloat(mProgress)
            out.writeFloat(mVisualProgress)
            out.writeInt(iconSize)
            out.writeInt(labelSize)
            out.writeString(labelText)
            out.writeBoolean(labelAsPercentage)
            out.writeBoolean(iconLabelBlend)
            out.writeInt(touchMode)
            out.writeInt(iconTextVisible)
        }

        companion object {
            val CREATOR: Parcelable.Creator<IOSliderSaveState> = object : Parcelable.Creator<IOSliderSaveState?> {
                override fun createFromParcel(`in`: Parcel): IOSliderSaveState? {
                    return IOSliderSaveState(`in`)
                }

                override fun newArray(size: Int): Array<IOSliderSaveState?> {
                    return arrayOfNulls(size)
                }
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

    init {
        isSaveEnabled = true
        getResources(context, attrs, defStyleAttr)
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
        val typeResource: String
        typeResource = try {
            context.resources.getResourceTypeName(iconResource)
        } catch (e: NotFoundException) {
            TYPE_NOT_FOUND
        }
        if (typeResource == TYPE_RAW) {
            setIconAnimation(iconResource)
        } else if (typeResource == TYPE_DRAWABLE) {
            setIconDrawable(ContextCompat.getDrawable(context, iconResource))
        } else {
            Log.d(TAG, "IOSlider: Did not set an Icon Drawable.")
        }
    }
}