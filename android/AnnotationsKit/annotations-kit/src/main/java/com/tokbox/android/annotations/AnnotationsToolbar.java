package com.tokbox.android.annotations;


import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.w3c.dom.Text;


public class AnnotationsToolbar extends LinearLayout {

    private View rootView;
    private ImageButton mFreeHandBtn;
    private ImageButton mEraseBtn;
    private ImageButton mTypeBtn;
    private ImageButton mScreenshotBtn;
    private ImageButton mPickerColorBtn;
    private TextView mDoneBtn;

    private Context mContext;
    private LinearLayout mMainToolbar;
    private LinearLayout mColorToolbar;
    private HorizontalScrollView mColorScrollView;

    private ActionsListener mActionsListener;

    public  interface ActionsListener {
        void onItemSelected(View v, boolean selected);
        void onColorSelected(int color);
    }

    public void setActionListener(ActionsListener listener) {
        this.mActionsListener = listener;
    }


    private OnClickListener colorClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            int color = getResources().getColor(R.color.picker_color_orange);

            if (v.getId() == R.id.picker_purple){
                color = getResources().getColor(R.color.picker_color_purple);
            }
            if (v.getId() == R.id.picker_red){
                color = getResources().getColor(R.color.picker_color_red);
            }
            if (v.getId() == R.id.picker_orange){
                color = getResources().getColor(R.color.picker_color_orange);
            }
            if (v.getId() == R.id.picker_blue){
                color = getResources().getColor(R.color.picker_color_blue);
            }
            if (v.getId() == R.id.picker_green){
                color = getResources().getColor(R.color.picker_color_green);
            }
            if (v.getId() == R.id.picker_white){
                color = getResources().getColor(R.color.picker_color_white);
            }
            if (v.getId() == R.id.picker_black){
                color = getResources().getColor(R.color.picker_color_black);
            }
            if (v.getId() == R.id.picker_yellow){
                color = getResources().getColor(R.color.picker_color_yellow);
            }
            if (v.getId() == R.id.picker_gray){
                color = getResources().getColor(R.color.picker_color_gray);
            }

            updateColorPickerSelectedButtons(v, color);
            if (v.isSelected()) {
                v.setSelected(false);
            } else {
                v.setSelected(true);
            }

            if  (mActionsListener != null ){
                mActionsListener.onColorSelected(color);
            }
        }
    };
    public AnnotationsToolbar(Context context) {
        super(context);
        mContext = context;

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        init();
    }

    public AnnotationsToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        init();
    }

    public void init() {
        rootView = inflate(mContext, R.layout.annotations_toolbar, this);
        mMainToolbar = (LinearLayout) rootView.findViewById(R.id.main_toolbar);

        mColorToolbar = (LinearLayout) rootView.findViewById(R.id.color_toolbar);
        mColorScrollView = (HorizontalScrollView) rootView.findViewById(R.id.color_view);
        mFreeHandBtn = (ImageButton) mMainToolbar.findViewById(R.id.draw_freehand);
        mPickerColorBtn = (ImageButton) mMainToolbar.findViewById(R.id.picker_color);
        mTypeBtn = (ImageButton) mMainToolbar.findViewById(R.id.type_tool);
        mScreenshotBtn = (ImageButton) mMainToolbar.findViewById(R.id.screenshot);
        mEraseBtn = (ImageButton) mMainToolbar.findViewById(R.id.erase);
        mDoneBtn = (TextView) mMainToolbar.findViewById(R.id.done);

        final int mCount = mColorToolbar.getChildCount();

        int [] colors = {R.color.picker_color_blue,  R.color.picker_color_purple, R.color.picker_color_red, R.color.picker_color_orange,
                R.color.picker_color_yellow, R.color.picker_color_green,  R.color.picker_color_black, R.color.picker_color_gray, R.color.picker_color_white};

        // Loop through all of the children.
        for (int i = 0; i < mCount; ++i) {
            mColorToolbar.getChildAt(i).setOnClickListener(colorClickListener);
            ((ImageButton)mColorToolbar.getChildAt(i)).setColorFilter(getResources().getColor(colors [i]));
        }

        //Init actions
        mFreeHandBtn.setOnClickListener(mActionsClickListener);
        mTypeBtn.setOnClickListener(mActionsClickListener);
        mEraseBtn.setOnClickListener(mActionsClickListener);
        mScreenshotBtn.setOnClickListener(mActionsClickListener);
        mPickerColorBtn.setOnClickListener(mActionsClickListener);
        mDoneBtn.setOnClickListener(mActionsClickListener);

        mDoneBtn.setSelected(false);
    }

    public void restart(){
        int mCount = mMainToolbar.getChildCount();
        for (int i = 0; i < mCount; ++i) {
            mMainToolbar.getChildAt(i).setSelected(false);
        }

        mCount = mColorToolbar.getChildCount();
        for (int i = 0; i < mCount; ++i) {
            mColorToolbar.getChildAt(i).setSelected(false);
        }

    }
    private OnClickListener mActionsClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if ( mActionsListener != null ){
                if (v.getId() == R.id.picker_color){
                    if (mColorScrollView.getVisibility() == View.GONE)
                        mColorScrollView.setVisibility(View.VISIBLE);
                    else {
                        mColorScrollView.setVisibility(View.GONE);
                    }
                }
                else {
                    mColorScrollView.setVisibility(View.GONE);
                }
                updateSelectedButtons(v);
                if (v.getId() != R.id.screenshot && v.getId() != R.id.erase && v.getId() != R.id.done) {
                    if (v.isSelected()) {
                        v.setSelected(false);
                    } else {
                        v.setSelected(true);
                    }
                }
                mActionsListener.onItemSelected(v, v.isSelected());
            }
        }
    };
    private void updateColorPickerSelectedButtons(View v, int color){
        int mCount = mColorToolbar.getChildCount();

        mPickerColorBtn.setColorFilter(color);

        for (int i = 0; i < mCount; ++i) {
            if (mColorToolbar.getChildAt(i).getId() != v.getId() && mColorToolbar.getChildAt(i).isSelected()){
                mColorToolbar.getChildAt(i).setSelected(false);
            }
        }
    }
    private void updateSelectedButtons(View v){
        int mCount = mMainToolbar.getChildCount();

        for (int i = 0; i < mCount; ++i) {
            if (mMainToolbar.getChildAt(i).getId() != v.getId() && mMainToolbar.getChildAt(i).isSelected()){
                mMainToolbar.getChildAt(i).setSelected(false);
            }
        }
    }

}
