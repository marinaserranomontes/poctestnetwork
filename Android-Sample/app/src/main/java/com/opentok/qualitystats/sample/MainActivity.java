package com.opentok.qualitystats.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;
import com.opentok.android.SubscriberKit.VideoStatsListener;

public class MainActivity extends Activity implements Session.SessionListener, PublisherKit.PublisherListener, SubscriberKit.SubscriberListener {

    private static final String LOGTAG = "quality-stats-demo";

    private static final String SESSION_ID = "";
    private static final String TOKEN = "";
    private static final String APIKEY = "";

    private static final int TEST_DURATION = 15; //test quality duration in seconds
    private static final int TIME_SEC = 1000; //1 sec
    private static final int TIME_WINDOW = 7; //time interval to check the quality

    private Session mSession;
    private Publisher mPublisher;
    private Subscriber mSubscriber;

    private double mVideoPLRatio = 0.0;
    private long mVideoBw = 0;

    private double mAudioPLRatio = 0.0;
    private long mAudioBw = 0;

    private long startTimeAudio = 0;
    private long startTimeVideo = 0;

    private Handler mHandler = new Handler();

    private ProgressDialog mProgressDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionConnect();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if (mSession != null) {
            mSession.disconnect();
        }
    }

    public void sessionConnect() {
        Log.i(LOGTAG, "Connecting session");
        if (mSession == null) {
            mSession = new Session(this, APIKEY, SESSION_ID);
            mSession.setSessionListener(this);

            mProgressDialog = ProgressDialog.show(this, "Checking your available bandwidth", "Please wait");
            mSession.connect(TOKEN);
        }
    }

    @Override
    public void onConnected(Session session) {
        Log.i(LOGTAG, "Session is connected");

        mPublisher = new Publisher(this);
        mPublisher.setPublisherListener(this);
        mPublisher.setAudioFallbackEnabled(false);
        mPublisher.setPublishVideo(false); //at first, check the audio-only case
        mSession.publish(mPublisher);
    }

    @Override
    public void onDisconnected(Session session) {
        Log.i(LOGTAG, "Session is disconnected");

        mPublisher = null;
        mSubscriber = null;
        mSession = null;

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        Log.i(LOGTAG, "Session error: " + opentokError.getMessage());
        showAlert("Error", "Session error: " + opentokError.getMessage());
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        Log.i(LOGTAG, "Session onStreamDropped");
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        Log.i(LOGTAG, "Session onStreamReceived");
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
        Log.i(LOGTAG, "Publisher onStreamCreated");
        if (mSubscriber == null) {
            subscribeToStream(stream);
        }
    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
        Log.i(LOGTAG, "Publisher onStreamDestroyed");
        if (mSubscriber == null) {
            unsubscribeFromStream(stream);
        }
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {
        Log.i(LOGTAG, "Publisher error: " + opentokError.getMessage());
        showAlert("Error", "Publisher error: " + opentokError.getMessage());
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {
        Log.i(LOGTAG, "Subscriber onConnected");
        mHandler.postDelayed(statsRunnable, TEST_DURATION * TIME_SEC);
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {
        Log.i(LOGTAG, "Subscriber onDisconnected");
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {
        Log.i(LOGTAG, "Subscriber error: " + opentokError.getMessage());
        showAlert("Error", "Subscriber error: " + opentokError.getMessage());
    }

    private void subscribeToStream(Stream stream) {
        mSubscriber = new Subscriber(MainActivity.this, stream);

        mSubscriber.setSubscriberListener(this);
        mSession.subscribe(mSubscriber);
        mSubscriber.setVideoStatsListener(new VideoStatsListener() {

            @Override
            public void onVideoStats(SubscriberKit subscriber,
                                     SubscriberKit.SubscriberVideoStats stats) {
                if ( startTimeVideo == 0) {
                    startTimeVideo = System.currentTimeMillis();
                }
                checkVideoStats(stats);
            }

        });

        mSubscriber.setAudioStatsListener(new SubscriberKit.AudioStatsListener() {
            @Override
            public void onAudioStats(SubscriberKit subscriber, SubscriberKit.SubscriberAudioStats stats) {
                if (startTimeAudio == 0) {
                    startTimeAudio = System.currentTimeMillis();
                }
                checkAudioStats(stats);

                //check audio quality after TIME_WINDOW seconds
                if ((System.currentTimeMillis() - startTimeAudio) / 1000 > TIME_WINDOW && !mPublisher.getPublishVideo()) {
                    checkAudioQuality();
                }
            }
        });
    }

    private void unsubscribeFromStream(Stream stream) {
        if (mSubscriber.getStream().equals(stream)) {
            mSubscriber = null;
        }
    }

    private void checkVideoStats(SubscriberKit.SubscriberVideoStats stats) {
        long now = System.currentTimeMillis();

        mVideoPLRatio = (double) stats.videoPacketsLost / (double) stats.videoPacketsReceived;
        if ((now - startTimeVideo) != 0) {
            mVideoBw = ((8 * 1000 * (stats.videoBytesReceived)) / (now - startTimeVideo));
        }
        Log.i(LOGTAG, "Video bandwidth: " + mVideoBw + "Video Bytes received: "+ stats.videoBytesReceived);
        Log.i(LOGTAG, "Video packet lost: "+ stats.videoPacketsLost + "Video packet loss ratio: " + mVideoPLRatio);
    }

    private void checkAudioStats(SubscriberKit.SubscriberAudioStats stats) {
        long now = System.currentTimeMillis();

        mAudioPLRatio = (double) stats.audioPacketsLost / (double) stats.audioPacketsReceived;
        if ((now - startTimeAudio) != 0) {
            mAudioBw = ((8 * 1000 * (stats.audioBytesReceived)) / (now - startTimeAudio));
        }
        Log.i(LOGTAG, "Audio bandwidth: " + mAudioBw + " Audio Bytes received: " + stats.audioBytesReceived);
        Log.i(LOGTAG, "Audio packet lost : " + stats.audioPacketsLost + "Audio packet loss ratio: " + mAudioPLRatio);
    }

    private void checkAudioQuality() {
        if (mSession != null) {
            Log.i(LOGTAG, "Check audio quality stats data");
            if ( mAudioPLRatio < 0.03 ){
                //go to video call to check the quality with enabled video
                mPublisher.setPublishVideo(true);
                mSubscriber.setSubscribeToVideo(true);
            }
            else {
                //quality is not good for audio only
                showAlert("Not good", "You can't connect successfully");
                mSession.disconnect();
            }
        }
    }

    private void checkQuality() {
        if (mSession != null) {
            Log.i(LOGTAG, "Check quality stats data");

            if ( mVideoBw < 150000 || mVideoPLRatio > 0.05 ) {
                showAlert("Voice-only", "Your bandwidth is too low for video");
            }
            else {
                showAlert("All good", "You're all set!");
            }
        }
    }

    private void showAlert(String title, String Message) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(title)
                .setMessage(Message)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                try {
                                    finish();
                                } catch (Throwable throwable) {
                                    throwable.printStackTrace();
                                }
                            }
                        }).setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private Runnable statsRunnable = new Runnable() {

        @Override
        public void run() {
            if (mSession != null ) {
                mSession.disconnect();
                checkQuality();
            }
        }
    };

}
