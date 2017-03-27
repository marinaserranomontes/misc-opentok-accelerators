package com.tokbox.android.accpack;


import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.util.Log;
import android.view.View;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;
import com.tokbox.android.accpack.config.OpenTokConfig;
import com.tokbox.android.logging.OTKAnalytics;
import com.tokbox.android.logging.OTKAnalyticsData;

import java.util.ArrayList;
import java.util.UUID;

public class OneToOneCommunication implements
        AccPackSession.SessionListener, Publisher.PublisherListener, Publisher.CameraListener, Subscriber.SubscriberListener, Subscriber.VideoListener, Session.ReconnectionListener {

    private static final String LOGTAG = OneToOneCommunication.class.getName();
    private Context mContext;

    private AccPackSession mSession;
    private Publisher mPublisher;
    private Subscriber mSubscriber;
    private Subscriber mScreenSubscriber;
    private ArrayList<Stream> mStreams;

    private boolean isInitialized = false;
    private boolean isStarted = false;
    private boolean mLocalAudio = true;
    private boolean mLocalVideo = true;
    private boolean mRemoteAudio = true;
    private boolean mRemoteVideo = true;

    private int mCameraId;

    private boolean isRemote = false;
    private boolean startPublish = false;

    private boolean isScreensharing = false;

    protected Listener mListener;

    private String mSessionId;
    private String mApiKey;
    private String mToken;

    private boolean mSubscribeToSelf = false;

    private OTKAnalyticsData mAnalyticsData;
    private OTKAnalytics mAnalytics;

    private BaseVideoRenderer mScreenRenderer;

    /**
     * Defines values for the {@link #enableLocalMedia(MediaType, boolean)}
     * and {@link #enableRemoteMedia(MediaType, boolean)} methods.
     */
    public enum MediaType {
        AUDIO,
        VIDEO
    };

    /**
     * Monitors OpenTok actions which should be notified to the activity.
     */
    public static interface Listener {

        /**
         * Invoked when the onetoonecommunicator is initialized
         */
        void onInitialized();

        /**
         * Invoked when there is an error trying to connect to the session, publishing or subscribing.
         *
         * @param error The error code and error message.
         */
        void onError(String error);

        /**
         * Invoked when the quality of the network is not really good
         *
         * @param warning Indicates if the platform has run an alert (quality really wrong and video is disabled)
         *                or a warning (quality is poor but the video is not disabled).
         */
        void onQualityWarning(boolean warning);

        /**
         * Invoked when the remote video is disabled.
         * Reasons: the remote stops to publish the video, the quality is wrong and the platform has disabled the video
         * or the video remote control has disabled.
         *
         * @param enabled Indicates the status of the remote video.
         */
        void onAudioOnly(boolean enabled);

        /**
         * Invoked when the preview (view) is ready to be added to the container.
         *
         * @param preview Indicates the publisher view.
         */
        void onPreviewReady(View preview);

        /**
         * Invoked when the remote (subscriber view) is ready to be added to the container.
         *
         * @param remoteView Indicates the subscriber view.
         */
        void onRemoteViewReady(View remoteView);

        /**
         * Invoked when the session is attempting to reconnect
         */
        void onReconnecting();

        /**
         * Invoked when the session reconnected
         */
        void onReconnected();

        /**
         * Invoked when the camera change the id
         */
        void onCameraChanged(int newCameraId);
    }

    /*Constructor
     * @param context Application context
     * @param sessionId OpenTok credentials
     * @param token OpenTok credentials
     * @apiKey OpenTok credentials
     */
    public OneToOneCommunication(Context context, String sessionId, String token, String apiKey) {
        this.mContext = context;
        this.mSessionId = sessionId;
        this.mToken = token;
        this.mApiKey = apiKey;

        mStreams = new ArrayList<Stream>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCameraId = CameraCharacteristics.LENS_FACING_FRONT;
        }
        else {
            mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }
    }

    /**
     * Set 1to1 communication listener.
     * @param listener OneToOne Listener
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Enable or disable the subscribeToSelf feature
     * @param subscribeToSelf Whether to enable subscribeToSelf (<code>true</code>) or not (
     *              <code>false</code>).
     */
    public void setSubscribeToSelf(boolean subscribeToSelf) {
        this.mSubscribeToSelf = subscribeToSelf;
    }

    public void init() {
        if (mSession == null) {
            mSession = new AccPackSession(mContext,
                    mApiKey, mSessionId);

            mSession.setSessionListener(this);
            mSession.connect(mToken);
        }
    }

    /**
     * Start the communication.
     */
    public void start() {
        addLogEvent(OpenTokConfig.LOG_ACTION_START_COMM, OpenTokConfig.LOG_VARIATION_ATTEMPT);

        if (mSession != null && isInitialized) {

            if (mPublisher == null) {
                mPublisher = new Publisher(mContext, "myPublisher");
                mPublisher.setPublisherListener(this);
                mPublisher.setCameraListener(this);

                mPublisher.setPublishVideo(mLocalVideo);
                mPublisher.setPublishAudio(mLocalAudio);

                attachPublisherView();
                mSession.publish(mPublisher);
                startPublish = false;
            }
        } else {
            startPublish = true;
            init();

        }
        addLogEvent(OpenTokConfig.LOG_ACTION_START_COMM, OpenTokConfig.LOG_VARIATION_SUCCESS);
    }

    /**
     * End the communication.
     */
    public void end() {
        addLogEvent(OpenTokConfig.LOG_ACTION_END_COMM, OpenTokConfig.LOG_VARIATION_ATTEMPT);
        if ( mSession != null ) {

            if (mPublisher != null) {
                mSession.unpublish(mPublisher);

            }
            if (mSubscriber != null) {
                mSession.unsubscribe(mSubscriber);
            }
            if (mScreenSubscriber != null){
                mSession.unsubscribe(mScreenSubscriber);
            }
            isRemote = false;
            restartViews();
            mPublisher = null;
            isScreensharing = false;
            mSubscriber = null;
            isStarted = false;
            addLogEvent(OpenTokConfig.LOG_ACTION_END_COMM, OpenTokConfig.LOG_VARIATION_SUCCESS);
        }
    }

    /**
     * Destroy the communication.
     */
    public void destroy() {

        if ( mPublisher != null ) {
            mSession.unpublish(mPublisher);
            mPublisher = null;
        }
        if ( mSubscriber != null ) {
            mSession.unsubscribe(mSubscriber);
            mSubscriber = null;
        }
        if ( mSession != null ) {
            mSession.disconnect();
        }
    }

    /**
     * Enable/disable the local audio/video
     *
     * @param type  The MediaType value: audio or video
     * @param value Whether to enable video/audio (<code>true</code>) or not (
     *              <code>false</code>).
     */
    public void enableLocalMedia(MediaType type, boolean value) {

        switch (type) {
            case AUDIO:
                if ( mPublisher != null ) {
                    mPublisher.setPublishAudio(value);
                }
                this.mLocalAudio = value;
                break;

            case VIDEO:
                this.mLocalVideo = value;
                if ( mPublisher != null ) {
                    mPublisher.setPublishVideo(value);
                    if (value) {
                        mPublisher.getView().setVisibility(View.VISIBLE);
                    } else {
                        mPublisher.getView().setVisibility(View.GONE);
                    }
                }
                break;
        }
    }

    /**
     * Enable/disable the remote audio/video
     *
     * @param type  The MediaType value: audio or video
     * @param value Whether to enable video/audio (<code>true</code>) or not (
     *              <code>false</code>).
     */
    public void enableRemoteMedia(MediaType type, boolean value) {
        if ( mSubscriber != null ) {
            switch (type) {
                case AUDIO:
                    mSubscriber.setSubscribeToAudio(value);
                    this.mRemoteAudio = value;
                    break;

                case VIDEO:
                    mSubscriber.setSubscribeToVideo(value);
                    this.mRemoteVideo = value;
                    setRemoteAudioOnly(value ? false : true);
                    break;
            }

        }
    }

    /**
     * Cycles between cameras, if there are multiple cameras on the device.
     */
    public void swapCamera() {
        if ( mPublisher != null ) {
            mPublisher.cycleCamera();
        }
    }

    /**
     * Check if the communication started
     *
     * @return true if the session is connected; false if it is not.
     */
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * Check if the communication started
     *
     * @return true if the session is connected; false if it is not.
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Whether the Local/Publisher is publishing audio or not
     *
     * @return true if the Publisher is publishing audio; false if it is not.
     */
    public boolean getLocalAudio() {
        return mLocalAudio;
    }

    /**
     * Whether the Local/Publisher is publishing video or not
     *
     * @return true if the Publisher is publishing video; false if it is not.
     */
    public boolean getLocalVideo() {
        return mLocalVideo;
    }

    /**
     * Whether the Subscriber/Remote is subscribing to audio or not
     *
     * @return true if the Subscriber is subscribing to audio; false if it is not.
     */
    public boolean getRemoteAudio() {
        return mRemoteAudio;
    }

    /**
     * Whether the Subscriber/Remote is subscribing to video or not
     *
     * @return true if the Subscriber is subscribing to video; false if it is not.
     */
    public boolean getRemoteVideo() {
        return mRemoteVideo;
    }

    /**
     * Whether the Remote is connected or not.
     *
     * @return true if the Remote is connected; false if it is not.
     */
    public boolean isRemote() {
        return isRemote;
    }

    /**
     * Whether the Screensharing remote is connected or not.
     *
     * @return true if the screensharing remote is connected; false if it is not.
     */
    public boolean isScreensharing() {
        return isScreensharing;
    }

    /**
     * Check the active camera
     *
     * @return The ID of the active camera.
     */
    public int getCameraId() {
        return mCameraId;
    }

    public void reloadViews() {
        if ( mPublisher != null ) {
            attachPublisherView();
        }
        if ( isRemote && mSubscriber != null ) {
            attachSubscriberView(mSubscriber);
        }
    }

    private void subscribeToStream(Stream stream) {
        Log.i(LOGTAG, "SubscribeToStream");

        if ( stream.getStreamVideoType() == Stream.StreamVideoType.StreamVideoTypeScreen ){
            Log.i(LOGTAG, "Subscriber with type screen");
            mScreenSubscriber = new Subscriber(mContext, stream);
            mScreenSubscriber.setVideoListener(this);
            mScreenSubscriber.setSubscriberListener(this);

            if ( mScreenRenderer != null ){
                mScreenSubscriber.setRenderer(mScreenRenderer);
            }
            mSession.subscribe(mScreenSubscriber);

            isScreensharing = true;
        }
        else {
            if ( mSubscriber == null ) { //oneToOne --> keep the first subscriber connection
                mSubscriber = new Subscriber(mContext, stream);
                mSubscriber.setVideoListener(this);
                mSubscriber.setSubscriberListener(this);
                mSession.subscribe(mSubscriber);
            }
        }
    }

    private void unsubscribeFromStream(Stream stream) {
        if ( mStreams.size() > 0 ) {
            mStreams.remove(stream);

            if (stream.getStreamVideoType() == Stream.StreamVideoType.StreamVideoTypeScreen) {
                isScreensharing = false;
                if (mScreenSubscriber != null && mScreenSubscriber.getStream().equals(stream) ) {
                    onRemoteViewReady(mScreenSubscriber.getView());
                    mScreenSubscriber = null;
                }
            }
            else {
                if ( mSubscriber != null && mSubscriber.getStream().equals(stream) ) {
                    isRemote = false;
                    onRemoteViewReady(mSubscriber.getView());
                    mSubscriber = null;
                }
            }
            /*if ( !mStreams.isEmpty() ) {
                subscribeToStream(mStreams.get(0));
            }*/
        }
    }

    private void attachPublisherView() {
        mPublisher.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL);
        onPreviewReady(mPublisher.getView());
    }

    private void attachSubscriberView(Subscriber subscriber) {
        if ( subscriber!= null && subscriber.getStream()!= null ) {
            if (subscriber.getStream().getStreamVideoType() == Stream.StreamVideoType.StreamVideoTypeScreen) {
                subscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                        BaseVideoRenderer.STYLE_VIDEO_FIT);
            } else {
                subscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                        BaseVideoRenderer.STYLE_VIDEO_FILL);

            }
            isRemote = true;
            onRemoteViewReady(subscriber.getView());
        }
    }

    private void setRemoteAudioOnly(boolean audioOnly) {
        if ( !audioOnly ) {
            mSubscriber.getView().setVisibility(View.VISIBLE);
            onAudioOnly(false);
        } else {
            mSubscriber.getView().setVisibility(View.GONE);
            onAudioOnly(true);
        }
    }

    private void restartComm() {
        mSubscriber = null;
        isRemote = false;
        isScreensharing = false;
        isInitialized = false;
        isStarted = false;
        mPublisher = null;
        mLocalAudio = true;
        mLocalVideo = true;
        mRemoteAudio = true;
        mRemoteVideo = true;
        mStreams.clear();
        mSession = null;
    }

    private void restartViews() {
        if ( mSubscriber != null ) {
            onRemoteViewReady(mSubscriber.getView());
        }
        if ( mScreenSubscriber != null ) {
            onRemoteViewReady(mScreenSubscriber.getView());
        }
        if ( mPublisher != null ){
            onPreviewReady(null);
        }
    }

    protected void onInitialized() {
        if ( this.mListener != null ) {
            this.mListener.onInitialized();
        }

        addLogEvent(OpenTokConfig.LOG_ACTION_INITIALIZE, OpenTokConfig.LOG_VARIATION_SUCCESS);
    }

    protected void onError(String error) {
        if ( this.mListener != null ) {
            this.mListener.onError(error);
        }
    }

    protected void onQualityWarning(boolean warning) {
        if ( this.mListener != null ) {
            this.mListener.onQualityWarning(warning);
        }
    }

    protected void onAudioOnly(boolean enabled) {
        if ( this.mListener != null ) {
            this.mListener.onAudioOnly(enabled);
        }
    }

    protected void onPreviewReady(View preview) {
        if ( this.mListener != null ) {
            this.mListener.onPreviewReady(preview);
        }
    }

    protected void onRemoteViewReady(View remoteView) {
        if ( this.mListener != null ) {
            this.mListener.onRemoteViewReady(remoteView);
        }
    }

    protected void onReconnecting(){
        if ( this.mListener != null ) {
            this.mListener.onReconnecting();
        }
    }

    protected void onReconnected(){
        if ( this.mListener != null ) {
            this.mListener.onReconnected();
        }
    }

    protected void onCameraChanged(int newCameraId){
        if ( this.mListener != null ) {
            this.mListener.onCameraChanged(newCameraId);
        }
    }

    private void addLogEvent(String action, String variation){
        if ( mAnalytics!= null ) {
            mAnalytics.logEvent(action, variation);
        }
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
        isStarted = true;

        if ( mStreams.size() > 0 ) {
            for ( Stream stream1 : mStreams ) {
                subscribeToStream(stream1);
            }
        }

        if ( mSubscribeToSelf ) {
            mStreams.add(stream);
            if ( mSubscriber == null ) {
                subscribeToStream(stream);
            }
        }
    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
        Log.i(LOGTAG, "onStreamDestroyed");

        if ( mSubscribeToSelf && (mSubscriber != null || mScreenSubscriber != null)) {
            unsubscribeFromStream(stream);
        }
        //restart media status
        mLocalAudio = true;
        mLocalVideo = true;
        mRemoteAudio = true;
        mRemoteVideo = true;
    }


    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {
        Log.i(LOGTAG, "Error publishing: " + opentokError.getErrorCode() + "-" + opentokError.getMessage());
        onError(opentokError.getErrorCode() + " - " + opentokError.getMessage());
        restartComm();

        addLogEvent(OpenTokConfig.LOG_ACTION_START_COMM, OpenTokConfig.LOG_VARIATION_ERROR);
    }

    @Override
    public void onConnected(Session session) {
        Log.i(LOGTAG, "Connected to the session.");
        isInitialized = true;

        //Init the analytics logging
        String source = mContext.getPackageName();

        SharedPreferences prefs = mContext.getSharedPreferences("opentok", Context.MODE_PRIVATE);
        String guidVSol = prefs.getString("guidVSol", null);
        if (null == guidVSol) {
            guidVSol = UUID.randomUUID().toString();
            prefs.edit().putString("guidVSol", guidVSol).commit();
        }

        mAnalyticsData = new OTKAnalyticsData.Builder(OpenTokConfig.LOG_CLIENT_VERSION, source, OpenTokConfig.LOG_COMPONENTID, guidVSol).build();
        mAnalytics = new OTKAnalytics(mAnalyticsData);

        mAnalyticsData.setSessionId(mSessionId);
        mAnalyticsData.setConnectionId(session.getConnection().getConnectionId());
        mAnalyticsData.setPartnerId(mApiKey);

        mAnalytics. setData(mAnalyticsData);

        addLogEvent(OpenTokConfig.LOG_ACTION_INITIALIZE, OpenTokConfig.LOG_VARIATION_ATTEMPT);

        onInitialized();

        if ( startPublish ) {
            addLogEvent(OpenTokConfig.LOG_ACTION_INITIALIZE, OpenTokConfig.LOG_VARIATION_SUCCESS);
            start();
        }
    }

    @Override
    public void onDisconnected(Session session) {
        Log.i(LOGTAG, "Disconnected to the session.");
        restartViews();
        restartComm();
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        Log.i(LOGTAG, "New remote is connected to the session");
        if ( !mSubscribeToSelf ) {
            mStreams.add(stream);
            //if ( mSubscriber == null && isStarted ) {
            if (isStarted()){
                subscribeToStream(stream);
            }
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        Log.i(LOGTAG, "Remote left the communication");
        if ( !mSubscribeToSelf ) {
            unsubscribeFromStream(stream);
        }
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        Log.i(LOGTAG, "Session error: " + opentokError.getErrorCode() + "-" + opentokError.getMessage());
        onError(opentokError.getErrorCode() + " - " + opentokError.getMessage());
        restartComm();

        addLogEvent(OpenTokConfig.LOG_ACTION_INITIALIZE, OpenTokConfig.LOG_VARIATION_ERROR );
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {
        Log.i(LOGTAG, "Subscriber connected.");
        if ( !subscriberKit.getStream().hasVideo() ) {
            attachSubscriberView(mSubscriber);
            setRemoteAudioOnly(true);
        }
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {
        Log.i(LOGTAG, "Subscriber disconnected.");
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {
        Log.i(LOGTAG, "Error subscribing: " + opentokError.getErrorCode() + "-" + opentokError.getMessage());
        onError(opentokError.getErrorCode() + " - " + opentokError.getMessage());
        restartComm();

    }

    @Override
    public void onVideoDataReceived(SubscriberKit subscriber) {
        Log.i(LOGTAG, "First frame received");
        if (subscriber.getStream().getStreamVideoType() == Stream.StreamVideoType.StreamVideoTypeScreen){
            attachSubscriberView(mScreenSubscriber);
        }
        else {
            attachSubscriberView(mSubscriber);
        }
    }

    @Override
    public void onVideoDisabled(SubscriberKit subscriberKit, String reason) {
        Log.i(LOGTAG,
                "Video disabled:" + reason);
        setRemoteAudioOnly(true); //enable audio only status
        if ( reason.equals("quality") ) {  //network quality alert
            onQualityWarning(false);
        }
    }

    @Override
    public void onVideoEnabled(SubscriberKit subscriberKit, String reason) {
        Log.i(LOGTAG, "Video enabled:" + reason);
        //disable audio only status
        setRemoteAudioOnly(false);
    }

    @Override
    public void onVideoDisableWarning(SubscriberKit subscriberKit) {
        Log.i(LOGTAG, "Video may be disabled soon due to network quality degradation.");
        //network quality warning
        onQualityWarning(true);
    }

    @Override
    public void onVideoDisableWarningLifted(SubscriberKit subscriberKit) {
        Log.i(LOGTAG, "Video may no longer be disabled as stream quality improved.");
    }

    @Override
    public void onCameraChanged(Publisher publisher, int i) {
        Log.i(LOGTAG, "Camera changed: "+i);
        mCameraId = i;
        onCameraChanged(i);
    }

    @Override
    public void onCameraError(Publisher publisher, OpentokError opentokError) {
        Log.i(LOGTAG, "Camera error: ");
        onError(opentokError.getErrorCode() + " - " + opentokError.getMessage());
    }

    @Override
    public void onReconnecting(Session session) {
        Log.i(LOGTAG, "The session is attempting to reconnect.");
        onReconnecting();
    }

    @Override
    public void onReconnected(Session session) {
        Log.i(LOGTAG, "The session reconnected.");
        onReconnected();
    }

    public AccPackSession getSession() {
        return mSession;
    }

    public View getRemoteVideoView (){
        if ( mSubscriber != null ){
            return mSubscriber.getView();
        }

        return null;
    }

    public View getRemoteScreenView (){
        if ( mScreenSubscriber != null ){
            return mScreenSubscriber.getView();
        }
        return null;
    }

    public View getPreviewView (){
        if ( mPublisher != null ){
            return mPublisher.getView();
        }

        return null;
    }

    public Publisher getLocal(){
        if ( mPublisher != null ){
            return mPublisher;
        }
        return null;
    }

    public Subscriber getRemote(){
        if ( mSubscriber != null ){
            return mSubscriber;
        }
        if ( mScreenSubscriber != null ){
            return mScreenSubscriber;
        }
        return null;
    }

    public void setRemoteFill(boolean fill){
        if ( mScreenSubscriber != null && fill ){
            mScreenSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
        }
        if ( mSubscriber != null && fill ){
            mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
        }
    }

    public void setRemoteScreenRenderer(BaseVideoRenderer renderer){
        if ( renderer != null ){
            mScreenRenderer = renderer;
        }
    }
}