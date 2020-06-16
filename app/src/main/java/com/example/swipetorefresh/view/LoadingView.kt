package com.example.swipetorefresh.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.Transformation
import android.widget.AbsListView
import android.widget.ImageView
import androidx.annotation.NonNull
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewCompat
import kotlin.math.roundToInt

class LoadingView constructor(context: Context, attrs: AttributeSet? = null) :
    ViewGroup(context, attrs) {

    private var mTarget: View? = null
    private val mRefreshView: ImageView
    private val mDecelerateInterpolator: Interpolator
    private val mTouchSlop: Int
    var totalDragDistance: Int = 0
    private var mCurrentDragPercent: Float = 0.toFloat()
    private var mCurrentOffsetTop: Int = 0
    private var mRefreshing: Boolean = false
    private var mActivePointerId: Int = 0
    private var mIsBeingDragged: Boolean = false
    private var mInitialMotionY: Float = 0.toFloat()
    private var mFrom: Int = 0
    private var mFromDragPercent: Float = 0.toFloat()
    private var mNotify: Boolean = false
    private var mListener: OnRefreshListener? = null
    private var mTargetPaddingTop: Int = 0
    private var mTargetPaddingBottom: Int = 0
    private var mTargetPaddingRight: Int = 0
    private var mTargetPaddingLeft: Int = 0

    private val mAnimateToCorrectPosition = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val targetTop: Int
            val endTarget = totalDragDistance
            targetTop = mFrom + ((endTarget - mFrom) * interpolatedTime).toInt()
            val offset = targetTop - (mTarget?.top ?: 0)

            mCurrentDragPercent = mFromDragPercent - (mFromDragPercent - 1.0f) * interpolatedTime
            setTargetOffsetTop(offset, false /* requires update */)
        }
    }
    private val mAnimateToStartPosition = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            moveToStart(interpolatedTime)
        }
    }
    private val mToStartListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {}

        override fun onAnimationRepeat(animation: Animation) {}

        override fun onAnimationEnd(animation: Animation) {
            mCurrentOffsetTop = mTarget?.top ?: 0
        }
    }

    init {
        mDecelerateInterpolator = DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR)
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
        totalDragDistance = convertDpToPixel(context,
            DRAG_MAX_DISTANCE
        )
        mRefreshView = ImageView(context)
        addView(mRefreshView)
        setWillNotDraw(false)
        ViewCompat.setChildrenDrawingOrderEnabled(this, true)
    }

    override fun onMeasure(width: Int, height: Int) {
        var widthMeasureSpec = width
        var heightMeasureSpec = height
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        ensureTarget()
        if (mTarget == null)
            return

        widthMeasureSpec = MeasureSpec.makeMeasureSpec(
            measuredWidth - paddingRight - paddingLeft,
            View.MeasureSpec.EXACTLY
        )
        heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
            measuredHeight - paddingTop - paddingBottom,
            View.MeasureSpec.EXACTLY
        )
        mTarget?.measure(widthMeasureSpec, heightMeasureSpec)
        mRefreshView.measure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun ensureTarget() {
        if (mTarget != null)
            return
        if (childCount > 0) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child !== mRefreshView) {
                    mTarget = child
                    mTargetPaddingBottom = mTarget?.paddingBottom ?: 0
                    mTargetPaddingLeft = mTarget?.paddingLeft ?: 0
                    mTargetPaddingRight = mTarget?.paddingRight ?: 0
                    mTargetPaddingTop = mTarget?.paddingTop ?: 0
                }
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || canChildScrollUp() || mRefreshing) {
            return false
        }

        when (MotionEventCompat.getActionMasked(ev)) {
            MotionEvent.ACTION_DOWN -> {
                setTargetOffsetTop(0, true)
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0)
                mIsBeingDragged = false
                val initialMotionY = getMotionEventY(ev, mActivePointerId)
                if (initialMotionY == -1f) {
                    return false
                }
                mInitialMotionY = initialMotionY
            }
            MotionEvent.ACTION_MOVE -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }
                val y = getMotionEventY(ev, mActivePointerId)
                if (y == -1f) {
                    return false
                }
                val yDiff = y - mInitialMotionY
                if (yDiff > mTouchSlop && !mIsBeingDragged) {
                    mIsBeingDragged = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mIsBeingDragged = false
                mActivePointerId =
                    INVALID_POINTER
            }
            MotionEventCompat.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }

        return mIsBeingDragged
    }

    override fun onTouchEvent(@NonNull ev: MotionEvent): Boolean {

        if (!mIsBeingDragged) {
            return super.onTouchEvent(ev)
        }

        when (MotionEventCompat.getActionMasked(ev)) {
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId)
                if (pointerIndex < 0) {
                    return false
                }

                val y = MotionEventCompat.getY(ev, pointerIndex)
                val yDiff = y - mInitialMotionY
                val scrollTop = yDiff * DRAG_RATE
                mCurrentDragPercent = scrollTop / totalDragDistance
                if (mCurrentDragPercent < 0) {
                    return false
                }
                val boundedDragPercent = Math.min(1f, Math.abs(mCurrentDragPercent))
                val extraOS = Math.abs(scrollTop) - totalDragDistance
                val slingshotDist = totalDragDistance.toFloat()
                val tensionSlingshotPercent = Math.max(
                    0f,
                    Math.min(extraOS, slingshotDist * 2) / slingshotDist
                )
                val tensionPercent = (tensionSlingshotPercent / 4 - Math.pow(
                    (tensionSlingshotPercent / 4).toDouble(), 2.0
                )).toFloat() * 2f
                val extraMove = slingshotDist * tensionPercent / 2
                val targetY = (slingshotDist * boundedDragPercent + extraMove).toInt()
                setTargetOffsetTop(targetY - mCurrentOffsetTop, true)
            }
            MotionEventCompat.ACTION_POINTER_DOWN -> {
                val index = MotionEventCompat.getActionIndex(ev)
                mActivePointerId = MotionEventCompat.getPointerId(ev, index)
            }
            MotionEventCompat.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }

                val pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId)
                val y = MotionEventCompat.getY(ev, pointerIndex)
                val overScrollTop = (y - mInitialMotionY) * DRAG_RATE
                mIsBeingDragged = false
                if (overScrollTop > totalDragDistance) {
                    setRefreshing(true, true)
                    animateOffsetToStartPosition()
                    mListener?.isLoading()
                } else {
                    mRefreshing = false
                    animateOffsetToStartPosition()
                }
                mActivePointerId =
                    INVALID_POINTER
                return false
            }
        }

        return true
    }

    private fun animateOffsetToStartPosition() {
        mFrom = mCurrentOffsetTop
        mFromDragPercent = mCurrentDragPercent
        val animationDuration =
            Math.abs((MAX_OFFSET_ANIMATION_DURATION * mFromDragPercent).toLong())

        mAnimateToStartPosition.reset()
        mAnimateToStartPosition.duration = animationDuration
        mAnimateToStartPosition.interpolator = mDecelerateInterpolator
        mAnimateToStartPosition.setAnimationListener(mToStartListener)
        mRefreshView.clearAnimation()
        mRefreshView.startAnimation(mAnimateToStartPosition)
    }

    private fun animateOffsetToCorrectPosition() {
        mFrom = mCurrentOffsetTop
        mFromDragPercent = mCurrentDragPercent

        mAnimateToCorrectPosition.reset()
        mAnimateToCorrectPosition.duration = MAX_OFFSET_ANIMATION_DURATION.toLong()
        mAnimateToCorrectPosition.interpolator = mDecelerateInterpolator
        mRefreshView.clearAnimation()
        mRefreshView.startAnimation(mAnimateToCorrectPosition)

        if (mRefreshing) {
            if (mNotify) {
                if (mListener != null) {
                    mListener?.onRefresh()
                }
            }
        } else {
            animateOffsetToStartPosition()
        }
        mCurrentOffsetTop = mTarget?.top ?: 0
        mTarget?.setPadding(
            mTargetPaddingLeft,
            mTargetPaddingTop,
            mTargetPaddingRight,
            totalDragDistance
        )
    }

    private fun moveToStart(interpolatedTime: Float) {
        val targetTop = mFrom - (mFrom * interpolatedTime).toInt()
        val targetPercent = mFromDragPercent * (1.0f - interpolatedTime)
        val offset = targetTop - (mTarget?.top ?: 0)

        mCurrentDragPercent = targetPercent
        mTarget?.setPadding(
            mTargetPaddingLeft,
            mTargetPaddingTop,
            mTargetPaddingRight,
            mTargetPaddingBottom + targetTop
        )
        setTargetOffsetTop(offset, false)
    }

    fun setRefreshing(refreshing: Boolean) {
        if (mRefreshing != refreshing) {
            setRefreshing(refreshing, true /* notify */)
        }
    }

    private fun setRefreshing(refreshing: Boolean, notify: Boolean) {
        if (mRefreshing != refreshing) {
            mNotify = notify
            ensureTarget()
            mRefreshing = refreshing
            if (mRefreshing) {
                mListener?.isLoading()
                //animateOffsetToCorrectPosition()
            } else {
                mListener?.onRefresh()
                //animateOffsetToStartPosition()
            }
        }
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = MotionEventCompat.getActionIndex(ev)
        val pointerId = MotionEventCompat.getPointerId(ev, pointerIndex)
        if (pointerId == mActivePointerId) {
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex)
        }
    }

    private fun getMotionEventY(ev: MotionEvent, activePointerId: Int): Float {
        val index = MotionEventCompat.findPointerIndex(ev, activePointerId)
        return if (index < 0) {
            -1f
        } else MotionEventCompat.getY(ev, index)
    }

    private fun setTargetOffsetTop(offset: Int, requiresUpdate: Boolean) {
        mTarget?.offsetTopAndBottom(offset)
        mCurrentOffsetTop = mTarget?.top ?: 0
        if (requiresUpdate && android.os.Build.VERSION.SDK_INT < 11) {
            invalidate()
        }
    }

    private fun canChildScrollUp(): Boolean {
        return when {
            android.os.Build.VERSION.SDK_INT < 14 -> if (mTarget is AbsListView) {
                val absListView = mTarget as AbsListView
                absListView.childCount > 0 && (absListView.firstVisiblePosition > 0 || absListView.getChildAt(
                    0
                )
                    .top < absListView.paddingTop)
            } else {
                mTarget!!.scrollY > 0
            }
            else -> ViewCompat.canScrollVertically(mTarget, -1)
        }
    }


    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        ensureTarget()
        if (mTarget == null)
            return

        val height = measuredHeight
        val width = measuredWidth
        val left = paddingLeft
        val top = paddingTop
        val right = paddingRight
        val bottom = paddingBottom

        mTarget?.layout(
            left,
            top + mCurrentOffsetTop,
            left + width - right,
            top + height - bottom + mCurrentOffsetTop
        )
        mRefreshView.layout(left, top, left + width - right, top + height - bottom)
    }

    fun setOnRefreshListener(listener: OnRefreshListener) {
        mListener = listener
    }

    private fun convertDpToPixel(context: Context, dp: Int): Int {
        val r = context.resources
        val px =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), r.displayMetrics)
        return px.roundToInt()
    }

    interface OnRefreshListener {
        fun onRefresh()
        fun isLoading()
    }

    companion object {
        private val MAX_OFFSET_ANIMATION_DURATION = 500
        private val DRAG_MAX_DISTANCE = 120
        private val DRAG_RATE = .5f
        private val DECELERATE_INTERPOLATION_FACTOR = 1f
        private val INVALID_POINTER = -1
    }

}
