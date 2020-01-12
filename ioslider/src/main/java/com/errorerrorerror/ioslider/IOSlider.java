package com.errorerrorerror.ioslider;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.AnyRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.Dimension;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.StyleableRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieListener;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.LottieTask;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IOSlider extends View {

    private static final String TAG = IOSlider.class.getName();
    private static final int DEF_STYLE_RES = R.style.Widget_IOSlider;
    private static final String TYPE_RAW  = "raw";
    private static final String TYPE_DRAWABLE  = "drawable";
    private static final String TYPE_NOT_FOUND  = "not_found";

    /**
     * This callback listens for changes in {@link IOSlider}.
     */
    public interface OnSliderChangeListener {
        /**
         * Notifies any changes to the progress level.
         *
         * @param slider The current slider that has been changed.
         * @param progress the current progress level. The value varies
         *                 between {@link #minValue} and {@link #maxValue}.
         *                 These values can be set by using {@link #setMinValue(int)}
         *                 and {@link #setMaxValue(int)}
         * @param fromUser Alerts the client if the progress was changed by the user.
         */
        void onProgressChanged(IOSlider slider, int progress, boolean fromUser);
    }

    /**
     * This callback listens for changes on touch.
     */
    public interface OnSliderTouchListener {
        /**
         * Notifies when user touches the slider.
         * @param slider The slider that was touched.
         */
        void onStartTrackingTouch(IOSlider slider);

        /**
         * Notifies when the user stops touching the slider.
         * @param slider The slider that stopped being touched.
         */
        void onStopTrackingTouch(IOSlider slider);
    }

    @IntDef({DRAG, TOUCH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TouchMode {}
    @TouchMode private int touchMode;

    /**
     * Sets the slider to change on drag.
     */
    public static final int DRAG = 0;

    /**
     * Sets the slider to change on touch.
     */
    public static final int TOUCH = 1;

    @IntDef({TEXT, ICON, TEXTICON, ICONTEXT, NONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface IconTextVisibility {}
    @IconTextVisibility private int iconLabelVisibility;

    /**
     * Shows only the icon.
     */
    public static final int ICON = 0;

    /**
     * Shows only the label.
     */
    public static final int TEXT = 1;

    /**
     * Shows the Icon first, then the label.
     */
    public static final int ICONTEXT = 2;

    /**
     * Shows the label first then the icon.
     */
    public static final int TEXTICON = 3;

    /**
     * Does not show the icon and the label.
     */
    public static final int NONE = 4;

    /**
     * The corner radius of the view.
     */
    private int mRadius;

    /**
     * The size of the label.
     */
    private int labelSize;

    /**
     * The actual progress.
     */
    @FloatRange(from = 0f, to = 1f) private float mProgress;

    /**
     * This is used mainly for animating the progress of the view so
     * it will not interfere with the actual progress.
     */
    @FloatRange(from = 0f, to = 1f) private float mVisualProgress;

    /**
     * The minimum value the progress can go.
     */
    private int minValue;

    /**
     * The maximum value the progress can go.
     */
    private int maxValue;

    /**
     * The width of the stroke.
     */
    private int strokeWidth;

    /**
     * The text to show.
     */
    private String labelText = "";

    /**
     * The size of the icon.
     */
    @Dimension private int iconSize;

    /**
     * Blend the icon and label based on the progress.
     */
    private boolean iconLabelBlending;

    /**
     * Whether or not it should set the text to show the percentage of
     * the progress.
     */
    private boolean labelAsPercentage = true;

    @NonNull private final Paint inactiveTrackPaint;
    @NonNull private final Paint activeTrackPaint;
    @NonNull private final Paint textPaint;
    @NonNull private final Paint strokePaint;

    private ColorStateList labelColor;
    private ColorStateList inactiveTrackColor;
    private ColorStateList activeTrackColor;
    @Nullable private ColorStateList iconColor;
    private ColorStateList strokeColor;

    @NonNull private final Rect activeTrackRect;
    @NonNull private final Rect inactiveTrackRect;
    @NonNull private final Rect drawableRect;
    @NonNull private final Rect labelBounds;
    @NonNull private final Path strokePath;

    @AnyRes private int iconResource;
    @Nullable private Drawable iconDrawable;
    private boolean cancelAnimator;
    private float lastTouchY;

    @NonNull private List<OnSliderChangeListener> onSliderChangeListener = new ArrayList<>();
    @NonNull private List<OnSliderTouchListener> onSliderTouchListener = new ArrayList<>();
    @Nullable private ObjectAnimator animator;
    private static final String VISUAL_PROGRESS = "VisualProgress";
    private static final FastOutSlowInInterpolator TIME_INTERPOLATOR = new FastOutSlowInInterpolator();
    private static final int ANIMATION_DURATION = 1000;
    private LottieTask<LottieComposition> lottieCompositionTask;

    public IOSlider(Context context) {
        this(context, null);
    }

    public IOSlider(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.IOSliderStyle);
    }

    public IOSlider(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSaveEnabled(true);

        getResources(context, attrs, defStyleAttr);

        inactiveTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactiveTrackPaint.setStyle(Paint.Style.FILL);
        activeTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activeTrackPaint.setStyle(Paint.Style.FILL);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setTextSize(labelSize);
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidth);
        activeTrackRect = new Rect();
        inactiveTrackRect = new Rect();
        labelBounds = new Rect();
        drawableRect = new Rect();
        strokePath = new Path();

        String typeResource;
        try {
            typeResource = context.getResources().getResourceTypeName(iconResource);
        } catch (Resources.NotFoundException e) {
            typeResource = TYPE_NOT_FOUND;
        }
        if (typeResource.equals(TYPE_RAW)) {
            setIconAnimation(iconResource);
        } else if (typeResource.equals(TYPE_DRAWABLE)) {
            setIconDrawable(ContextCompat.getDrawable(context, iconResource));
        } else {
            Log.d(TAG, "IOSlider: Did not set an Icon Drawable.");
        }
    }

    private void getResources(Context context, @Nullable AttributeSet attrs, int style) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.IOSlider, style, IOSlider.DEF_STYLE_RES);

        mRadius = ta.getDimensionPixelSize(R.styleable.IOSlider_cornerRadius, context.getResources().getDimensionPixelSize(R.dimen.corner_radius));

        iconResource = ta.getResourceId(R.styleable.IOSlider_icon, 0);
        iconColor = getColorStateList(context, ta, R.styleable.IOSlider_iconColor, R.color.slider_icon_color);
        iconSize = ta.getDimensionPixelSize(R.styleable.IOSlider_iconSize, getContext().getResources().getDimensionPixelSize(R.dimen.icon_size));

        if (ta.hasValue(R.styleable.IOSlider_labelText)) {
            labelText = ta.getString(R.styleable.IOSlider_labelText);
            labelAsPercentage = false;
        }

        labelColor = getColorStateList(context, ta, R.styleable.IOSlider_labelColor, R.color.slider_text_color);
        labelSize = ta.getDimensionPixelSize(R.styleable.IOSlider_labelSize, context.getResources().getDimensionPixelSize(R.dimen.text_size));

        activeTrackColor = getColorStateList(context, ta, R.styleable.IOSlider_activeTrackColor, R.color.slider_active_track_color);
        inactiveTrackColor = getColorStateList(context, ta, R.styleable.IOSlider_inactiveTrackColor, R.color.slider_inactive_track_color);

        minValue = ta.getInt(R.styleable.IOSlider_min, 0);
        maxValue = ta.getInt(R.styleable.IOSlider_max, 100);

        strokeWidth = ta.getDimensionPixelSize(R.styleable.IOSlider_strokeWidth, 0);
        strokeColor = getColorStateList(context, ta, R.styleable.IOSlider_strokeColor, android.R.color.transparent);

        iconLabelBlending = ta.getBoolean(R.styleable.IOSlider_blendIconLabel, false);

        touchMode = ta.getInt(R.styleable.IOSlider_touchMode, DRAG);

        setProgress(ta.getInt(R.styleable.IOSlider_progress, 50));

        iconLabelVisibility = ta.getInt(R.styleable.IOSlider_iconTextVisibility, ICONTEXT);

        ta.recycle();

        validateMinValue();
        validateMaxValue();
    }

    /**
     * Sets the minimum value to the specified value.
     *
     * @throws IllegalArgumentException if the minimum value is greater than or
     *                                  equal to {@link #maxValue}
     * @see #getMinValue()
     * @param minValue the desired minimum value for the slider.
     */
    public void setMinValue(int minValue) {
        this.minValue = minValue;
        validateMinValue();
    }

    /**
     * Returns the minimum value of the progress.
     * @see #setMinValue(int)
     * @return the minimum value.
     */
    public int getMinValue() {
        return minValue;
    }

    /**
     * Sets the maximum value to the specified value.
     *
     * @throws IllegalArgumentException if the maximum value is less than or
     *                                  equal to {@link #minValue}
     * @see #getMaxValue()
     * @param maxValue the desired maximum value for the slider
     */
    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
        validateMaxValue();
    }

    /**
     * Returns the maximum value of the progress.
     * @see #setMaxValue(int)
     * @return the maximum value.
     */
    public int getMaxValue() {
        return maxValue;
    }

    /**
     * Sets the slider to the specified value without animation.
     *
     * Does not do anything if the value is less than or
     * more than maximum value.
     *
     * @see #getProgress()
     * @param progress the desired progress the user wants.
     *                 Must be in between {@code minValue } and {@code maxValue } inclusive.
     */
    public void setProgress(int progress) {
        setProgressInternal(progress, false);
    }

    /**
     * This is basically {@link #setProgress(int)} but has the option to
     * animate the progress. For interpolation it uses {@link FastOutSlowInInterpolator}
     * to animate the progress with {@value #ANIMATION_DURATION} duration.
     *
     * Note: It does not manipulate {@link #mProgress} since for the animation
     * to occur, it requires to have the value to change. Instead, this animation
     * occurs in {@code mVisualProgress}. It will only dispatch on change once and
     * that's the new progress value.
     *
     * @see #setProgress(int)
     * @see #getProgress()
     * @param progress the progress to animate.
     * @param animate sets whether or not to animate the new progress.
     * @param cancelOnTouch if the animation is running, you can set whether or not
     *                      you want the user to cancel the animation on {@value TOUCH}
     *                      or {@value DRAG}.
     */
    public void setProgress(int progress, boolean animate, boolean cancelOnTouch) {
        if (cancelAnimator != cancelOnTouch) {
            cancelAnimator = cancelOnTouch;
        }
        setProgressInternal(progress, animate);
    }

    /**
     * This internal method sets the new progress. If the user wants to animate the sliding
     * progress, it will use {@link ObjectAnimator} to change the {@link #mVisualProgress}.
     * {@link #mProgress} will only be dispatched once.
     *
     * @see #setProgress(int)
     * @see #setProgress(int, boolean, boolean)
     * @see #getProgress()
     * @see #getRawProgress()
     */
    private void setProgressInternal(int progress, boolean animate) {
        if(!isValueValid(progress)) return;

        float scaled = (float) (progress - minValue) / (maxValue - minValue);
        if (scaled == mProgress) return;

        if (animate) {
            animator = ObjectAnimator.ofFloat(this,  PROPERTY_VISUAL, scaled);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                animator.setAutoCancel(true);
            }
            animator.setDuration(ANIMATION_DURATION);
            animator.setInterpolator(TIME_INTERPOLATOR);
            animator.start();
        } else {
            setVisualProgress(scaled);
        }

        // Dispatch the new value
        mProgress = scaled;
        dispatchProgressChanged(false);
    }

    /**
     * Sets the progress for the visual. Used with {@link ObjectAnimator} to animate the progress.
     * @param visualProgress the new value to update the visual progress
     */
    private void setVisualProgress(float visualProgress) {
        this.mVisualProgress = visualProgress;
        updateText();
        updateIconAnimation();

        invalidate();
    }

    /**
     * Returns the progress of the slider in respect to {@link #getMinValue()}
     * and {@link #getMaxValue()}.
     * @see #setProgress(int)
     * @see #setProgress(int, boolean, boolean)
     * @see #getRawProgress()
     */
    public int getProgress() {
        return Math.round((maxValue - minValue) * mProgress + minValue);
    }

    /**
     * This method returns the actual progress of the slider. This is used
     * to draw the slider's active track and get the value of the progress.
     *
     * @see #getProgress()
     */
    @FloatRange(from = 0f, to = 1f)
    public float getRawProgress() {
        return mProgress;
    }

    /**
     * This method allows for user to either drag the slider by passing
     * {@value DRAG} of if you want the progress to change on touch you can
     * pass {@value TOUCH}.
     * @see #getTouchMode()
     * @param touchMode either {@value TOUCH} or {@value DRAG}.
     */
    public void setTouchMode(@TouchMode int touchMode) {
        if (touchMode != TOUCH && touchMode != DRAG) return;
        if (this.touchMode != touchMode) {
            this.touchMode = touchMode;
            invalidate();
        }
    }

    public int getTouchMode() {
        return touchMode;
    }

    /**
     * Sets the width of the stroke.
     *
     * @see #getStrokeWidth()
     * @param width the new width of the stroke.
     */
    public void setStrokeWidth(@DimenRes int width) {
        if (strokeWidth != width) {
            strokeWidth = width;
            strokePaint.setStrokeWidth(width);
            invalidate();
        }
    }

    /**
     * Returns the stroke width.
     * @see #setStrokeWidth(int)
     */
    public int getStrokeWidth() {
        return strokeWidth;
    }

    /**
     * Sets the size of the icon.
     * @see #getIconSize()
     * @param iconSize the new size for the icon.
     */
    public void setIconSize(int iconSize) {
        if (this.iconSize == iconSize) return;
        this.iconSize = iconSize;
        updateIconSize();
        invalidate();
    }

    /**
     * Returns the icon size.
     * @see #setIconSize(int)
     */
    public int getIconSize() {
        return iconSize;
    }

    /**
     * Sets the text for the label. By default, the label shows the percentage
     * in between {@link #minValue} and {@link #maxValue}, but using this method
     * overrides the text it shows.
     * @see #getLabelText()
     * @param labelText the string to draw on the slider.
     */
    public void setLabelText(@NonNull String labelText) {
        if (this.labelText.equals(labelText)) return;
        this.labelText = labelText;
        labelAsPercentage = false;
        invalidate();
    }

    /**
     * Returns the current text from the label.
     *
     * @see #setLabelText(String)
     */
    @NonNull
    public String getLabelText() {
        return labelText;
    }

    /**
     * This method  sets the label to show the percentage of the progress.
     *
     * @see #setLabelText(String)
     * @see #getLabelText()
     */
    public void setLabelAsPercentage() {
        if (!labelAsPercentage) {
            labelAsPercentage = true;
            updateText();
            invalidate();
        }
    }

    /**
     * Sets the corner radius of the view.
     *
     * @see #getCornerRadius()
     * @param cornerRadius the new corner radius.
     */
    public void setCornerRadius(int cornerRadius) {
        if (mRadius != cornerRadius) {
            mRadius = cornerRadius;
            updateShadowAndOutline(getWidth(), getHeight());
            invalidate();
        }
    }

    /**
     * Returns the current corner radius of the view.
     *
     * @see #setCornerRadius(int)
     */
    public int getCornerRadius() {
        return mRadius;
    }

    /**
     * This method allows for the user to either show the icon or/and the label
     * and the order in which to show.
     * @see #getIconLabelVisibility()
     * @param iconTextVisibility the new visibility.
     */
    public void setIconLabelVisibility(@IconTextVisibility int iconTextVisibility) {
        if (iconLabelVisibility != iconTextVisibility) {
            this.iconLabelVisibility = iconTextVisibility;
            invalidate();
        }
    }

    /**
     * Returns the visibility of icon and label.
     *
     * @see #setIconLabelVisibility(int)
     */
    @IconTextVisibility
    public int getIconLabelVisibility() {
        return iconLabelVisibility;
    }

    /**
     * Sets the icon drawable. Can pass null to remove the icon.
     *
     * @see #getIconDrawable()
     * @param iconDrawable the new icon drawable.
     */
    public void setIconDrawable(@Nullable Drawable iconDrawable) {
        if (this.iconDrawable == iconDrawable) return;
        // Used if there was a LottieDrawable in use.
        cancelLoaderTask();
        clearComposition();
        this.iconDrawable = iconDrawable;
        if (this.iconDrawable != null) {
            updateIconDrawableColor(iconColor);
            updateIconAnimation();
            updateIconSize();
            invalidate();
        }
    }

    /**
     * Returns the current icon drawable. May return null.
     *
     * @see #setIconDrawable(Drawable)
     */
    @Nullable
    public Drawable getIconDrawable() {
        return iconDrawable;
    }

    /**
     * Sets the icon color. It uses {@link PorterDuffColorFilter} with
     * {@link PorterDuff.Mode#SRC_IN} to set the color state list.
     * It may be null to remove the color overlay.
     *
     * @see #setIconColor(int)
     * @see #getIconColor()
     * @param iconColor the new color state list or null
     */
    public void setIconColor(@Nullable ColorStateList iconColor) {
        if (this.iconColor == iconColor) return;
        this.iconColor = iconColor;
        refreshColorState();
    }

    /**
     * This method is basically {@link #setIconColor(ColorStateList)} but with
     * {@link Color} parameters.
     *
     * @see #setIconColor(ColorStateList)
     * @see #getIconColor()
     * @param color the new color filter.
     */

    public void setIconColor(@ColorInt int color) {
        setIconColor(ColorStateList.valueOf(color));
    }

    /**
     * Retuens the current icon color.
     *
     * @see #setIconColor(ColorStateList)
     * @see #setIconColor(int)
     */
    @Nullable
    public ColorStateList getIconColor() {
        return iconColor;
    }

    /**
     * Sets the new color state list for the stroke. This cannot be null.
     *
     * @see #getStrokeColor()
     * @param strokeColor the new color state list.
     */
    public void setStrokeColor(@NonNull ColorStateList strokeColor) {
        if (this.strokeColor.equals(strokeColor)) return;
        this.strokeColor = strokeColor;
        refreshColorState();
    }

    /**
     * This method is basically {@link #setStrokeColor(ColorStateList)} but with
     * {@link Color} parameters.
     *
     * @see #setStrokeColor(ColorStateList)
     * @see #getStrokeColor()
     * @param color the new color.
     */
    public void setStrokeColor(@ColorInt int color) {
        setStrokeColor(ColorStateList.valueOf(color));
    }

    /**
     * Returns the color of the stroke.
     *
     * @see #setStrokeColor(int)
     * @see #setStrokeColor(ColorStateList)
     */
    @NonNull
    public ColorStateList getStrokeColor() {
        return strokeColor;
    }

    /**
     * Sets the new color state list for the inactive track.
     * This cannot be null.
     *
     * @see #setInactiveTrackColor(int)
     * @see #getInactiveTrackColor()
     * @param inactiveTrackColor the new color state list.
     */
    public void setInactiveTrackColor(@NonNull ColorStateList inactiveTrackColor) {
        if (this.inactiveTrackColor.equals(inactiveTrackColor)) return;
        this.inactiveTrackColor = inactiveTrackColor;
        refreshColorState();
    }

    /**
     * This method is basically {@link #setInactiveTrackColor(ColorStateList)} but with
     * {@link Color} parameters.
     *
     * @see #setInactiveTrackColor(ColorStateList)
     * @see #getInactiveTrackColor()
     * @param color the new color.
     */
    public void setInactiveTrackColor(@ColorInt int color) {
        setInactiveTrackColor(ColorStateList.valueOf(color));
    }

    /**
     * Returns the color of the inactive track.
     *
     * @see #setInactiveTrackColor(int)
     * @see #setInactiveTrackColor(ColorStateList)
     */
    @NonNull
    public ColorStateList getInactiveTrackColor() {
        return inactiveTrackColor;
    }

    /**
     * Sets the new color state list for the active track.
     * This cannot be null.
     *
     * @see #setActiveTrackColor(int)
     * @see #getActiveTrackColor()
     * @param activeTrackColor the new color state list.
     */
    public void setActiveTrackColor(@NonNull ColorStateList activeTrackColor) {
        if (this.activeTrackColor.equals(activeTrackColor)) return;
        this.activeTrackColor = activeTrackColor;
        refreshColorState();
    }

    /**
     * This method is basically {@link #setActiveTrackColor(ColorStateList)} but with
     * {@link Color} parameters.
     *
     * @see #setActiveTrackColor(ColorStateList)
     * @see #getActiveTrackColor()
     * @param color the new color.
     */
    public void setActiveTrackColor(@ColorInt int color) {
        setActiveTrackColor(ColorStateList.valueOf(color));
    }

    /**
     * Returns the color of the active track.
     *
     * @see #setActiveTrackColor(int)
     * @see #setActiveTrackColor(ColorStateList)
     */
    @NonNull
    public ColorStateList getActiveTrackColor() {
        return activeTrackColor;
    }

    /**
     * Returns the current label text color state list.
     *
     * @see #setLabelColor(ColorStateList)
     * @see #setLabelColor(int)
     * @return the current label color.
     */
    @NonNull
    public ColorStateList getLabelColor() {
        return labelColor;
    }

    /**
     * Sets the label text to the specified color list. Does not do anything if
     * the color state list is the same.
     *
     * Note: If you have blending mode enabled it will still update the color but
     * depending on the {@link #mProgress} it might be showing the inverted color.
     * @see #getLabelColor()
     * @param labelColor the new color state list.
     */
    public void setLabelColor(@NonNull ColorStateList labelColor) {
        if (this.labelColor.equals(labelColor)) return;
        this.labelColor = labelColor;
        refreshColorState();
    }

    /**
     * This is basically {@link #setLabelColor(ColorStateList)} but with a {@link Color} int
     * instead.
     * @see #getLabelColor()
     * @param color the new color.
     */
    public void setLabelColor(@ColorInt int color) {
        setLabelColor(ColorStateList.valueOf(color));
    }

    /**
     * This method blends the icon and label based on the progress. If the progress
     * draws under the icon or label, it colors them with the inactive color.
     *
     * @see #isIconLabelBlending()
     * @param blend either blend or not blend the color.
     */
    public void setIconLabelBlending(boolean blend) {
        if (blend != iconLabelBlending) {
            this.iconLabelBlending = blend;
            invalidate();
        }
    }

    /**
     * Returns whether or not blending on icon and text is enabled.
     *
     * @see #setIconLabelBlending(boolean)
     */
    public boolean isIconLabelBlending() {
        return iconLabelBlending;
    }

    /**
     *
     * @see OnSliderChangeListener
     */
    public void addOnSliderChangeListener(@NonNull OnSliderChangeListener listener) {
        this.onSliderChangeListener.add(listener);
    }

    public void removeOnSliderChangeListener(@NonNull OnSliderChangeListener listener) {
        this.onSliderChangeListener.remove(listener);
    }

    private void dispatchProgressChanged(boolean fromUser) {
        final int progress = getProgress();
        for(OnSliderChangeListener changeListener : onSliderChangeListener){
            changeListener.onProgressChanged(this, progress, fromUser);
        }
    }

    public void addOnSliderTouchListener(@NonNull OnSliderTouchListener listener) {
        this.onSliderTouchListener.add(listener);
    }

    public void removeOnSliderTouchListener(@NonNull OnSliderTouchListener listener) {
        this.onSliderTouchListener.remove(listener);
    }

    private void dispatchOnStartTrackingTouch() {
        for (OnSliderTouchListener listener : onSliderTouchListener) {
            listener.onStartTrackingTouch(this);
        }
    }

    private void dispatchOnStopTrackingTouch() {
        for (OnSliderTouchListener listener : onSliderTouchListener) {
            listener.onStopTrackingTouch(this);
        }
    }

    private void updateText() {
        if (labelAsPercentage) {
            float value = mVisualProgress * 100;
            labelText = String.format(Locale.ENGLISH, "%.0f", value) + "%";
        }
    }

    private void updateIconAnimation() {
        if (iconDrawable == null) return;

        if (iconDrawable instanceof LottieDrawable) {
            final LottieDrawable lottieDrawable = (LottieDrawable) iconDrawable;
            lottieDrawable.setProgress(mVisualProgress);
        } else {
            boolean didChange = iconDrawable.setLevel((int) (mVisualProgress * (maxValue - minValue)));
            if (didChange) {
                iconDrawable.invalidateSelf();
            }
        }
    }

    private void updateIconDrawableColor(@Nullable ColorStateList colorStateList) {
        if (iconDrawable == null) return;

        PorterDuffColorFilter colorFilter = null;
        if (colorStateList != null) {
            colorFilter = new PorterDuffColorFilter(getColorForState(colorStateList), PorterDuff.Mode.SRC_IN);
        }

        if (iconDrawable instanceof LottieDrawable) {
            KeyPath keyPath = new KeyPath("**");
            LottieValueCallback<ColorFilter> callback = new LottieValueCallback<>(colorFilter);
            ((LottieDrawable) iconDrawable).addValueCallback(keyPath, LottieProperty.COLOR_FILTER, callback);

        } else {
//            if (iconDrawable.getColorFilter() != colorFilter) {
//                iconDrawable.setColorFilter(colorFilter);
//            }
//
            DrawableCompat.setTintList(iconDrawable, colorStateList);
        }
    }

    private void updateIconSize() {
        if (iconDrawable == null) return;

        if (iconDrawable instanceof LottieDrawable) {

            /// Workaround since LottieDrawable handles width/height on setScale
            int previousWidth = Math.max(1, iconDrawable.getBounds().width());
            float scale = (float) iconSize/previousWidth;
            ((LottieDrawable) iconDrawable).setScale(scale);
        } else {
            iconDrawable.setBounds(0,0, iconSize, iconSize);
        }
    }

    private void updateTextSize() {
        if (textPaint.getTextSize() != labelSize) {
            textPaint.setTextSize(labelSize);
        }
    }

    private void updateShadowAndOutline(int width, int height) {
        int left = getPaddingStart();
        int top = getPaddingTop();
        int right = width - getPaddingEnd();
        int bottom = height - getPaddingBottom();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(left, top, right, bottom, mRadius);
                }
            });

            strokePath.reset();
            strokePath.addRoundRect(left, top, right, bottom, mRadius, mRadius, Path.Direction.CW);
        } else {
            strokePath.reset();
            strokePath.addPath(createRoundRect(left, top, right, bottom, mRadius));
        }
    }

    /// For api lower than 21
    public static Path createRoundRect(int left, int top, int right, int bottom, float radius){
        Path path = new Path();
        path.moveTo(left + radius,top);
        path.lineTo(right - radius,top);
        path.quadTo(right, top, right, top + radius);
        path.lineTo(right ,bottom - radius);
        path.quadTo(right ,bottom, right - radius, bottom);
        path.lineTo(left + radius,bottom);
        path.quadTo(left,bottom,left, bottom - radius);
        path.lineTo(left,top + radius);
        path.quadTo(left,top, left + radius, top);
        path.close();

        return path;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || (animator != null && animator.isRunning() && !cancelAnimator)) return false;
        if (animator != null) {
            animator.cancel();
            mProgress = mVisualProgress = (float) animator.getAnimatedValue();
            animator = null;
        }

        float dY;
        boolean handledTouch = false;
        boolean needsUpdate = false;
        int height = getHeight() - (getPaddingTop() + getPaddingBottom()) - strokeWidth;

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                lastTouchY = event.getY() - getPaddingTop() - Math.round(strokeWidth/2);
                if (touchMode == TOUCH) {
                    mProgress = mVisualProgress = Math.max(0.0f, Math.min(1.0f, 1.0f - (lastTouchY / height)));
                    needsUpdate = true;
                }
                dispatchOnStartTrackingTouch();
                setPressed(true);
                handledTouch = true;
                break;

            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                dY = lastTouchY - event.getY() + getPaddingTop() + Math.round(strokeWidth/2);
                needsUpdate = calculateValueFromEvent(dY, height);
                lastTouchY-= dY;
                setPressed(true);
                handledTouch = true;
                break;

            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                dispatchOnStopTrackingTouch();
                setPressed(false);
                handledTouch = true;
                break;

            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                setPressed(false);
                handledTouch = true;
                needsUpdate = true;
                break;
        }

        if (needsUpdate) {
            dispatchProgressChanged(true);
            updateText();
            updateIconAnimation();
            invalidate();
        }

        return handledTouch;
    }

    private boolean calculateValueFromEvent(float distanceYY, int height) {
        boolean updatedValue = false;
        float distance = distanceYY / height;
        float newProgress = mProgress + distance;
        newProgress = Math.max(0.0f, Math.min(1.0f, newProgress));
        if (mProgress != newProgress) {
            mProgress = mVisualProgress = newProgress;
            updatedValue = true;
        }

        return updatedValue;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        /// Sets shadow and the outline
        updateShadowAndOutline(w, h);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        textPaint.setColor(getColorForState(labelColor));
        activeTrackPaint.setColor(getColorForState(activeTrackColor));
        inactiveTrackPaint.setColor(getColorForState(inactiveTrackColor));
        strokePaint.setColor(getColorForState(strokeColor));

        updateIconDrawableColor(iconColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int left = getPaddingStart();
        int top = getPaddingTop();
        int right = getWidth() - getPaddingEnd();
        int bottom = getHeight() - getPaddingBottom();

        /// Clips view to stroke path
        canvas.clipPath(strokePath);

        /// Draws inactive track
        drawInactiveTrack(canvas, left, top, right, bottom);

        /// Draws active track
        drawActiveTrack(canvas, left, top, right, bottom);

        /// Draws the text
        drawLabel(canvas, left, top);

        /// Draws the icon
        drawIcon(canvas, left, top);

        // Draws the stroke
        // We have to use round rect since path causes the view to draw another layer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.drawRoundRect(left, top, right, bottom, mRadius, mRadius, strokePaint);
        } else {
            canvas.drawPath(strokePath, strokePaint);
        }
    }

    private void drawInactiveTrack(Canvas canvas, int left, int top, int right, int bottom) {
        float inactiveTrackRange = 1f - mVisualProgress;

        int height = bottom - top - strokeWidth;

        bottom = Math.round(inactiveTrackRange * height) + getPaddingTop() + strokeWidth/2;

        inactiveTrackRect.set(left + strokeWidth/2, top + strokeWidth/2, right - strokeWidth/2, bottom);
        canvas.drawRect(inactiveTrackRect, inactiveTrackPaint);
    }

    private void drawActiveTrack(Canvas canvas, int left, int top, int right, int bottom) {
        int height = bottom - top - strokeWidth;

        top = Math.round(height - (mVisualProgress * height)) + getPaddingTop() + strokeWidth/2;

        activeTrackRect.set(left + strokeWidth/2, top, right - strokeWidth/2, bottom - strokeWidth/2);
        canvas.drawRect(activeTrackRect, activeTrackPaint);
    }

    private void drawLabel(Canvas canvas, int left, int top) {
        if (!canDrawLabel()) return;

        textPaint.getTextBounds(labelText, 0, labelText.length(), labelBounds);
        labelBounds.right = (int) textPaint.measureText(labelText);

        int width = getWidth() - (left + getPaddingEnd());
        int height = getHeight() - (top + getPaddingBottom());

        float position = (iconLabelVisibility == TEXT) ? 0.5f : (iconLabelVisibility == TEXTICON) ? 0.25f : 0.75f;

        left += (width - labelBounds.width())/2;
        top += (int) (height * position) - labelBounds.height()/2;

        if (iconLabelBlending) {
            boolean isInBounds = activeTrackRect.contains(left, top + labelBounds.height()/2, left + labelBounds.width(), top + labelBounds.height());
            if (isInBounds) {
                if (textPaint.getColor() != getColorForState(inactiveTrackColor)) {
                    textPaint.setColor(getColorForState(inactiveTrackColor));
                }

            } else {
                if (textPaint.getColor() != getColorForState(labelColor)) {
                    textPaint.setColor(getColorForState(labelColor));
                }
            }

        } else {
            if (textPaint.getColor() != getColorForState(labelColor)) {
                textPaint.setColor(getColorForState(labelColor));
            }
        }

        canvas.save();
        canvas.rotate(-getRotation(), getPaddingStart() + width/2, height * position + getPaddingTop());
        canvas.drawText(labelText,0, labelText.length(), left, top + labelBounds.height(), textPaint);
        canvas.restore();
    }

    private void drawIcon(Canvas canvas, int left, int top) {
        if (iconDrawable == null || !canDrawIcon()) return;

        int iconWidth = iconDrawable.getBounds().width();
        int iconHeight = iconDrawable.getBounds().height();

        int width = getWidth() - (left + getPaddingEnd());
        int height = getHeight() - (top + getPaddingBottom());

        float position = (iconLabelVisibility == ICON) ? 0.5f : (iconLabelVisibility == TEXTICON) ? 0.75f : 0.25f;

        left += (width - iconWidth)/2;
        top += (int) (height * position) - iconHeight/2;

        drawableRect.set(left, top, left + iconWidth, top + iconHeight);

        if (iconLabelBlending) {
            boolean isInBounds = activeTrackRect.contains(left, top + iconHeight/2, left + iconWidth, top + iconHeight);
            if (isInBounds) {
                updateIconDrawableColor(inactiveTrackColor);
            } else {
                updateIconDrawableColor(iconColor);
            }
        } else {
            updateIconDrawableColor(iconColor);
        }

        canvas.save();
        canvas.rotate(-getRotation(), getPaddingStart() + width/2,
                getPaddingTop() + height * position);

        canvas.translate(left, top);
        iconDrawable.draw(canvas);
        canvas.restore();
    }

    @ColorInt
    private int getColorForState(@NonNull ColorStateList colorStateList) {
        return colorStateList.getColorForState(getDrawableState(), colorStateList.getDefaultColor());
    }

    private void refreshColorState() {
        drawableStateChanged();
        invalidate();
    }

    private boolean isValueValid(float value) {
        boolean isValid = false;
        if (value < minValue || value > maxValue) {
            Log.e(TAG, "Value must be in between min value and max value inclusive");
        } else {
            isValid = true;
        }

        return isValid;
    }

    private void validateMinValue() {
        if (minValue >= maxValue) {
            Log.e(TAG, "Minimum value must be less than max value.");
            throw new IllegalArgumentException("Minimum value must be less than max value.");
        }
    }

    private void validateMaxValue() {
        if (maxValue <= minValue) {
            Log.e(TAG, "Max value must be greater than min value.");
            throw new IllegalArgumentException("Max value must be greater than min value.");
        }
    }

    private boolean canDrawIcon() {
        return iconLabelVisibility == ICON || iconLabelVisibility == ICONTEXT || iconLabelVisibility == TEXTICON;
    }

    private boolean canDrawLabel() {
        return iconLabelVisibility == TEXT || iconLabelVisibility == TEXTICON || iconLabelVisibility == ICONTEXT;
    }

    /////////////// LottieDrawable Settings /////////////////
    /**
     * Sets the animation from a file in the raw directory. This is used for animating
     * {@link LottieDrawable} on progress changed.
     * For more information on how this works check {@link com.airbnb.lottie.LottieAnimationView#setAnimation(int)}
     */
    public void setIconAnimation(@RawRes int res) {
        if (!(iconDrawable instanceof LottieDrawable)) {
            iconDrawable = null;
            iconDrawable = new LottieDrawable();
        }

        LottieTask<LottieComposition> task = LottieCompositionFactory.fromRawRes(getContext(), res, null);
        setCompositionTask(task);
    }

    private void setComposition(LottieComposition composition) {
        if (!(iconDrawable instanceof LottieDrawable)) return;

        boolean isNewComposition = ((LottieDrawable) iconDrawable).setComposition(composition);

        if (isNewComposition) {
            updateIconAnimation();
            updateIconSize();
            updateIconDrawableColor(iconColor);
            invalidate();
        }
    }

    private void setCompositionTask(LottieTask<LottieComposition> compositionTask) {
        cancelLoaderTask();
        clearComposition();
        compositionTask
                .addListener(new LottieListener<LottieComposition>() {
                    @Override
                    public void onResult(LottieComposition result) {
                        setComposition(result);
                    }
                })
                .addFailureListener(new LottieListener<Throwable>() {
                    @Override
                    public void onResult(Throwable result) {
                        throw new IllegalStateException("Unable to parse composition", result);
                    }
                });
    }

    private void cancelLoaderTask() {
        if (lottieCompositionTask == null) return;
        lottieCompositionTask
                .removeListener(new LottieListener<LottieComposition>() {
                    @Override
                    public void onResult(LottieComposition result) {
                        setComposition(result);
                    }
                })
                .removeFailureListener(new LottieListener<Throwable>() {
                    @Override
                    public void onResult(Throwable result) {
                        throw new IllegalStateException("Unable to parse composition", result);
                    }
                });
    }

    private void clearComposition() {
        lottieCompositionTask = null;
        if (iconDrawable instanceof LottieDrawable) {
            ((LottieDrawable) iconDrawable).clearComposition();
        }
    }

    /**
     * This is a wrapper for {@link #setVisualProgress(float)} so it can be used
     * with ObjectAnimator.
     */
    private final Property<IOSlider, Float> PROPERTY_VISUAL = new Property<IOSlider, Float>(Float.class, VISUAL_PROGRESS) {
        @Override
        public void set(IOSlider object, Float value) {
            object.setVisualProgress(value);
        }

        @Override
        public Float get(IOSlider object) {
            return object.mVisualProgress;
        }
    };

    /**
     * Helper method to get color state list.
     */
    @NonNull
    private static ColorStateList getColorStateList(@NonNull Context context, @NonNull TypedArray attributes, @StyleableRes int index, @ColorRes int defaultValue) {
        boolean setDefaultValue = true;
        ColorStateList colorStateList = null;
        if (attributes.hasValue(index)) {
            int resourceId = attributes.getResourceId(index, 0);
            if (resourceId != 0) {
                colorStateList = AppCompatResources.getColorStateList(context, resourceId);
                if (colorStateList != null) {
                    setDefaultValue = false;
                }
            }
        }

        if (setDefaultValue) {
            colorStateList = AppCompatResources.getColorStateList(context, defaultValue);
        }

        return colorStateList;
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        IOSliderSaveState state = new IOSliderSaveState(superState);
        state.minValue = minValue;
        state.maxValue = maxValue;
        state.mProgress = mProgress;
        state.mVisualProgress = mVisualProgress;
        state.labelSize = labelSize;
        state.iconSize = iconSize;
        state.labelAsPercentage = labelAsPercentage;
        state.iconLabelBlend = iconLabelBlending;
        state.touchMode = touchMode;
        state.iconTextVisible = iconLabelVisibility;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        IOSliderSaveState savedState = (IOSliderSaveState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        minValue = savedState.minValue;
        maxValue = savedState.maxValue;
        mProgress = savedState.mProgress;
        mVisualProgress = savedState.mVisualProgress;
        iconSize = savedState.iconSize;
        labelSize = savedState.labelSize;
        labelText = savedState.labelText;
        labelAsPercentage = savedState.labelAsPercentage;
        iconLabelBlending = savedState.iconLabelBlend;
        touchMode = savedState.touchMode;
        iconLabelVisibility = savedState.iconTextVisible;

        updateIconSize();
        updateText();
        updateTextSize();
        invalidate();
        dispatchProgressChanged(false);
    }

    private static class IOSliderSaveState extends BaseSavedState {
        int minValue;
        int maxValue;
        float mProgress;
        float mVisualProgress;
        int iconSize;
        int labelSize;
        String labelText;
        boolean labelAsPercentage;
        boolean iconLabelBlend;
        @TouchMode int touchMode;
        @IconTextVisibility int iconTextVisible;

        IOSliderSaveState(Parcel source) {
            super(source);
            minValue = source.readInt();
            maxValue = source.readInt();
            mProgress = source.readFloat();
            mVisualProgress = source.readFloat();
            iconSize = source.readInt();
            labelSize = source.readInt();
            labelText = source.readString();
            labelAsPercentage = source.readBoolean();
            iconLabelBlend = source.readBoolean();
            touchMode = source.readInt();
            iconTextVisible = source.readInt();
        }

        IOSliderSaveState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(minValue);
            out.writeInt(maxValue);
            out.writeFloat(mProgress);
            out.writeFloat(mVisualProgress);
            out.writeInt(iconSize);
            out.writeInt(labelSize);
            out.writeString(labelText);
            out.writeBoolean(labelAsPercentage);
            out.writeBoolean(iconLabelBlend);
            out.writeInt(touchMode);
            out.writeInt(iconTextVisible);
        }

        public static final Parcelable.Creator<IOSliderSaveState> CREATOR
                = new Parcelable.Creator<IOSliderSaveState>() {
            public IOSliderSaveState createFromParcel(Parcel in) {
                return new IOSliderSaveState(in);
            }

            public IOSliderSaveState[] newArray(int size) {
                return new IOSliderSaveState[size];
            }
        };
    }
}
