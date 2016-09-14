# ViewPager学习
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;项目需求，想要实现一个轮播的广告页，于是使用VIewPager来实现，首先来看下PagerAdapter。
### 1.PagerAdapter
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;PagerAdapter为一个抽象类，导包如下：
    
    import android.support.v4.view.PagerAdapter;

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在使用PagerAdapter时，需要自定义类继承自PagerAdapter，同时，需要实现以下四个方法：

    @Override
    //此方法返回可获得的View数量
    public int getCount() {
        return 0;
    }

    @Override
    //用来判断instantiateItem方法返回的Object是否要和当前页面关联
    public boolean isViewFromObject(View view, Object object) {
        return false;
    }

    @Override
    //移除position处的view，将view从container中移除
    public void destroyItem(ViewGroup container, int position, Object object) {
        super.destroyItem(container, position, object);
    }

    @Override
    //创建position位置处的view，同时需要手动添加到container中
    public Object instantiateItem(ViewGroup container, int position) {
        return super.instantiateItem(container, position);
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;示例使用如下：

    public class ViewPagerAdapter extends PagerAdapter {

        private List<ImageView> views;

        public ViewPagerAdapter(List<ImageView> views) {
            this.views = views;
        }

        @Override
        public int getCount() {
            return views == null ? 0 : views.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (views != null) {
                View view = views.get(position);
                container.removeView(view);
            }
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            if (views != null) {
                View view = views.get(position);
                if (view != null) {
                    container.addView(view);
                    return view;
                }
            }   
            return null;
        }
    }

### 2.实现轮播
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;采用Handler+ViewPager来实现定时播放，同时使用LinearLayout来实现*圆点指示器*
#### a.布局如下：

    <?xml version="1.0" encoding="utf-8"?>
    <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <android.support.v4.view.ViewPager
            android:id="@+id/viewPager"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

        <LinearLayout
            android:id="@+id/indicator"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_alignParentBottom="true"
            android:layout_marginBottom="10dp"
            android:gravity="center_horizontal"
            android:orientation="horizontal"
            android:padding="5dp" />
    </RelativeLayout>


#### b.圆点指示器
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;如上面的布局文件所示，采用LinearLayout来定制圆点指示器，当只有一张图片时，不显示圆点指示器，圆点采用Shape绘制而成，主要有正常状态的圆点和被选中的圆点：
##### (1).正常圆点

    <?xml version="1.0" encoding="utf-8"?>
    <shape xmlns:android="http://schemas.android.com/apk/res/android"
        android:shape="oval"
        android:useLevel="false">
        <solid android:color="#fff" />
        <size
            android:width="10dp"
            android:height="10dp" />
    </shape>

##### (2).选中状态的圆点

    <?xml version="1.0" encoding="utf-8"?>
    <shape xmlns:android="http://schemas.android.com/apk/res/android"
        android:shape="oval"
        android:useLevel="false">
        <!-- 圆的大小，要保证长和宽一样大 -->
        <size
            android:width="10dp"
            android:height="10dp" />
        <!-- 中间填充的颜色 -->
        <solid android:color="#000" />
        <!-- 边框大小及颜色 --> 
        <stroke
        android:width="1dp"
        android:color="#fff" />
    </shape>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;使用时，根据List中ImageView的数量添加圆点，当List的大小为1时，隐藏圆点指示器，添加圆点指示器的代码如下：

    private void initIndicator() {
        //mIndicator布局中的LinearLayout
        mIndicator.removeAllViews();
        if (mImages != null && mImages.size() > 1) {
            mIndicator.setVisibility(View.VISIBLE);
        } else {
            mIndicator.setVisibility(View.GONE);
        }
        for (int i = 0; i < mImages.size(); i++) {
            ImageView imageView = new ImageView(SplashActivity.this);
            if (i == 0) {
                imageView.setBackgroundResource(R.drawable.point_selected);
            } else {
                imageView.setBackgroundResource(R.drawable.point_normal);
            }
            mIndicator.addView(imageView);
        }
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;同时，ViewPager中的页面改变时，需要改变圆点状态，方法如下：

    private void changePoint(int position) {
        //mPrePosition标识页面变换之前的位置
        if (mPrePosition != position) {
            ImageView prePoint = (ImageView) mIndicator.getChildAt(mPrePosition);
            ImageView mCurPoint = (ImageView) mIndicator.getChildAt(position);
            if (prePoint != null && mCurPoint != null) {
                prePoint.setBackgroundResource(R.drawable.point_normal);
                mCurPoint.setBackgroundResource(R.drawable.point_selected);
                mPrePosition = position;
            }
        }
    }
#### c.示例代码如下：

    //示例数据，初始化要显示的图片*****************************************
    mImages = new ArrayList<ImageView>();
    ImageView imageView1 = new ImageView(SplashActivity.this);
    imageView1.setBackgroundResource(R.drawable.default_imageload_pic);
    mImages.add(imageView1);
    ImageView imageView2 = new ImageView(SplashActivity.this);
    imageView2.setBackgroundResource(R.drawable.default_imageload_pic);
    mImages.add(imageView2);
    ImageView imageView3 = new ImageView(SplashActivity.this);
    imageView3.setBackgroundResource(R.drawable.default_imageload_pic);
    mImages.add(imageView3);
    //*********************************************************************

        mAdapter = new ViewPagerAdapter(mImages);
        mViewPager.setAdapter(mAdapter);

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;设置监听页面变化，需要在页面被选中时，改变圆点指示器的状态：
    
    mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageSelected(int position) {
            //改变圆点指示器的位置
            changePoint(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    });

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;当用户手动滑动图片时，停止自动滑动，设置一个标志位即可。
        
    mViewPager.setOnTouchListener(new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            autoChange = false;
            return false;
        }
    });

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;使用Handler自动翻页，示例代码如下：

    private Handler mHanlder = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CHANGE:
                    if (autoChange) {
                        changeImage(mPrePosition + 1);
                        mHanlder.sendEmptyMessageDelayed(MSG_CHANGE, INTERVAL);
                    }
                    break;
            }
        }
    };
    mHanlder.sendEmptyMessageDelayed(MSG_CHANGE, INTERVAL);

    //翻页
    private void changeImage(int position) {
        if (mPrePosition != position) {
            if (mImages != null) {
                if (position >= mImages.size()) {
                    position = 0;
                }
                mViewPager.setCurrentItem(position);
            }
        }
    }

### 注意：
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;为了避免*Handler*造成的*内存泄漏*，需要在onDestory方法里面remove掉所有消息，并且销毁Handler，方法如下：
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHanlder.removeCallbacksAndMessages(null);
        mHanlder = null;
    }




