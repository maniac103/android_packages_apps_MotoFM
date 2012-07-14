package com.motorola.fmradio;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.motorola.android.fmradio.IFMRadioService;
import com.motorola.android.fmradio.IFMRadioServiceCallback;
import com.motorola.fmradio.FMDataProvider.Channels;

public class FMRadioPlayerService extends Service {
    private static final String TAG = "FMRadioPlayerService";

    private static final String ACTION_AUDIOPATH_BUSY = "android.intent.action.AudioPathBusy";
    private static final String ACTION_AUDIOPATH_FREE = "android.intent.action.AudioPathFree";
    private static final String ACTION_MUSIC_META_CHANGED = "com.android.music.metachanged";
    private static final String ACTION_MUSIC_PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
    private static final String ACTION_MUSIC_SERVICE_COMMAND = "com.android.music.musicservicecommand";

    private static final String MUSIC_EXTRA_COMMAND = "command";
    private static final String MUSIC_PAUSE = "pause";

    public static int FM_ROUTING_HEADSET = 0;
    public static int FM_ROUTING_SPEAKER = 1;

    private static final String ROUTING_KEY = "FM_routing";
    private static final String ROUTING_VALUE_HEADSET = "DEVICE_OUT_WIRED_HEADPHONE";
    private static final String ROUTING_VALUE_SPEAKER = "DEVICE_OUT_SPEAKER";

    private static final String LAUNCH_KEY = "FM_launch";
    private static final String LAUNCH_VALUE_OFF = "off";
    private static final String LAUNCH_VALUE_ON = "on";

    private static final int STEREO_HEADSET = 1;
    private static final int STEREO_HEADSET2 = 2;
    private static final int OMTP_HEADSET = 3;

    private static final int MSG_SEEK_CHANNEL = 1;
    private static final int MSG_SHOW_NOTICE = 2;
    private static final int MSG_TUNE_COMPLETE = 3;
    private static final int MSG_SEEK_COMPLETE = 4;
    private static final int MSG_UPDATE_AUDIOMODE = 5;
    private static final int MSG_RDS_PS_UPDATE = 6;
    private static final int MSG_RDS_RT_UPDATE = 7;
    private static final int MSG_RDS_PTY_UPDATE = 8;
    private static final int MSG_SHUTDOWN = 9;

    private IFMRadioService mIFMRadioService = null;
    private IFMRadioPlayerServiceCallbacks mCallbacks = null;
    private boolean mReady = false;

    private boolean mUSBand = false;
    private int mAudioMode = 0;
    private int mAudioRouting = FM_ROUTING_HEADSET;

    private AudioManager am;
    private Notification mNotification;
    private PendingIntent mActivityIntent;

    private int mHeadsetState = -1;
    private boolean misPowerOn = false;
    private boolean mBound = false;

    private int mCurFreq;
    private String mRdsStationName;
    private String mRdsRadioText;
    private int mRdsPTYValue;

    private BroadcastReceiver mReceiver = null;
    private ContentObserver mObserver = null;

    protected ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "Connected to FM radio service");
            mIFMRadioService = IFMRadioService.Stub.asInterface(service);
            try {
                mIFMRadioService.registerCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "Could not register radio service callbacks", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            try {
                mIFMRadioService.unregisterCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "Unregistering radio service callbacks failed", e);
            }

            mIFMRadioService = null;
            Log.v(TAG, "Disconnected from FM radio service");
        }
    };

    protected IFMRadioServiceCallback mCallback = new IFMRadioServiceCallback.Stub() {
        @Override
        public void onCommandComplete(int cmd, int status, String value) throws RemoteException {
            Log.v(TAG, "Got radio service event: cmd " + cmd + " status " + status + " value " + value);
            switch (cmd) {
                case 0: {
                    Message msg = Message.obtain(mHandler, MSG_TUNE_COMPLETE, status, Integer.parseInt(value), null);
                    mHandler.sendMessage(msg);
                    break;
                }
                case 1: {
                    Message msg = Message.obtain(mHandler, MSG_SEEK_COMPLETE, status, Integer.parseInt(value), null);
                    mHandler.sendMessage(msg);
                    break;
                }
                case 2:
                    if (status != 0) {
                        resetRDSData();
                    }
                    if (mCallbacks != null) {
                        try {
                            mCallbacks.onScanFinished(status != 0, Integer.parseInt(value));
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report scan result", e);
                        }
                    }
                    break;
                case 3:
                    if (status == 0) {
                        notifyTuneResult(false);
                    } else if (mCallbacks != null) {
                        try {
                            mCallbacks.onAbortComplete(Integer.parseInt(value));
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report abort complete", e);
                        }
                    }
                    break;
                case 4: {
                        Message msg = Message.obtain(mHandler, MSG_RDS_PS_UPDATE, value);
                        mHandler.sendMessage(msg);
                    }
                    break;
                case 5: {
                        Message msg = Message.obtain(mHandler, MSG_RDS_RT_UPDATE, value);
                        mHandler.sendMessage(msg);
                    }
                    break;
                case 6:
                    if (mUSBand) {
                        String stationName = mIFMRadioService.getRDSStationName();
                        Message msg = Message.obtain(mHandler, MSG_RDS_PS_UPDATE, stationName);
                        mHandler.sendMessage(msg);
                    }
                    break;
                case 7: {
                        int newPty = Integer.parseInt(value) + (mUSBand ? 32 : 0);
                        Message msg = Message.obtain(mHandler, MSG_RDS_PTY_UPDATE, newPty, 0, null);
                        mHandler.sendMessage(msg);
                    }
                    break;
                case 8:
                    break;
                case 9:
                    if (status == 0) {
                        notifyEnableChangeComplete(true, false);
                    } else {
                        misPowerOn = true;
                    }
                    break;
                case 10:
                    misPowerOn = false;
                    break;
               case 15: {
                    Message msg = Message.obtain(mHandler, MSG_UPDATE_AUDIOMODE, Integer.parseInt(value), 0, null);
                    mHandler.sendMessage(msg);

                    boolean success = true;
                    if (!mReady) {
                        try {
                            /* TODO: add option for defining 'seek signal threshold' ... 12 is the default
                             */
                            success = mIFMRadioService.setRSSI(12);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not set RSSI", e);
                            success = false;
                        }
                    }
                    if (success) {
                        break;
                    }
                    /* otherwise fall-through intended, failure to set RSSI is non-fatal */
                }
                case 23:
                    if (!mReady && !enableRds()) {
                        notifyTuneResult(false);
                    }
                    break;
                case 20:
                    resetRDSData();
                    if (!mReady) {
                        Log.w(TAG, "Complete FM Radio PowerOn Sequence Succeeded!");
                        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        if (tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                            Log.d(TAG, "Phone not idle on startup, muting.");
                            setFMMuteState(true);
                        }
                        am.setParameters(LAUNCH_KEY + "=" + LAUNCH_VALUE_ON);
                        audioPrepare(mAudioRouting);
                        mReady = true;
                        notifyEnableChangeComplete(true, true);
                    }
                    break;
                case 24: {
                    Message msg = Message.obtain(mHandler, MSG_UPDATE_AUDIOMODE, Integer.parseInt(value), 0, null);
                    mHandler.sendMessage(msg);
                    break;
                }
                case 25:
                    if (mCallbacks != null) {
                        try {
                            mCallbacks.onScanUpdate(Integer.parseInt(value));
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report scan update", e);
                        }
                    }
                    break;
                case 11:
                case 12:
                case 13:
                case 14:
                case 16:
                case 17:
                case 18:
                case 19:
                case 21:
                case 22:
                    if (status == 0 && mCallbacks != null) {
                        try {
                            mCallbacks.onError();
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report error", e);
                        }
                    }
                    break;
             }
        }
    };

    private final IFMRadioPlayerService.Stub mBinder = new IFMRadioPlayerService.Stub() {
        @Override
        public void registerCallbacks(IFMRadioPlayerServiceCallbacks cb) {
            mCallbacks = cb;
        }

        @Override
        public void unregisterCallbacks() {
            mCallbacks = null;
        }

        @Override
        public int getAudioRouting() {
            return mAudioRouting;
        }

        @Override
        public boolean powerOn() {
            Log.d(TAG, "Got FM radio power on request");
            if (misPowerOn) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyEnableChangeComplete(true, true);
                    }
                });
                return true;
            }

            if (isAirplaneModeOn()) {
                Message msg = Message.obtain(mHandler, MSG_SHOW_NOTICE,
                        R.string.fmradio_airplane_mode_enabled, 0, null);
                mHandler.sendMessage(msg);
                mHandler.sendEmptyMessage(MSG_SHUTDOWN);
                return false;
            }

            Intent headsetIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
            handleHeadsetChange(headsetIntent != null ? headsetIntent.getIntExtra("state", 0) : -1);

            if (!isHeadsetConnected()) {
                return false;
            }

            if (!mBound) {
                mBound = bindService(new Intent("com.motorola.android.fmradio.FMRADIO_SERVICE"), mConnection, 1);
                if (!mBound) {
                    Log.w(TAG, "Powering on FM radio failed");
                    mHandler.sendEmptyMessage(MSG_SHUTDOWN);
                    return false;
                }
                registerBroadcastListener();
                registerObserver();
            }

            return true;
        }

        @Override
        public void powerOff() {
            Log.d(TAG, "Got FM radio power off request");
            if (mReady) {
                am.setMode(AudioManager.MODE_NORMAL);
                if (mBound) {
                    unbindService(mConnection);
                    mBound = false;
                }
            }
        }

        @Override
        public boolean scan() {
            Log.d(TAG, "Got scan request");
            if (mReady) {
                try {
                    return mIFMRadioService.scan();
                } catch (RemoteException e) {
                    Log.e(TAG, "Initiating scan failed", e);
                }
            }
            return false;
        }

        @Override
        public boolean seek(int freq, boolean upward) {
            Log.d(TAG, "Got seek request, frequency " + freq + " upward " + upward);
            if (mReady) {
                Message msg = Message.obtain(mHandler, MSG_SEEK_CHANNEL, upward ? 0 : 1, 0, null);
                mHandler.sendMessage(msg);
            }
            return mReady;
        }

        @Override
        public boolean setAudioRouting(int routing) {
            Log.d(TAG, "Got request for setting audio routing to " + routing);
            if (mReady) {
                mAudioRouting = routing;
                audioPrepare(routing);
            }
            return mReady;
        }

        @Override
        public boolean stopScan() {
            Log.d(TAG, "Got stop scan request");
            if (mReady) {
                try {
                    return mIFMRadioService.stopScan();
                } catch (RemoteException e) {
                    Log.e(TAG, "Stopping scan failed", e);
                }
            }
            return false;
        }

        @Override
        public boolean stopSeek() {
            Log.d(TAG, "Got stop seek request");
            if (mReady) {
                try {
                    return mIFMRadioService.stopSeek();
                } catch (RemoteException e) {
                    Log.e(TAG, "Stopping seek failed", e);
                }
            }
            return false;
        }

        @Override
        public boolean tune(int freq) {
            Log.d(TAG, "Got tune request, frequency " + freq);
            boolean result = false;
            if (mReady) {
                try {
                    result = mIFMRadioService.tune(freq);
                } catch (RemoteException e) {
                    Log.e(TAG, "Tuning failed", e);
                }
            }
            if (result) {
                resetRDSData();
            }
            return result;
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SEEK_CHANNEL:
                    try {
                        mIFMRadioService.seek(msg.arg1);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Seeking failed", e);
                        notifySeekResult(false);
                    }
                    break;
                case MSG_SHOW_NOTICE:
                    FMUtil.showNoticeDialog(FMRadioPlayerService.this, msg.arg1);
                    break;
                case MSG_TUNE_COMPLETE:
                    handleTuneComplete(msg.arg1 != 0, msg.arg2);
                    break;
                case MSG_SEEK_COMPLETE:
                    int preFreq = mCurFreq;
                    mCurFreq = msg.arg2;
                    Log.v(TAG, "Seek completed, success " + (msg.arg1 != 0) + " frequency " + mCurFreq);
                    resetRDSData();
                    notifySeekResult(true);
                    if (preFreq != mCurFreq) {
                        updateStateIndicators();
                    }
                    break;
                case MSG_UPDATE_AUDIOMODE:
                    mAudioMode = msg.arg1;
                    if (mCallbacks != null) {
                        try {
                            mCallbacks.onAudioModeChanged(mAudioMode != 0);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report audio mode change", e);
                        }
                    }
                    break;
                case MSG_RDS_PS_UPDATE:
                    String newPs = (String) msg.obj;
                    if (!TextUtils.equals(mRdsStationName, newPs)) {
                        mRdsStationName = newPs;
                        updateStateIndicators();
                        notifyRdsUpdate();
                    }
                    break;
                case MSG_RDS_RT_UPDATE:
                    String newRt = (String) msg.obj;
                    if (!TextUtils.equals(mRdsRadioText, newRt)) {
                        mRdsRadioText = newRt;
                        notifyRdsUpdate();
                    }
                    break;
                case MSG_RDS_PTY_UPDATE:
                    if (mRdsPTYValue != msg.arg1) {
                        mRdsPTYValue = msg.arg1;
                        notifyRdsUpdate();
                    }
                    break;
                case MSG_SHUTDOWN:
                    Log.d(TAG, "Shutting down FM radio player service");
                    notifyEnableChangeComplete(false, true);
                    restoreAudioRoute();
                    stopSelf();
                    break;
            }
        }
    };

    private void audioPrepare(int routing) {
        final String route = routing == FM_ROUTING_SPEAKER ?
                ROUTING_VALUE_SPEAKER : ROUTING_VALUE_HEADSET;

        Log.d(TAG, "Setting FM audio routing to " + route);
        am.setParameters(ROUTING_KEY + "="  + route);
    }

    private final boolean isAirplaneModeOn() {
        return Settings.System.getInt(getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) == 1;
    }

    private final boolean isMusicPlaying() {
        return ((AudioManager) getSystemService(AUDIO_SERVICE)).isMusicActive();
    }

    private void registerObserver() {
        if (mObserver != null) {
            return;
        }

        mObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                if (mCurFreq != 0) {
                    updateStateIndicators();
                }
            }
        };

        getContentResolver().registerContentObserver(Channels.CONTENT_URI, true, mObserver);
    }

    private void registerBroadcastListener() {
        if (mReceiver != null) {
            return;
        }

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "Received broadcast: " + action);

                if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                    int state = intent.getIntExtra("state", 0);
                    handleHeadsetChange(state);
                } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                    int state = intent.getIntExtra("state", 0);
                    Log.v(TAG, "Got airplane mode change message, new state " + state);
                    if (state != 0) {
                        FMUtil.showNoticeDialog(context, R.string.fmradio_airplane_mode_enabled);
                        mHandler.sendEmptyMessage(MSG_SHUTDOWN);
                    }
                } else if (action.equals(ACTION_AUDIOPATH_FREE)) {
                    Log.v(TAG, "Audio path is available again");
                    setFMMuteState(false);
                } else if (action.equals(ACTION_AUDIOPATH_BUSY)) {
                    Log.d(TAG, "Audio path is busy");
                    setFMMuteState(true);
                } else if (action.equals(ACTION_MUSIC_PLAYSTATE_CHANGED)) {
                    if (isMusicPlaying()) {
                        FMUtil.showNoticeDialog(context, R.string.fmradio_music_playing_in_listen);
                        mHandler.sendEmptyMessage(MSG_SHUTDOWN);
                    }
                } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                    String phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    Log.d(TAG, "Got phone state change, new state " + phoneState);
                    if (phoneState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            Log.w(TAG, "Waiting for route change failed", e);
                        }
                        if (mAudioRouting == FM_ROUTING_HEADSET) {
                            audioPrepare(FM_ROUTING_SPEAKER);
                            audioPrepare(FM_ROUTING_HEADSET);
                        } else {
                            audioPrepare(FM_ROUTING_HEADSET);
                            audioPrepare(FM_ROUTING_SPEAKER);
                        }
                        setFMMuteState(false);
                        audioPrepare(mAudioRouting);
                        setFMVolume(Preferences.getVolume(FMRadioPlayerService.this));
                    } else {
                        setFMMuteState(true);
                    }
                } else if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                    if (intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1) == AudioManager.STREAM_FM) {
                        int volume = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, 0);
                        Log.d(TAG, "Received FM volume change intent, setting volume to " + volume);
                        Preferences.setVolume(FMRadioPlayerService.this, volume);
                        setFMVolume(volume);
                    }
                }
            }
        };

        Log.v(TAG, "Registering broadcast receiver");
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
        filter.addAction(ACTION_AUDIOPATH_FREE);
        filter.addAction(ACTION_AUDIOPATH_BUSY);
        filter.addAction(ACTION_MUSIC_PLAYSTATE_CHANGED);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(mReceiver, filter);
    }

    private void resetRDSData() {
        mRdsStationName = null;
        mRdsPTYValue = 0;
        mRdsRadioText = null;
    }

    private void restoreAudioRoute() {
        if (am != null) {
            am.setParameters(LAUNCH_KEY + "=" + LAUNCH_VALUE_OFF);
            am.setMode(AudioManager.MODE_NORMAL);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
        am = (AudioManager) getSystemService(AUDIO_SERVICE);

        Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.setComponent(new ComponentName("com.motorola.fmradio", "com.motorola.fmradio.FMRadioMain"));
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        mActivityIntent = PendingIntent.getActivity(this, 0, launchIntent, 0);
        mNotification = new Notification(R.drawable.fm_statusbar_icon, null, System.currentTimeMillis());
        mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();

        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        if (mObserver != null) {
            getContentResolver().unregisterContentObserver(mObserver);
        }
        if (mReady) {
            restoreAudioRoute();
        }
        cancelStateIndicators();
    }

    private void setFMVolume(int volume) {
        Log.v(TAG, "setFMVolume (" + volume + ")");
        try {
            mIFMRadioService.setVolume(volume);
        } catch (RemoteException e) {
            Log.e(TAG, "Setting FM volume failed", e);
        }
    }

    private void setFMMuteState(boolean mute) {
        Log.v(TAG, "setFMMuteState (" + mute + ")");
        try {
            mIFMRadioService.setMute(mute ? 1 : 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Setting FM mute state failed", e);
        }
    }

    private void updateStateIndicators() {
        if (mCurFreq == 0) {
            return;
        }

        final String frequencyString = FMUtil.formatFrequency(this, mCurFreq);
        String stationName = null;
        Cursor cursor = getContentResolver().query(Channels.CONTENT_URI, FMUtil.PROJECTION,
                Channels.FREQUENCY + "=?", new String[] { String.valueOf(mCurFreq) }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                String name = cursor.getString(FMUtil.CHANNEL_COLUMN_NAME);
                String rdsName = cursor.getString(FMUtil.CHANNEL_COLUMN_RDSNAME);

                if (!TextUtils.isEmpty(name)) {
                    stationName = name;
                } else if (!TextUtils.isEmpty(rdsName)) {
                    stationName = rdsName;
                }
            }
            cursor.close();
        }

        if (stationName == null && !TextUtils.isEmpty(mRdsStationName)) {
            stationName = mRdsStationName;
        }

        mNotification.setLatestEventInfo(this, stationName != null ? stationName : frequencyString,
                stationName != null ? frequencyString : "", mActivityIntent);
        startForeground(R.string.app_name, mNotification);

        updateFmStateBroadcast(true);

        /* fake a music state change to make the FM state appear on the lockscreen */
        StringBuilder sb = new StringBuilder();
        if (stationName != null) {
            sb.append(stationName);
            sb.append(" (");
            sb.append(frequencyString);
            sb.append(")");
        } else {
            sb.append(frequencyString);
        }
        updateMusicMetadata(getString(R.string.app_name), sb.toString(), true);
    }

    private void cancelStateIndicators() {
        stopForeground(true);
        updateFmStateBroadcast(false);
        updateMusicMetadata(null, null, false);
    }

    private void updateMusicMetadata(String artist, String title, boolean active) {
        Intent intent = new Intent(ACTION_MUSIC_META_CHANGED);
        intent.putExtra("artist", artist);
        intent.putExtra("track", title);
        intent.putExtra("playing", active);
        sendStickyBroadcast(intent);
    }

    private void updateFmStateBroadcast(boolean active) {
        Intent intent = new Intent("com.android.media.intent.action.FM_STATE_CHANGED");
        intent.putExtra("active", active);
        sendStickyBroadcast(intent);
    }

    private boolean isHeadsetConnected() {
        return mHeadsetState == STEREO_HEADSET || mHeadsetState == STEREO_HEADSET2 || mHeadsetState == OMTP_HEADSET;
    }

    private void handleHeadsetChange(int state) {
        mHeadsetState = state;
        boolean available = isHeadsetConnected();

        Log.v(TAG, "Headset change: state " + state + " -> available " + available);
        if (available) {
            if (isMusicPlaying()) {
                Intent i = new Intent(ACTION_MUSIC_SERVICE_COMMAND);
                i.putExtra(MUSIC_EXTRA_COMMAND, MUSIC_PAUSE);
                sendBroadcast(i);
            }
        } else {
            Message msg = Message.obtain(mHandler, MSG_SHOW_NOTICE, R.string.fmradio_no_headset, 0, null);
            mHandler.sendMessage(msg);
            mHandler.sendEmptyMessage(MSG_SHUTDOWN);
        }
    }

    private void notifyEnableChangeComplete(boolean enabled, boolean success) {
        if (mCallbacks != null) {
            try {
                if (enabled) {
                    mCallbacks.onEnabled(success);
                } else {
                    mCallbacks.onDisabled();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Could not report enable state", e);
            }
        }
    }

    private void notifyTuneResult(boolean success) {
        if (mCallbacks != null) {
            try {
                mCallbacks.onTuneChanged(success);
            } catch (RemoteException e) {
                Log.e(TAG, "Could not report tune change", e);
            }
        }
    }

    private void notifySeekResult(boolean success) {
        if (mCallbacks != null) {
            try {
                mCallbacks.onSeekFinished(success, mCurFreq);
            } catch (RemoteException e) {
                Log.e(TAG, "Could not report seek result", e);
            }
        }
    }

    private void notifyRdsUpdate() {
        if (mCallbacks != null) {
            try {
                mCallbacks.onRdsDataChanged(mCurFreq, mRdsStationName, mRdsRadioText, mRdsPTYValue);
            } catch (RemoteException e) {
                Log.e(TAG, "Could not report RDS change", e);
            }
        }
    }

    private void handleTuneComplete(boolean success, int frequency) {
        Log.v(TAG, "FM tune complete, success " + success + " frequency " + frequency);
        mCurFreq = frequency;
        if (!success) {
            notifyTuneResult(false);
        } else if (!mReady) {
            int lastFreq = Preferences.getLastFrequency(FMRadioPlayerService.this);
            if (mCurFreq == lastFreq) {
                Log.v(TAG, "Finished first tuning, initializing volume");
                try {
                    mIFMRadioService.getAudioMode();
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed getting audio mode", e);
                    mAudioMode = 0;
                    notifyTuneResult(false);
                }
                setFMVolume(Preferences.getVolume(FMRadioPlayerService.this));
                updateStateIndicators();
            } else {
                Log.v(TAG, "Initializing tuning to last frequency " + lastFreq);
                try {
                    mIFMRadioService.tune(lastFreq);
                } catch (RemoteException e) {
                    notifyTuneResult(false);
                }
            }
        } else {
            updateStateIndicators();
            notifyTuneResult(true);
        }
    }

    private boolean enableRds() {
        boolean result = false;

        try {
            mUSBand = mIFMRadioService.getBand() == 0;
        } catch (RemoteException e) {
            Log.e(TAG, "Could not determine FM radio band", e);
        }

        Log.v(TAG, "Enabling RDS in " + (mUSBand ? "RBDS" : "RDS") + " mode");
        try {
            result = mIFMRadioService.setRdsEnable(true, mUSBand ? 1 : 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Enabling RDS failed", e);
        }

        return result;
    }
}
