package com.tokbox.android.screensharingsample;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tokbox.android.accpack.OneToOneCommunication;
import com.tokbox.android.annotations.AnnotationsToolbar;
import com.tokbox.android.annotations.AnnotationsView;
import com.tokbox.android.accpack.screensharing.ScreenSharingFragment;
import com.tokbox.android.annotations.utils.AnnotationsVideoRenderer;
import com.tokbox.android.screensharingsample.config.OpenTokConfig;
import com.tokbox.android.screensharingsample.ui.PreviewCameraFragment;
import com.tokbox.android.screensharingsample.ui.PreviewControlFragment;
import com.tokbox.android.screensharingsample.ui.RemoteControlFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements OneToOneCommunication.Listener, PreviewControlFragment.PreviewControlCallbacks,
        RemoteControlFragment.RemoteControlCallbacks, PreviewCameraFragment.PreviewCameraCallbacks, ScreenSharingFragment.ScreenSharingListener, AnnotationsView.AnnotationsListener {

    private final String LOG_TAG = MainActivity.class.getSimpleName();

    private final String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private final int permsRequestCode = 200;

    //OpenTok calls
    private OneToOneCommunication mComm;

    private RelativeLayout mPreviewViewContainer;
    private RelativeLayout mRemoteViewContainer;
    private RelativeLayout mAudioOnlyView;
    private RelativeLayout mLocalAudioOnlyView;
    private RelativeLayout.LayoutParams layoutParamsPreview;
    private RelativeLayout mCameraFragmentContainer;
    private RelativeLayout mActionBarContainer;

    private TextView mAlert;
    private ImageView mAudioOnlyImage;

    //UI control bars fragments
    private PreviewControlFragment mPreviewFragment;
    private RemoteControlFragment mRemoteFragment;
    private PreviewCameraFragment mCameraFragment;
    private FragmentTransaction mFragmentTransaction;

    //ScreenSharing fragment
    private ScreenSharingFragment mScreenSharingFragment;

    //Dialog
    ProgressDialog mProgressDialog;

    private AnnotationsToolbar mAnnotationsToolbar;

    private TextView mCallToolbar;

    private boolean isRemoteAnnotations = false;
    private boolean isScreensharing = false;
    private boolean isAnnotations = false;
    private CountDownTimer mCountDownTimer;

    private int mOrientation;

    private boolean mAudioPermission = false;
    private boolean mVideoPermission = false;
    private boolean mWriteExternalStoragePermission = false;
    private boolean mReadExternalStoragePermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(LOG_TAG, "onCreate");

        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPreviewViewContainer = (RelativeLayout) findViewById(R.id.publisherview);
        mRemoteViewContainer = (RelativeLayout) findViewById(R.id.subscriberview);
        mAlert = (TextView) findViewById(R.id.quality_warning);
        mAudioOnlyView = (RelativeLayout) findViewById(R.id.audioOnlyView);
        mLocalAudioOnlyView = (RelativeLayout) findViewById(R.id.localAudioOnlyView);
        mCameraFragmentContainer = (RelativeLayout) findViewById(R.id.camera_preview_fragment_container);
        mActionBarContainer = (RelativeLayout) findViewById(R.id.actionbar_preview_fragment_container);

        mAnnotationsToolbar = (AnnotationsToolbar) findViewById(R.id.annotations_bar);

        mCallToolbar = (TextView) findViewById(R.id.call_toolbar);

        //request Marshmallow camera permission
        if (ContextCompat.checkSelfPermission(this,permissions[1]) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,permissions[0]) != PackageManager.PERMISSION_GRANTED){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissions, permsRequestCode);
            }
        }
        else {
            mVideoPermission = true;
            mAudioPermission = true;
            mWriteExternalStoragePermission = true;
            mReadExternalStoragePermission = true;
        }

        //init 1to1 communication object
        mComm = new OneToOneCommunication(MainActivity.this, OpenTokConfig.SESSION_ID, OpenTokConfig.TOKEN, OpenTokConfig.API_KEY);
        mComm.setSubscribeToSelf(OpenTokConfig.SUBSCRIBE_TO_SELF);
        //set listener to receive the communication events, and add UI to these events
        mComm.setListener(this);
        mComm.init();

        //init remote annotations renderer
        mRenderer = new AnnotationsVideoRenderer(this);
        mComm.setRemoteScreenRenderer(mRenderer);

        //init controls fragments
        if (savedInstanceState == null) {
            mFragmentTransaction = getSupportFragmentManager().beginTransaction();
            initCameraFragment(); //to swap camera
            initPreviewFragment(); //to enable/disable local media
            initRemoteFragment(); //to enable/disable remote media
            initScreenSharingFragment();//to start/stop sharing the screen
            mFragmentTransaction.commitAllowingStateLoss();
        }

        //show connecting dialog
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle("Please wait");
        mProgressDialog.setMessage("Connecting...");
        mProgressDialog.show();

        //get orientation
        mOrientation = getResources().getConfiguration().orientation;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mCameraFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(mCameraFragment).commit();
            initCameraFragment();
        }

        if (mPreviewFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(mPreviewFragment).commit();
            initPreviewFragment();
        }

        if (mRemoteFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(mRemoteFragment).commit();
            initRemoteFragment();
        }

        if ( mScreenSharingFragment != null ){
            getSupportFragmentManager().beginTransaction()
                    .remove(mScreenSharingFragment).commit();
            initScreenSharingFragment();
        }

        if (mComm != null) {
            mComm.reloadViews(); //reload the local preview and the remote views
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_menu, menu);
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mComm != null && mComm.isStarted()) {
            if (isScreensharing) {
                onScreenSharing();
            }
            else {
                mComm.getSession().onResume();
                mComm.reloadViews();
            }
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mComm != null) {
            if (isScreensharing){
                mComm.start();
                showAVCall(true);
            }
            mComm.getSession().onPause();

            if (mComm.isRemote()) {
                mRemoteViewContainer.removeAllViews();
            }
        }

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mComm.destroy();
    }

    @Override
    public void onRequestPermissionsResult(final int permsRequestCode, final String[] permissions,
                                           int[] grantResults) {
        switch (permsRequestCode) {
            case 200:
                mVideoPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                mAudioPermission = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                mReadExternalStoragePermission = grantResults[2] == PackageManager.PERMISSION_GRANTED;
                mWriteExternalStoragePermission = grantResults[3] == PackageManager.PERMISSION_GRANTED;

                if ( !mVideoPermission || !mAudioPermission || !mReadExternalStoragePermission || !mWriteExternalStoragePermission ){
                    final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(getResources().getString(R.string.permissions_denied_title));
                    builder.setMessage(getResources().getString(R.string.alert_permissions_denied));
                    builder.setPositiveButton("I'M SURE", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.setNegativeButton("RE-TRY", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestPermissions(permissions, permsRequestCode);
                            }
                        }
                    });
                    builder.show();
                }

                break;
        }
    }

    public OneToOneCommunication getComm() {
        return mComm;
    }

    public void showRemoteControlBar(View v) {
        if (mRemoteFragment != null && mComm.isRemote()) {
            mRemoteFragment.show();
        }
    }

    public boolean isScreensharing() {
        return isScreensharing;
    }

    public void onCallToolbar(View view) {
        showAll();
    }

    //Video local button event
    @Override
    public void onDisableLocalVideo(boolean video) {
        if (mComm != null) {
            mComm.enableLocalMedia(OneToOneCommunication.MediaType.VIDEO, video);

            if (mComm.isRemote()) {
                if (!video) {
                    mAudioOnlyImage = new ImageView(this);
                    mAudioOnlyImage.setImageResource(R.drawable.avatar);
                    mAudioOnlyImage.setBackgroundResource(R.drawable.bckg_audio_only);
                    mPreviewViewContainer.addView(mAudioOnlyImage);
                } else {
                    mPreviewViewContainer.removeView(mAudioOnlyImage);
                }
            } else {
                if (!video) {
                    mLocalAudioOnlyView.setVisibility(View.VISIBLE);
                    mPreviewViewContainer.addView(mLocalAudioOnlyView);
                } else {
                    mLocalAudioOnlyView.setVisibility(View.GONE);
                    mPreviewViewContainer.removeView(mLocalAudioOnlyView);
                }
            }
        }
    }

    //Call button event
    @Override
    public void onCall() {
        if (mComm != null && mComm.isStarted()) {
            mComm.end();
            cleanViewsAndControls();
        } else {
            mComm.start();
            if (mPreviewFragment != null) {
                mPreviewFragment.setEnabled(true);
            }
        }
    }

    public boolean isScreensharing() {
        return isScreensharing;
    }

    @Override
    public void onScreenSharing() {
        if (mScreenSharingFragment.isStarted()) {
            mScreenSharingFragment.stop();
            isScreensharing = false;
            showAVCall(true);
            showAnnotationsToolbar(false);
            mPreviewFragment.restartScreensharing(); //restart screensharing UI
            mComm.start(); //restart the av call
            isAnnotations = false;
        }

        if (mScreenSharingFragment != null) {
            if (!mScreenSharingFragment.isStarted()) {
                showAVCall(false);
                mComm.end(); //stop the av call
                mScreenSharingFragment.start();
                mPreviewFragment.enableAnnotations(true);
            }
        }
    }

    @Override
    public void onAnnotations() {
        if (!isAnnotations) {
            showAnnotationsToolbar(true);
            isAnnotations = true;
        } else {
            showAnnotationsToolbar(false);
            isAnnotations = false;
        }
    }

    //Audio remote button event
    @Override
    public void onDisableRemoteAudio(boolean audio) {
        if (mComm != null) {
            mComm.enableRemoteMedia(OneToOneCommunication.MediaType.AUDIO, audio);
        }
    }

    //Video remote button event
    @Override
    public void onDisableRemoteVideo(boolean video) {
        if (mComm != null) {
            mComm.enableRemoteMedia(OneToOneCommunication.MediaType.VIDEO, video);
        }
    }

    //Camera control button event
    @Override
    public void onCameraSwap() {
        if (mComm != null) {
            mComm.swapCamera();
        }
    }

    public void onCallToolbar(View view) {
        showAll();
    }

    //OneToOneCommunicator listener events
    @Override
    public void onInitialized() {
        mProgressDialog.dismiss();
    }

    //Annotations listener events
    @Override
    public void onScreencaptureReady(Bitmap bmp) {
        saveScreencapture(bmp);
    }

    @Override
    public void onAnnotationsSelected(AnnotationsView.Mode mode) {
        if (mode.equals(AnnotationsView.Mode.Pen) || mode.equals(AnnotationsView.Mode.Text)) {
            showAll();
            //show minimized calltoolbar
            mCallToolbar.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAnnotationsToolbar.getLayoutParams();
            params.addRule(RelativeLayout.ABOVE, mCallToolbar.getId());
            mAnnotationsToolbar.setLayoutParams(params);
            mAnnotationsToolbar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAnnotationsDone() {
        restartAnnotations();
        isAnnotations = false;
    }

    //OneToOneCommunication callbacks
    @Override
    public void onError(String error) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        mComm.end(); //end communication
        mProgressDialog.dismiss();
        cleanViewsAndControls(); //restart views
    }

    @Override
    public void onQualityWarning(boolean warning) {
        if (warning) { //quality warning
            mAlert.setBackgroundResource(R.color.quality_warning);
            mAlert.setTextColor(this.getResources().getColor(R.color.warning_text));
        } else { //quality alert
            mAlert.setBackgroundResource(R.color.quality_alert);
            mAlert.setTextColor(this.getResources().getColor(R.color.white));
        }
        mAlert.bringToFront();
        mAlert.setVisibility(View.VISIBLE);
        mAlert.postDelayed(new Runnable() {
            public void run() {
                mAlert.setVisibility(View.GONE);
            }
        }, 7000);
    }

    @Override
    public void onAudioOnly(boolean enabled) {
        if (enabled) {
            mAudioOnlyView.setVisibility(View.VISIBLE);
        } else {
            mAudioOnlyView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPreviewReady(View preview) {
        mPreviewViewContainer.removeAllViews();
        if (preview != null) {
            layoutParamsPreview = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

            if (mComm.isRemote()) {
                layoutParamsPreview.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
                        RelativeLayout.TRUE);
                layoutParamsPreview.addRule(RelativeLayout.ALIGN_PARENT_RIGHT,
                        RelativeLayout.TRUE);
                layoutParamsPreview.width = (int) getResources().getDimension(R.dimen.preview_width);
                layoutParamsPreview.height = (int) getResources().getDimension(R.dimen.preview_height);
                layoutParamsPreview.rightMargin = (int) getResources().getDimension(R.dimen.preview_rightMargin);
                layoutParamsPreview.bottomMargin = (int) getResources().getDimension(R.dimen.preview_bottomMargin);
                if (mComm.getLocalVideo()) {
                    preview.setBackgroundResource(R.drawable.preview);
                }
            } else {
                preview.setBackground(null);
            }

            mPreviewViewContainer.addView(preview);
            mPreviewViewContainer.setLayoutParams(layoutParamsPreview);
            if (!mComm.getLocalVideo() && !mComm.isScreensharing()) {
                onDisableLocalVideo(false);
            }
        }
        mActionBarContainer.setBackgroundColor(getResources().getColor(R.color.bckg_bar));
    }

    @Override
    public void onRemoteViewReady(View remoteView) {
        //update preview when a new participant joined to the communication
        if (remoteView != null) {
            mRemoteViewContainer.removeAllViews();

            // check if it is screensharing
            if (mComm.isScreensharing() && mComm.isRemote()) {
                mRemoteViewContainer.removeAllViews();
                mPreviewViewContainer.removeAllViews();
                onPreviewReady(mComm.getRemoteVideoView());
                if (mComm.getRemoteScreenView() != null) {
                    //force landscape
                    if (mComm.getRemote().getStream().getVideoWidth() > mComm.getRemote().getStream().getVideoHeight()) {
                        forceLandscape();
                    }
                    //show remote view
                    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                            this.getResources().getDisplayMetrics().widthPixels, this.getResources()
                            .getDisplayMetrics().heightPixels);
                    mRemoteViewContainer.addView(mComm.getRemoteScreenView(), layoutParams);
                    this.remoteAnnotations();
                    isRemoteAnnotations = true;
                }
            } else {
                restartOrientation();
                if (mComm.isStarted()) {
                    onPreviewReady(mComm.getPreviewView()); //main preview view
                }
                if (!mComm.isRemote()) {
                    //clear views
                    onAudioOnly(false);
                    mRemoteViewContainer.removeAllViews();
                    mRemoteViewContainer.setClickable(false);
                } else {
                    showAnnotationsToolbar(false);
                    if (mComm.getRemoteVideoView() != null) {
                        //show remote view
                        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                                this.getResources().getDisplayMetrics().widthPixels, this.getResources()
                                .getDisplayMetrics().heightPixels);
                        mRemoteViewContainer.removeView(remoteView);

                        mRemoteViewContainer.addView(mComm.getRemoteVideoView(), layoutParams);
                        mRemoteViewContainer.setClickable(true);
                    }
                }
            }
        }
    }

    @Override
    public void onReconnecting() {
        Log.i(LOG_TAG, "The session is reconnecting.");
        Toast.makeText(this, R.string.reconnecting, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onReconnected() {
        Log.i(LOG_TAG, "The session reconnected.");
        Toast.makeText(this, R.string.reconnected, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCameraChanged(int newCameraId) {
        Log.i(LOG_TAG, "The camera changed. New camera id is: "+newCameraId);
    }

    private void remoteAnnotations() {
        try {
            AnnotationsView remoteAnnotationsView = new AnnotationsView(this, mComm.getSession(), OpenTokConfig.API_KEY, mComm.getRemote());

            AnnotationsVideoRenderer renderer = new AnnotationsVideoRenderer(this);
            mComm.getRemote().setRenderer(renderer);
            remoteAnnotationsView.setVideoRenderer(renderer);
            remoteAnnotationsView.attachToolbar(mAnnotationsToolbar);
            remoteAnnotationsView.setAnnotationsListener(this);
            ((ViewGroup) mRemoteViewContainer).addView(remoteAnnotationsView);
            mPreviewFragment.enableAnnotations(true);
        } catch (Exception e) {
            Log.i(LOG_TAG, "Exception - enableRemoteAnnotations: " + e);
        }
    }
    //Private methods
    private void initPreviewFragment() {
        mPreviewFragment = new PreviewControlFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.actionbar_preview_fragment_container, mPreviewFragment).commit();

        if (isRemoteAnnotations || isAnnotations) {
            mPreviewFragment.enableAnnotations(true);
        }
    }

    @Override
    public void onReconnected() {
        Log.i(LOG_TAG, "The session reconnected.");
        Toast.makeText(this, R.string.reconnected, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCameraChanged(int newCameraId) {
        Log.i(LOG_TAG, "The camera changed. New camera id is: "+newCameraId);
    }

    //Audio local button event
    @Override
    public void onDisableLocalAudio(boolean audio) {
        if (mComm != null) {
            mComm.enableLocalMedia(OneToOneCommunication.MediaType.AUDIO, audio);
        }
    }

    //cleans views and controls
    private void cleanViewsAndControls() {
        mPreviewFragment.restart();
        mActionBarContainer.setBackground(null);
    }

    private void showAVCall(boolean show) {
        if (show) {
            mPreviewViewContainer.setVisibility(View.VISIBLE);
            mRemoteViewContainer.setVisibility(View.VISIBLE);
            mCameraFragmentContainer.setVisibility(View.VISIBLE);
            mAnnotationsToolbar.setVisibility(View.GONE);
            mCallToolbar.setVisibility(View.GONE);
        } else {
            mPreviewViewContainer.setVisibility(View.GONE);
            mRemoteViewContainer.setVisibility(View.GONE);
            mCameraFragmentContainer.setVisibility(View.GONE);
        }
    }

    /**
     * Converts dp to real pixels, according to the screen density.
     *
     * @param dp A number of density-independent pixels.
     * @return The equivalent number of real pixels.
     */
    private int dpToPx(int dp) {
        double screenDensity = this.getResources().getDisplayMetrics().density;
        return (int) (screenDensity * (double) dp);
    }

    private void forceLandscape() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void restartOrientation() {
        setRequestedOrientation(mOrientation);
    }

    @Override
    public void onScreenSharingStarted() {
        Log.i(LOG_TAG, "onScreenSharingStarted");
        isScreensharing = true;
    }

    @Override
    public void onScreenSharingStopped() {
        Log.i(LOG_TAG, "onScreenSharingStopped");
    }

    @Override
    public void onScreenSharingError(String error) {
        Log.i(LOG_TAG, "onScreenSharingError " + error);
        isScreensharing = false;
        mComm.start();
        showAVCall(true);
    }

    @Override
    public void onAnnotationsViewReady(AnnotationsView view) {
        Log.i(LOG_TAG, "onAnnotationsViewReady ");
        view.setAnnotationsListener(this);
    }

    @Override
    public void onClosed() {
        Log.i(LOG_TAG, "onClosed ");
        mPreviewFragment.restartScreensharing(); //restart screensharing UI
        showAVCall(true);
        if (isAnnotations) {
            showAnnotationsToolbar(false);
            isAnnotations = false;
        }
        mComm.start(); //restart the av call
        isScreensharing = false;
    }

    //Private methods
    private void initPreviewFragment() {
        mPreviewFragment = new PreviewControlFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.actionbar_preview_fragment_container, mPreviewFragment).commit();

        if (isRemoteAnnotations || isAnnotations) {
            mPreviewFragment.enableAnnotations(true);
        }
    }

    private void initRemoteFragment() {
        mRemoteFragment = new RemoteControlFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.actionbar_remote_fragment_container, mRemoteFragment).commit();
    }

    private void initCameraFragment() {
        mCameraFragment = new PreviewCameraFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.camera_preview_fragment_container, mCameraFragment).commit();
    }

    private void initScreenSharingFragment() {
        mScreenSharingFragment = ScreenSharingFragment.newInstance(mComm.getSession(), OpenTokConfig.API_KEY);
        mScreenSharingFragment.enableAnnotations(true, mAnnotationsToolbar);
        mScreenSharingFragment.setListener(this);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.screensharing_fragment_container, mScreenSharingFragment).commit();
    }

    private void remoteAnnotations() {
        try {
            mRemoteAnnotationsView = new AnnotationsView(this, mComm.getSession(), OpenTokConfig.API_KEY, mComm.getRemote());
            mRemoteAnnotationsView.setVideoRenderer(mRenderer);
            mRemoteAnnotationsView.attachToolbar(mAnnotationsToolbar);
            mRemoteAnnotationsView.setAnnotationsListener(this);
            ((ViewGroup) mRemoteViewContainer).addView(mRemoteAnnotationsView);
            mPreviewFragment.enableAnnotations(true);
        } catch (Exception e) {
            Log.i(LOG_TAG, "Exception - enableRemoteAnnotations: " + e);
        }
    }

    private void saveScreencapture(Bitmap bmp) {
        if (bmp != null) {
            Bitmap annotationsBmp = null;
            Bitmap overlayBmp = null;
            //get bmp from annotationsView
            if ( mRemoteAnnotationsView != null ){
                annotationsBmp= getBitmapFromView(mRemoteAnnotationsView);
                overlayBmp = mergeBitmaps(bmp, annotationsBmp);
            }
            else {
                overlayBmp = bmp;
            }

            String filename;
            Date date = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            filename = sdf.format(date);
            try {
                //String path = Environment.getExternalStorageDirectory().toString() + "/PICTURES/Screenshots/";
                String path = Environment.getExternalStorageDirectory().toString();
                OutputStream fOut = null;
                File file = new File(path, filename + ".jpg");
                fOut = new FileOutputStream(file);

                overlayBmp.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
                fOut.flush();
                fOut.close();

                MediaStore.Images.Media.insertImage(getContentResolver()
                        , file.getAbsolutePath(), file.getName(), file.getName());

                openScreenshot(file);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Bitmap getBitmapFromView(View view) {
        Bitmap returnedBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(returnedBitmap);
        Drawable bgDrawable =view.getBackground();
        if (bgDrawable!=null)
            bgDrawable.draw(canvas);
        view.draw(canvas);
        return returnedBitmap;
    }

    private Bitmap mergeBitmaps(Bitmap bmp1, Bitmap bmp2){
        Bitmap bmpOverlay = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig());
        bmp2 = Bitmap.createScaledBitmap(bmp2, bmp1.getWidth(), bmp1.getHeight(),
                true);
        Canvas canvas = new Canvas(bmpOverlay);
        canvas.drawBitmap(bmp1, 0,0, null);
        canvas.drawBitmap(bmp2, 0,0, null);

        return bmpOverlay;
    }

    private void openScreenshot(File imageFile) {
        Uri uri = Uri.fromFile(imageFile);
        Intent intentSend = new Intent();
        intentSend.setAction(Intent.ACTION_SEND);
        intentSend.setType("image/*");

        intentSend.putExtra(Intent.EXTRA_SUBJECT, "");
        intentSend.putExtra(Intent.EXTRA_TEXT, "");
        intentSend.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(intentSend, "Share Screenshot"));
    }

    private void restartAnnotations() {
        mCallToolbar.setVisibility(View.GONE);
        showAnnotationsToolbar(false);
    }

    private void showAnnotationsToolbar(boolean show) {
        if (show) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAnnotationsToolbar.getLayoutParams();
            params.addRule(RelativeLayout.ABOVE, mActionBarContainer.getId());
            mAnnotationsToolbar.setLayoutParams(params);
            mAnnotationsToolbar.setVisibility(View.VISIBLE);
            mActionBarContainer.setVisibility(View.VISIBLE);
        } else {
            mCallToolbar.setVisibility(View.GONE);
            mAnnotationsToolbar.setVisibility(View.GONE);
            mActionBarContainer.setVisibility(View.VISIBLE);
            if ( mCountDownTimer != null ) {
                mCountDownTimer.cancel();
                mCountDownTimer = null;
            }
            mAnnotationsToolbar.restart();
        }
    }

    private void showAll() {
        mCallToolbar.setVisibility(View.GONE);
        showAnnotationsToolbar(true);
        mCountDownTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                mCallToolbar.setVisibility(View.VISIBLE);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAnnotationsToolbar.getLayoutParams();
                params.addRule(RelativeLayout.ABOVE, mCallToolbar.getId());
                mAnnotationsToolbar.setLayoutParams(params);
                mAnnotationsToolbar.setVisibility(View.VISIBLE);
                mActionBarContainer.setVisibility(View.GONE);
            }
        }.start();
    }
}

