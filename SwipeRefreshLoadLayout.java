package com.lzp.tests;

import android.content.Context;
import android.support.v4.widget.ListViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ListView;

/**
 * 下拉刷新，上拉加载控件
 */
public class SwipeRefreshLoadLayout extends ViewGroup {
    private View mHeaderView;
    private View mFooterView;

    private View mTargetView;
    private int mHeaderOffsetTop;

    private int mTouchSlop;
    private float mInitialDownY, mLastMotionY;
    private boolean mIsBeingDragged = false;
    private boolean mRefreshing = false;

    private static final float DRAG_RATE = .5f;

    private int mState = STATE_INIT;
    private static final int STATE_INIT = 0;
    private static final int STATE_SCROLL_UP = 1;
    private static final int STATE_SCROLL_DOWN = 2;
    private static final int STATE_SCROLL_UP_HEADER_SHOW = 3;
    private static final int STATE_SCROLL_DOWN_FOOTER_SHOW = 4;
    private static final int STATE_SCROLL_REFRESHING_HEADER = 5;
    private static final int STATE_SCROLL_REFRESHING_FOOTER = 6;

    private SwipRefreshLoadListener mListener;

    public interface SwipRefreshLoadListener {
        /**
         * scroll up
         */
        int ORIENTATION_UP = 1;

        /**
         * scroll down
         */
        int ORIENTATION_DOWN = 2;

        /**
         * header或footer完全显示出来时回调
         *
         * @param orientation {@link ORIENTATION_UP} {@link ORIENTATION_DOWN}
         */
        public void onRefresh(int orientation);

        /**
         * header或footer处于显示状态，header或footer显示一半以上时回调。
         * 可能会多次回调
         *
         * @param orientation {@link ORIENTATION_UP} {@link ORIENTATION_DOWN}
         */
        public void onShow(int orientation);

        /**
         * header或footer处于隐藏状态，header或footer显示一半以上时回调
         * 可能会多次回调
         *
         * @param orientation {@link ORIENTATION_UP} {@link ORIENTATION_DOWN}
         */
        public void onHide(int orientation);
    }

    public SwipeRefreshLoadLayout(Context context) {
        this(context, null);
    }

    public SwipeRefreshLoadLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeRefreshLoadLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    public void setRefreshLoadListener(SwipRefreshLoadListener listener) {
        mListener = listener;
    }

    public void setHeader(View headerView) {
        if (mHeaderView != null) {
            removeView(mHeaderView);
        }
        mHeaderView = headerView;
        addView(mHeaderView);
    }

    public void setFooter(View footerView) {
        if (mFooterView != null) {
            removeView(mFooterView);
        }
        mFooterView = footerView;
        addView(mFooterView);
    }

    public void finish() {
        mRefreshing = false;
        springBackFooter();
        springBackHeader();
        mState = STATE_INIT;
    }

    public boolean isRefreshing() {
        return mRefreshing;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (canChildScrollUp() && canChildScrollDown()) {
            return false;
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsBeingDragged = false;
                mInitialDownY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float y = ev.getY();
                ensureDragging(y);
                break;
            case MotionEvent.ACTION_UP:
                mIsBeingDragged = false;
                break;
        }
        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (canChildScrollUp() && canChildScrollDown()) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsBeingDragged = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float y = event.getY();
                ensureDragging(y);
                if (mIsBeingDragged) {
                    float dragDistance = (y - mLastMotionY) * DRAG_RATE;
                    if (dragDistance > 0) {
                        moveHeader(dragDistance);
                    } else {
                        moveFooter(dragDistance);
                    }
                    notifyListener();
                }
                mLastMotionY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    mIsBeingDragged = false;
                    if (mState == STATE_SCROLL_DOWN && mHeaderView.getTop() > -mHeaderView.getHeight() / 2) {//移动的距离超过一半
                        moveHeader(0 - mHeaderView.getTop());
                        mState = STATE_SCROLL_REFRESHING_HEADER;
                        mRefreshing = true;
                    } else if (mState == STATE_SCROLL_UP && mFooterView.getBottom() < (getBottom() + mFooterView.getHeight() / 2)) {//移动的距离超过一半
                        moveFooter(getBottom() - mFooterView.getBottom());
                        mState = STATE_SCROLL_REFRESHING_FOOTER;
                        mRefreshing = true;
                    } else if (mState == STATE_SCROLL_UP_HEADER_SHOW) {
                        springBackHeader();
                        mState = STATE_INIT;
                    } else if (mState == STATE_SCROLL_DOWN_FOOTER_SHOW) {
                        springBackFooter();
                        mState = STATE_INIT;
                    }
                    notifyListener();
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private void moveHeader(float dragDistance) {
        if (mHeaderView.getTop() < 0) {
            if (mState == STATE_SCROLL_DOWN && mHeaderView.getTop() + dragDistance > 0) {
                dragDistance = 0 - mHeaderView.getTop();
            } else if (mState == STATE_SCROLL_DOWN_FOOTER_SHOW && mHeaderView.getBottom() + dragDistance > getTop()) {
                dragDistance = getTop() - mHeaderView.getBottom();
            }

            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                child.offsetTopAndBottom((int) dragDistance);
            }
        }
    }

    private void moveFooter(float dragDistance) {

        if (mFooterView.getBottom() > getBottom()) {
            if (mState == STATE_SCROLL_UP && mFooterView.getBottom() + dragDistance < getBottom()) {
                dragDistance = getBottom() - mFooterView.getBottom();
            } else if (mState == STATE_SCROLL_UP_HEADER_SHOW && mFooterView.getTop() + dragDistance < getBottom()) {
                dragDistance = getBottom() - mFooterView.getTop();
            }

            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                child.offsetTopAndBottom((int) dragDistance);
            }
        }
    }

    private void notifyListener() {
        if (mHeaderView.getBottom() >= mHeaderView.getHeight() / 2 && mState == STATE_SCROLL_DOWN) {
            mListener.onShow(SwipRefreshLoadListener.ORIENTATION_DOWN);
        } else if (mHeaderView.getBottom() >= mHeaderView.getHeight() / 2 && mState == STATE_SCROLL_UP_HEADER_SHOW) {
            mListener.onHide(SwipRefreshLoadListener.ORIENTATION_UP);
        } else if ((mHeaderView.getTop() == getTop()) && (mState == STATE_SCROLL_REFRESHING_HEADER)) {
            mListener.onRefresh(SwipRefreshLoadListener.ORIENTATION_DOWN);
        } else if (mFooterView.getBottom() <= getBottom() + mFooterView.getHeight() / 2 && mState == STATE_SCROLL_UP) {
            mListener.onShow(SwipRefreshLoadListener.ORIENTATION_UP);
        } else if (mFooterView.getBottom() <= getBottom() + mFooterView.getHeight() / 2 && mState == STATE_SCROLL_DOWN_FOOTER_SHOW) {
            mListener.onHide(SwipRefreshLoadListener.ORIENTATION_DOWN);
        } else if ((mFooterView.getBottom() == getBottom()) && (mState == STATE_SCROLL_REFRESHING_FOOTER)) {
            mListener.onRefresh(SwipRefreshLoadListener.ORIENTATION_UP);
        }
    }

    private void springBackHeader() {
        int currentTop = mHeaderView.getTop();
        int targetTop = -mHeaderView.getHeight();
        int distance = targetTop - currentTop;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            child.offsetTopAndBottom(distance);
        }
    }

    private void springBackFooter() {
        int currentBottom = mFooterView.getBottom();
        int targetBottom = getBottom() + mFooterView.getHeight();
        int distance = targetBottom - currentBottom;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            child.offsetTopAndBottom(distance);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mTargetView == null) {
            ensureTarget();
        }
        if (mTargetView == null) {
            return;
        }

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        int childLeft = getPaddingLeft();
        int childTop = getPaddingTop();
        int childWidth = width - getPaddingLeft() - getPaddingRight();
        int childHeight = height - getPaddingTop() - getPaddingBottom();

        mTargetView.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

        int headerWidth = mHeaderView.getMeasuredWidth();
        int headerHeight = mHeaderView.getMeasuredHeight();
        mHeaderView.layout(width / 2 - headerWidth / 2, mHeaderOffsetTop, width / 2 + headerWidth / 2, mHeaderOffsetTop + headerHeight);

        int footerWidth = mFooterView.getMeasuredWidth();
        int footerHeight = mFooterView.getMeasuredHeight();
        mFooterView.layout(width / 2 - footerWidth / 2, childTop + childHeight, width / 2 + footerWidth / 2, childTop + childHeight + footerHeight);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTargetView == null) {
            ensureTarget();
        }
        if (mTargetView == null) {
            return;
        }
        mTargetView.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));

        LayoutParams lp = mHeaderView.getLayoutParams();
        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(widthMeasureSpec - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY);
        int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                getPaddingTop() + getPaddingBottom(), lp.height);

        mHeaderView.measure(childWidthMeasureSpec, childHeightMeasureSpec);

        lp = mFooterView.getLayoutParams();
        childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(widthMeasureSpec - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY);
        childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                getPaddingTop() + getPaddingBottom(), lp.height);

        mFooterView.measure(childWidthMeasureSpec, childHeightMeasureSpec);

        mHeaderOffsetTop = -mHeaderView.getMeasuredHeight();
    }

    private void ensureDragging(float y) {
        float yDiff = y - mInitialDownY;
        if (yDiff > 0) {//向下滑动
            //child可以向下滑动并且footerView没有显示出来，不拦截事件，将事件传递到child处理
            if (canChildScrollUp() && !(mFooterView.getBottom() < getBottom() + mFooterView.getHeight())) {
                mIsBeingDragged = false;
                return;
            }
            mState = STATE_SCROLL_DOWN;
            if (mFooterView.getBottom() < getBottom() + mFooterView.getHeight()) {//footer显示中
                mState = STATE_SCROLL_DOWN_FOOTER_SHOW;
            }
        } else {//向上滑动
            //child可以向上滑动并且headerView没有显示出来，不拦截事件，将事件传递到child处理
            if (canChildScrollDown() && !(mHeaderView.getTop() > -mHeaderView.getHeight())) {
                mIsBeingDragged = false;
                return;
            }
            mState = STATE_SCROLL_UP;
            if (mHeaderView.getTop() > -mHeaderView.getHeight()) {
                mState = STATE_SCROLL_UP_HEADER_SHOW;
            }
        }

        float yDiffAbs = Math.abs(y - mInitialDownY);
        if (yDiffAbs > mTouchSlop && !mIsBeingDragged) {
            mLastMotionY = mInitialDownY + mTouchSlop;
            mIsBeingDragged = true;
        }
    }

    private void ensureTarget() {
        if (mHeaderView == null && mFooterView == null)
            return;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (!(mHeaderView == child && mFooterView == child)) {
                mTargetView = child;
                return;
            }
        }
    }

    public boolean canChildScrollUp() {
        if (mTargetView instanceof ListView) {
            return ListViewCompat.canScrollList((ListView) mTargetView, -1);
        }
        return mTargetView.canScrollVertically(-1);
    }

    public boolean canChildScrollDown() {
        if (mTargetView instanceof ListView) {
            return ListViewCompat.canScrollList((ListView) mTargetView, 1);
        }
        return mTargetView.canScrollVertically(1);
    }
}
