package de.blinkt.openvpn.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;

import de.blinkt.openvpn.core.ICSOpenVPNApplication;
import de.blinkt.openvpn.util.CommonTools;

public class SwipeMenuView extends ViewGroup {
    private static final String TAG = "zxt";
    private boolean isSwipeEnable = false;//右滑删除功能的开关,默认开

    private int mScaleTouchSlop;//为了处理单击事件的冲突
    private int mMaxVelocity;//计算滑动速度用
    private int mPointerId;//多点触摸只算第一根手指的速度
    private int mHeight;//自己的高度
    private int mScreenW;//屏幕宽宽
    /**
     * 右侧菜单宽度总和(最大滑动距离)
     */
    private int mRightMenuWidths;
    /**
     * 滑动判定临界值（右侧菜单宽度的40%） 手指抬起时，超过了展开，没超过收起menu
     */
    private int mLimit;
    //private Scroller mScroller;//以前item的滑动动画靠它做，现在用属性动画做
    //上一次的xy
    private PointF mLastP = new PointF();

    //存储的是当前正在展开的View
    private static SwipeMenuView mViewCache;

    //防止多只手指一起滑我的flag 在每次down里判断， touch事件结束清空
    private static boolean isTouching;

    private VelocityTracker mVelocityTracker;//滑动速度变量


    private boolean isIos = true;//IOS类型的开关

    private boolean iosInterceptFlag = false;//IOS类型下，是否拦截事件的flag

    //20160929add 左滑右滑的开关
    private boolean isLeftSwipe = false;

    public SwipeMenuView(Context context) {
        this(context, null);
    }

    public SwipeMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public boolean isSwipeEnable() {
        return isSwipeEnable;
    }

    /**
     * 设置侧滑功能开关
     *
     * @param swipeEnable
     */
    public void setSwipeEnable(boolean swipeEnable) {
        isSwipeEnable = swipeEnable;
    }


    public boolean isIos() {
        return isIos;
    }

    /**
     * 设置是否开启IOS阻塞式交互
     * @param ios
     */
    public SwipeMenuView setIos(boolean ios) {
        isIos = ios;
        return this;
    }

    public boolean isLeftSwipe() {
        return isLeftSwipe;
    }

    /**
     * 设置是否开启左滑出菜单，设置false 为右滑出菜单
     * @param leftSwipe
     * @return
     */
    public SwipeMenuView setLeftSwipe(boolean leftSwipe) {
        isLeftSwipe = leftSwipe;
        return this;
    }


    private void init(Context context) {
        mScaleTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mScreenW = getResources().getDisplayMetrics().widthPixels;
        mMaxVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
        //初始化滑动帮助类对象
        //mScroller = new Scroller(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.d(TAG, "onMeasure() called with: " + "widthMeasureSpec = [" + widthMeasureSpec + "], heightMeasureSpec = [" + heightMeasureSpec + "]");
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mRightMenuWidths = 0;//由于ViewHolder的复用机制，每次这里要手动恢复初始值
        int childCount = getChildCount();

        //add by 2016 08 11 为了子View的高，可以matchParent(参考的FrameLayout 和LinearLayout的Horizontal)
        final boolean measureMatchParentChildren = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;
        boolean isNeedMeasureChildHeight = false;

        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            if (childView.getVisibility() != GONE) {
                //measureChild(childView, widthMeasureSpec, heightMeasureSpec);
                measureChildWithMargins(childView, widthMeasureSpec, 0, heightMeasureSpec, 0);
                final MarginLayoutParams lp = (MarginLayoutParams) childView.getLayoutParams();
                mHeight = Math.max(mHeight, childView.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
                if (measureMatchParentChildren && lp.height == LayoutParams.MATCH_PARENT) {
                    isNeedMeasureChildHeight = true;
                }
                if (i > 0) {//第一个布局是Left item，从第二个开始才是RightMenu
                    mRightMenuWidths += childView.getMeasuredWidth();
                }
            }
        }
        setMeasuredDimension(mScreenW, mHeight);//宽度取屏幕宽度
        mLimit = mRightMenuWidths * 4 / 10;//滑动判断的临界值
        //Log.d(TAG, "onMeasure() called with: " + "mRightMenuWidths = [" + mRightMenuWidths);
        if (isNeedMeasureChildHeight) {//如果子View的height有MatchParent属性的，设置子View高度
            forceUniformHeight(childCount, widthMeasureSpec);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    /**
     * 给MatchParent的子View设置高度
     *
     * @param count
     * @param widthMeasureSpec
     * @see android.widget.LinearLayout# 同名方法
     */
    private void forceUniformHeight(int count, int widthMeasureSpec) {
        // Pretend that the linear layout has an exact size. This is the measured height of
        // ourselves. The measured height should be the max height of the children, changed
        // to accommodate the heightMeasureSpec from the parent
        int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(),
                MeasureSpec.EXACTLY);//以父布局高度构建一个Exactly的测量参数
        for (int i = 0; i < count; ++i) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                if (lp.height == LayoutParams.MATCH_PARENT) {
                    // Temporarily force children to reuse their old measured width
                    // FIXME: this may not be right for something like wrapping text?
                    int oldWidth = lp.width;//measureChildWithMargins 这个函数会用到宽，所以要保存一下
                    lp.width = child.getMeasuredWidth();
                    // Remeasure with new dimensions
                    measureChildWithMargins(child, widthMeasureSpec, 0, uniformMeasureSpec, 0);
                    lp.width = oldWidth;
                }
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        //LogUtils.d(TAG, "onLayout() called with: " + "changed = [" + changed + "], l = [" + l + "], t = [" + t + "], r = [" + r + "], b = [" + b + "]");
        int childCount = getChildCount();
        int left = l;
        int right = 0;
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            if (childView.getVisibility() != GONE) {
                if (i == 0) {//第一个子View是内容 宽度设置为全屏
                    childView.layout(left, getPaddingTop(), left + mScreenW, getPaddingTop() + childView.getMeasuredHeight());
                    left = left + mScreenW;
                } else {
                    if (isLeftSwipe) {
                        childView.layout(left, getPaddingTop(), left + childView.getMeasuredWidth(), getPaddingTop() + childView.getMeasuredHeight());
                        left = left + childView.getMeasuredWidth();
                    } else {
                        childView.layout(right - childView.getMeasuredWidth(), getPaddingTop(), right, getPaddingTop() + childView.getMeasuredHeight());
                        right = right - childView.getMeasuredWidth();
                    }

                }
            }
        }
        //Log.d(TAG, "onLayout() called with: " + "maxScrollGap = [" + maxScrollGap + "], l = [" + l + "], t = [" + t + "], r = [" + r + "], b = [" + b + "]");
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        //LogUtils.d(TAG, "dispatchTouchEvent() called with: " + "ev = [" + ev + "]");
        if (isSwipeEnable) {
            acquireVelocityTracker(ev);
            final VelocityTracker verTracker = mVelocityTracker;
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    iosInterceptFlag = false;//add by 2016 09 11 ，每次DOWN时，默认是不拦截的
                    if (isTouching) {//如果有别的指头摸过了，那么就return false。这样后续的move..等事件也不会再来找这个View了。
                        return false;
                    } else {
                        isTouching = true;//第一个摸的指头，赶紧改变标志，宣誓主权。
                    }
                    mLastP.set(ev.getRawX(), ev.getRawY());

                    //如果down，view和cacheview不一样，则立马让它还原。且把它置为null
                    if (mViewCache != null) {
                        if (mViewCache != this) {
                            mViewCache.smoothClose();
                            mViewCache = null;
                            iosInterceptFlag = isIos;//add by 2016 09 11 ，IOS模式开启的话，且当前有侧滑菜单的View，且不是自己的，就该拦截事件咯。
                        }
                        //只要有一个侧滑菜单处于打开状态， 就不给外层布局上下滑动了
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    //求第一个触点的id， 此时可能有多个触点，但至少一个，计算滑动速率用
                    mPointerId = ev.getPointerId(0);
                    break;
                case MotionEvent.ACTION_MOVE:
                    //add by 2016 09 11 ，IOS模式开启的话，且当前有侧滑菜单的View，且不是自己的，就该拦截事件咯。滑动也不该出现
                    if (iosInterceptFlag) {
                        break;
                    }
                    float gap = mLastP.x - ev.getRawX();
                    //为了在水平滑动中禁止父类ListView等再竖直滑动
                    if (Math.abs(gap) > 10 || Math.abs(getScrollX()) > 10) {//2016 09 29 修改此处，使屏蔽父布局滑动更加灵敏，
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    //如果scroller还没有滑动结束 停止滑动动画
/*                    if (!mScroller.isFinished()) {
                        mScroller.abortAnimation();
                    }*/
                    scrollBy((int) (gap), 0);//滑动使用scrollBy
                    //越界修正
                    if (isLeftSwipe) {//左滑
                        if (getScrollX() < 0) {
                            scrollTo(0, 0);
                        }
                        if (getScrollX() > mRightMenuWidths) {
                            scrollTo(mRightMenuWidths, 0);
                        }
                    } else {//右滑
                        if (getScrollX() < -mRightMenuWidths) {
                            scrollTo(-mRightMenuWidths, 0);
                        }
                        if (getScrollX() > 0) {
                            scrollTo(0, 0);
                        }
                    }

                    mLastP.set(ev.getRawX(), ev.getRawY());
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    //add by 2016 09 11 ，IOS模式开启的话，且当前有侧滑菜单的View，且不是自己的，就该拦截事件咯。滑动也不该出现
                    if (!iosInterceptFlag) {
                        //求伪瞬时速度
                        verTracker.computeCurrentVelocity(1000, mMaxVelocity);
                        final float velocityX = verTracker.getXVelocity(mPointerId);
                        if (Math.abs(velocityX) > 1000) {//滑动速度超过阈值
                            if (velocityX < -1000) {
                                if (isLeftSwipe) {//左滑
                                    //平滑展开Menu
                                    smoothExpand();
                                    //展开就加入ViewCache：
                                    mViewCache = this;
                                } else {
                                    //平滑关闭Menu
                                    smoothClose();
                                }
                            } else {
                                if (isLeftSwipe) {//左滑
                                    // 平滑关闭Menu
                                    smoothClose();
                                } else {

                                    //平滑展开Menu
                                    smoothExpand();
                                    //展开就加入ViewCache：
                                    mViewCache = this;
                                }
                            }
                        } else {





//                            if (Math.abs(getScrollX()) > mLimit) {//否则就判断滑动距离
//                                //平滑展开Menu
//                                smoothExpand();
//                                //展开就加入ViewCache：
//                                mViewCache = this;
//                            } else {
                                // 平滑关闭Menu
                                float width=    (CommonTools.getScreenWidth(ICSOpenVPNApplication.getContext())- CommonTools.dip2px(ICSOpenVPNApplication.getContext(),60));
                                if(mLastP.x<width) {
                                    smoothClose();
                                }
//                            }
                        }
                    }
                    //释放
                    releaseVelocityTracker();
                    //LogUtils.i(TAG, "onTouch A ACTION_UP ACTION_CANCEL:velocityY:" + velocityX);
                    isTouching = false;//没有手指在摸我了
                    break;
                default:
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_UP:
                //为了在侧滑时，屏蔽子View的点击事件
                if (isLeftSwipe) {
                    float width=    (CommonTools.getScreenWidth(ICSOpenVPNApplication.getContext())- CommonTools.dip2px(ICSOpenVPNApplication.getContext(),60));
                    if(mLastP.x>width){
                        return   false;
                    }

                    if (getScrollX() > mScaleTouchSlop) {
                        //add by 2016 09 10 解决一个智障问题~ 居然不给点击侧滑菜单 我跪着谢罪
                        //这里判断落点在内容区域屏蔽点击，内容区域外，允许传递事件继续向下的的。。。
                        if (ev.getX() < getWidth() - getScrollX()) {
                            return true;//true表示拦截
                        }
                    }
                } else {
                    if (-getScrollX() > mScaleTouchSlop) {
                        if (ev.getX() > -getScrollX()) {//点击范围在菜单外 屏蔽
                            return true;
                        }
                    }
                }

                break;
        }
        //模仿IOS 点击其他区域关闭：
        if (iosInterceptFlag) {
            //IOS模式开启，且当前有菜单的View，且不是自己的 拦截点击事件给子View
            return true;
        }

        return super.onInterceptTouchEvent(ev);
    }

    /**
     * 平滑展开
     */
    private ValueAnimator mExpandAnim, mCloseAnim;

    public void smoothExpand() {
        /*mScroller.startScroll(getScrollX(), 0, mRightMenuWidths - getScrollX(), 0);
        invalidate();*/
        mExpandAnim = ValueAnimator.ofInt(getScrollX(), isLeftSwipe ? mRightMenuWidths : -mRightMenuWidths);
        mExpandAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scrollTo((Integer) animation.getAnimatedValue(), 0);
            }
        });
        mExpandAnim.setInterpolator(new OvershootInterpolator());
        mExpandAnim.setDuration(300).start();
    }

    private boolean isExpand;
    public  boolean getExpand(){
        return isExpand;
    }
    public  void setExpand(boolean isExpand){
        this.isExpand=isExpand;
    }
    /**
     * 平滑关闭
     */
    public void smoothClose() {
/*        mScroller.startScroll(getScrollX(), 0, -getScrollX(), 0);
        invalidate();*/
        int s=getScrollX();
        if(s>150){
            s=0;
        }
        mCloseAnim = ValueAnimator.ofInt(s, 0);
        mCloseAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scrollTo((Integer) animation.getAnimatedValue(), 0);
            }
        });
        mCloseAnim.setInterpolator(new AnticipateInterpolator());
        mCloseAnim.setDuration(100).start();
        if(flagScroll){
            isSwipeEnable=false;
        }

        //LogUtils.d(TAG, "smoothClose() called with:getScrollX() " + getScrollX());
    }
    public boolean flagScroll;
    public boolean noScroll(boolean flagScroll){
        this.flagScroll=flagScroll;
        return flagScroll;
    }


    /**
     * @param event 向VelocityTracker添加MotionEvent
     * @see VelocityTracker#obtain()
     * @see VelocityTracker#addMovement(MotionEvent)
     */
    private void acquireVelocityTracker(final MotionEvent event) {
        if (null == mVelocityTracker) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    /**
     * * 释放VelocityTracker
     *
     * @see VelocityTracker#clear()
     * @see VelocityTracker#recycle()
     */
    private void releaseVelocityTracker() {
        if (null != mVelocityTracker) {
            mVelocityTracker.clear();
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    //每次ViewDetach的时候，判断一下 ViewCache是不是自己，如果是自己，关闭侧滑菜单，且ViewCache设置为null，
    // 理由：1 防止内存泄漏(ViewCache是一个静态变量)
    // 2 侧滑删除后自己后，这个View被Recycler回收，复用，下一个进入屏幕的View的状态应该是普通状态，而不是展开状态。
    @Override
    protected void onDetachedFromWindow() {
        if (this == mViewCache) {
            mViewCache.smoothClose();
            mViewCache = null;
        }
        super.onDetachedFromWindow();
    }

    public static SwipeMenuView getViewCache(){
        return mViewCache;
    }
    public static void setViewCache(SwipeMenuView viewCache){
        mViewCache=viewCache;
    }
    //展开时，禁止长按
    @Override
    public boolean performLongClick() {
        if (Math.abs(getScrollX()) > mScaleTouchSlop) {
            return false;
        }
        return super.performLongClick();
    }

    //平滑滚动 弃用 改属性动画实现
/*    @Override
    public void computeScroll() {
        //判断Scroller是否执行完毕：
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            //通知View重绘-invalidate()->onDraw()->computeScroll()
            invalidate();
        }
    }*/

    /**
     * 快速关闭。
     * 用于 点击侧滑菜单上的选项,同时想让它快速关闭(删除 置顶)。
     * 这个方法在ListView里是必须调用的，
     * 在RecyclerView里，视情况而定，如果是mAdapter.notifyItemRemoved(pos)方法不用调用。
     */
    public void quickClose() {
        if (this == mViewCache) {
            //先取消展开动画
            if (null != mExpandAnim && mExpandAnim.isRunning()) {
                mExpandAnim.cancel();
            }
            mViewCache.scrollTo(0, 0);//关闭
            mViewCache = null;
        }
    }

}
