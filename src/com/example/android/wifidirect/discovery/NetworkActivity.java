package com.example.android.wifidirect.discovery;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SlidingDrawer;

public class NetworkActivity extends Activity {

    private static final String TAG = "NetworkActivity";

    RelativeLayout mMainHex;
    ImageView mMainHexImg;
    RotateAnimation rotate;

    private final static int INITIAL_STATE = 0;
    private final static int SEARCH_STATE = 0;
    int state = INITIAL_STATE;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.network);
        mMainHex = (RelativeLayout) findViewById(R.id.main_hex_rel);
        mMainHexImg = (ImageView) findViewById(R.id.main_hex);


        rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setDuration(1000);
        rotate.setRepeatCount(Animation.INFINITE);
        mMainHexImg.startAnimation(rotate);

    }


    public void hexClicked(View v) {
        // Move the hex to the center
        if (state == INITIAL_STATE) {
            mMainHexImg.setAnimation(null);
            Animation centerAnim = AnimationUtils.loadAnimation(this, R.anim.center);
            v.startAnimation(centerAnim);
        }
    }
}