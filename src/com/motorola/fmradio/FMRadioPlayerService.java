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
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
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
    public static final String ACTION_FM_COMMAND = "com.motorola.fmradio.SERVICE_COMMAND";

    public static final String EXTRA_COMMAND = "command";
    public static final String COMMAND_TOGGLE_MUTE = "togglemute";
    public static final String COMMAND_NEXT = "next";
    public static final String COMMAND_PREV = "prev";

    public static int FM_ROUTING_HEADSET = 0;
    public static int FM_ROUTING_SPEAKER = 1;
    public static int FM_ROUTING_SPEAKER_ONLY = 2;

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
    private static final int MSG_SCAN_UPDATE = 4;
    private static final int MSG_SEEK_COMPLETE = 5;
    private static final int MSG_SCAN_COMPLETE = 6;
    private static final int MSG_ABORT_COMPLETE = 7;
    private static final int MSG_UPDATE_AUDIOMODE = 8;
    private static final int MSG_RDS_PS_UPDATE = 9;
    private static final int MSG_RDS_RT_UPDATE = 10;
    private static final int MSG_RDS_PTY_UPDATE = 11;
    private static final int MSG_RESTORE_AUDIO_AFTER_FOCUS_LOSS = 12;
    private static final int MSG_SET_ROUTING = 13;
    private static final int MSG_SHUTDOWN = 14;

    private static final int IDLE_DELAY = 10 * 1000;

    private IFMRadioService mIFMRadioService = null;
    private IFMRadioPlayerServiceCallbacks mCallbacks = null;

    /* flag indicating that the FM startup sequence is complete */
    private boolean mReady = false;
    /* flag indicating the current mute state */
    private boolean mMuted = false;
    /* flag indicating whether any client is bound to the service */
    private boolean mInUse = false;
    /* flag indicating whether we're bound to the HW service */
    private boolean mBound = false;
    /* flag indicating whether the hardware is powered */
    private boolean mPowerOn = false;
    /* flag indicating whether we've lost audio focus */
    private boolean mLostAudioFocus = false;
    /* flag indicating whether we're on the US band (important for handling RDS data) */
    private boolean mUSBand = false;

    private int mServiceStartId = -1;

    private int mHeadsetState = -1;
    private int mAudioMode = 0;
    private int mAudioRouting = FM_ROUTING_HEADSET;

    private AudioManager mAM;
    private Notification mNotification;
    private PendingIntent mActivityIntent;

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
            final Context context = FMRadioPlayerService.this;

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
                case 2: {
                    Message msg = Message.obtain(mHandler, MSG_SCAN_COMPLETE, status, 0, null);
                    mHandler.sendMessage(msg);
                    break;
                }
                case 3: {
                    Message msg = Message.obtain(mHandler, MSG_ABORT_COMPLETE, status, Integer.parseInt(value), null);
                    mHandler.sendMessage(msg);
                    break;
                }
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
                        mPowerOn = true;
                    }
                    break;
                case 10:
                    mPowerOn = false;
                    break;
               case 15: {
                    Message msg = Message.obtain(mHandler, MSG_UPDATE_AUDIOMODE, Integer.parseInt(value), 0, null);
                    mHandler.sendMessage(msg);

                    if (mReady || setSeekSensitivity(Preferences.getSeekSensitivityThreshold(context))) {
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
                        mAM.setParameters(LAUNCH_KEY + "=" + LAUNCH_VALUE_ON);
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
                case 25: {
                    Message msg = Message.obtain(mHandler, MSG_SCAN_UPDATE, Integer.parseInt(value), 0, null);
                    mHandler.sendMessage(msg);
                    break;
                }
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
            if (!isHeadsetConnected() && mAudioRouting == FM_ROUTING_SPEAKER) {
                return FM_ROUTING_SPEAKER_ONLY;
            }
            return mAudioRouting;
        }

        @Override
        public boolean powerOn() {
            Log.d(TAG, "Got FM radio power on request");
            if (mPowerOn) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyEnableChangeComplete(true, true);
                    }
                });
                return true;
            }

            boolean inAirplaneMode = Settings.System.getInt(
                    getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) == 1;

            if (inAirplaneMode && !Preferences.isAirplaneModeIgnored(FMRadioPlayerService.this)) {
                Message msg = Message.obtain(mHandler, MSG_SHOW_NOTICE,
                        R.string.fmradio_airplane_mode_enabled, 0, null);
                mHandler.sendMessage(msg);
                scheduleShutdown();
                return false;
            }

            Intent headsetIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
            int headsetState = headsetIntent != null ? headsetIntent.getIntExtra("state", 0) : -1;

            if (!handleHeadsetChange(headsetState)) {
                return false;
            }

            return startupFM();
        }

        @Override
        public void powerOff() {
            Log.d(TAG, "Got FM radio power off request");
            if (mReady) {
                mAM.setMode(AudioManager.MODE_NORMAL);
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
        public void setAudioRouting(int routing) {
            Log.d(TAG, "Got request for setting audio routing to " + routing);
            Message msg = Message.obtain(mHandler, MSG_SET_ROUTING, routing, 0, null);
            mHandler.sendMessage(msg);
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
                result = setFMFrequency(freq);
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
                case MSG_SCAN_UPDATE:
                    mCurFreq = msg.arg1;
                    resetRDSData();
                    updateStateIndicators();
                    if (mCallbacks != null) {
                        try {
                            mCallbacks.onScanUpdate(mCurFreq);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report scan update", e);
                        }
                    }
                    break;
                case MSG_SCAN_COMPLETE:
                    if (msg.arg1 != 0) {
                        resetRDSData();
                    }
                    if (mCallbacks != null) {
                        try {
                            mCallbacks.onScanFinished(msg.arg1 != 0, mCurFreq);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report scan result", e);
                        }
                    }
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
                case MSG_ABORT_COMPLETE:
                    mCurFreq = msg.arg2;
                    if (msg.arg1 == 0) {
                        notifyTuneResult(false);
                    } else if (mCallbacks != null) {
                        try {
                            mCallbacks.onAbortComplete(msg.arg2);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report abort complete", e);
                        }
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
                case MSG_RESTORE_AUDIO_AFTER_FOCUS_LOSS:
                    setFMMuteState(false);
                    audioPrepare(mAudioRouting == FM_ROUTING_HEADSET
                            ? FM_ROUTING_SPEAKER : FM_ROUTING_HEADSET);
                    audioPrepare(mAudioRouting);
                    setFMVolume(Preferences.getVolume(FMRadioPlayerService.this));
                    updateStateIndicators();
                    break;
                case MSG_SET_ROUTING:
                    if (msg.arg1 == FM_ROUTING_HEADSET || msg.arg1 == FM_ROUTING_SPEAKER) {
                        mAudioRouting = msg.arg1;
                        if (mReady) {
                            audioPrepare(mAudioRouting);
                        }
                    }
                    break;
                case MSG_SHUTDOWN:
                    if (!mBound && !mInUse) {
                        Log.d(TAG, "Shutting down FM radio player service");
                        stopSelf(mServiceStartId);
                    }
                    break;
            }
        }
    };

    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.v(TAG, "AudioFocus: received AUDIOFOCUS_LOSS, turning FM off");
                    mLostAudioFocus = true;
                    if (mBound) {
                        setFMMuteState(true);
                        shutdownFM();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.v(TAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT, muting");
                    mLostAudioFocus = true;
                    if (mReady) {
                        setFMMuteState(true);
                        updateStateIndicators();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.v(TAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                    mLostAudioFocus = false;
                    if (mReady) {
                        mHandler.sendEmptyMessageDelayed(MSG_RESTORE_AUDIO_AFTER_FOCUS_LOSS, 1000);
                    }
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();

        mAM = (AudioManager) getSystemService(AUDIO_SERVICE);

        scheduleShutdown();

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

        shutdownFM();
        restoreAudioRoute();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        mHandler.removeMessages(MSG_SHUTDOWN);
        mInUse = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind()");
        mHandler.removeMessages(MSG_SHUTDOWN);
        mInUse = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        mInUse = false;
        mCallbacks = null;
        stopSelf(mServiceStartId);
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mServiceStartId = startId;

        if (intent != null && TextUtils.equals(intent.getAction(), ACTION_FM_COMMAND) && mReady) {
            String command = intent.getStringExtra(EXTRA_COMMAND);
            if (COMMAND_TOGGLE_MUTE.equals(command)) {
                setFMMuteState(!mMuted);
                updateStateIndicators();
            } else if (COMMAND_NEXT.equals(command)) {
                handlePrevNextButton(true);
            } else if (COMMAND_PREV.equals(command)) {
                handlePrevNextButton(false);
            }
        }

        scheduleShutdown();
        return START_STICKY;
    }

    private void audioPrepare(int routing) {
        final String route = routing == FM_ROUTING_SPEAKER ?
                ROUTING_VALUE_SPEAKER : ROUTING_VALUE_HEADSET;

        Log.d(TAG, "Setting FM audio routing to " + route);
        mAM.setParameters(ROUTING_KEY + "="  + route);
    }

    private void restoreAudioRoute() {
        mAM.setParameters(LAUNCH_KEY + "=" + LAUNCH_VALUE_OFF);
        mAM.setMode(AudioManager.MODE_NORMAL);
    }

    private boolean startupFM() {
        if (mBound) {
            return true;
        }

        mBound = bindService(new Intent("com.motorola.android.fmradio.FMRADIO_SERVICE"), mConnection, 1);
        if (!mBound) {
            Log.w(TAG, "Powering on FM radio failed");
            mHandler.sendEmptyMessage(MSG_SHUTDOWN);
            return false;
        }

        mAM.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_FM, AudioManager.AUDIOFOCUS_GAIN);
        setMediaButtonReceiverEnabled(true);
        registerBroadcastReceiver();
        registerObserver();

        return true;
    }

    private void shutdownFM() {
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
        setMediaButtonReceiverEnabled(false);
        mAM.abandonAudioFocus(mAudioFocusListener);

        if (mReady) {
            restoreAudioRoute();
        }

        stopForeground(true);
        updateFmStateBroadcast(false);
        updateMusicMetadata(null, null, false);
        notifyEnableChangeComplete(false, true);
        scheduleShutdown();
    }

    private void scheduleShutdown() {
        Message msg = mHandler.obtainMessage(MSG_SHUTDOWN);
        mHandler.removeMessages(MSG_SHUTDOWN);
        mHandler.sendMessageDelayed(msg, IDLE_DELAY);
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

    private void registerBroadcastReceiver() {
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
                    if (state != 0 && !Preferences.isAirplaneModeIgnored(context)) {
                        FMUtil.showNoticeDialog(context, R.string.fmradio_airplane_mode_enabled);
                        mHandler.sendEmptyMessage(MSG_SHUTDOWN);
                    }
                } else if (action.equals(ACTION_AUDIOPATH_FREE)) {
                    Log.v(TAG, "Audio path is available again");
                    setFMMuteState(false);
                } else if (action.equals(ACTION_AUDIOPATH_BUSY)) {
                    Log.d(TAG, "Audio path is busy");
                    setFMMuteState(true);
                } else if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                    if (intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1) == AudioManager.STREAM_FM) {
                        int volume = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, 0);
                        Log.d(TAG, "Received FM volume change intent, setting volume to " + volume);
                        Preferences.setVolume(FMRadioPlayerService.this, volume);
                        setFMVolume(volume);
                    }
                } else if (mReady && action.equals(SettingsActivity.ACTION_RSSI_UPDATED)) {
                    setSeekSensitivity(intent.getIntExtra(SettingsActivity.EXTRA_RSSI, -1));
                }
            }
        };

        Log.v(TAG, "Registering broadcast receiver");
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
        filter.addAction(ACTION_AUDIOPATH_FREE);
        filter.addAction(ACTION_AUDIOPATH_BUSY);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(SettingsActivity.ACTION_RSSI_UPDATED);
        registerReceiver(mReceiver, filter);
    }

    private void setMediaButtonReceiverEnabled(boolean enable) {
        int flag = enable
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        ComponentName component = new ComponentName(getPackageName(), FMMediaButtonReceiver.class.getName());

        getPackageManager().setComponentEnabledSetting(component, flag, PackageManager.DONT_KILL_APP);

        if (enable) {
            Log.d(TAG, "register media button receiver " + component);
            mAM.registerMediaButtonEventReceiver(component);
        } else {
            mAM.unregisterMediaButtonEventReceiver(component);
        }
    }

    private void resetRDSData() {
        mRdsStationName = null;
        mRdsPTYValue = 0;
        mRdsRadioText = null;
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
            mIFMRadioService.setMute(mute || mLostAudioFocus ? 1 : 0);
            mMuted = mute;
        } catch (RemoteException e) {
            Log.e(TAG, "Setting FM mute state failed", e);
        }
    }

    private boolean setFMFrequency(int frequency) {
        try {
            return mIFMRadioService.tune(frequency);
        } catch (RemoteException e) {
            Log.e(TAG, "Tuning failed", e);
        }
        return false;
    }

    private Cursor getCurrentPresetCursor() {
        Cursor cursor = getContentResolver().query(Channels.CONTENT_URI, FMUtil.PROJECTION,
                Channels.FREQUENCY + "=?", new String[] { String.valueOf(mCurFreq) }, null);

        if (cursor == null) {
            return null;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    private int getNextPresetFrequency(int preset, boolean searchUpward) {
        Cursor cursor = getContentResolver().query(Channels.CONTENT_URI, FMUtil.PROJECTION, null, null, null);
        int bestFrequency = -1, bestPreset = searchUpward ? Integer.MAX_VALUE : Integer.MIN_VALUE;

        if (cursor == null) {
            return -1;
        }

        int count = cursor.getCount();

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            int currFreq = cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ);
            int currPreset = cursor.getInt(FMUtil.CHANNEL_COLUMN_ID);

            if (currFreq == 0 || currPreset == preset) {
                cursor.moveToNext();
                continue;
            }

            if (searchUpward && currPreset < preset) {
                currPreset += count;
            } else if (!searchUpward && currPreset > preset) {
                currPreset -= count;
            }

            int diff = Math.abs(currPreset - preset);
            int bestDiff = Math.abs(bestPreset - preset);
            boolean better = diff < bestDiff;

            if (better) {
                bestFrequency = currFreq;
                bestPreset = currPreset;
            }
            cursor.moveToNext();
        }

        cursor.close();
        return bestFrequency;
    }

    private void updateStateIndicators() {
        if (mCurFreq == 0) {
            return;
        }

        final String frequencyString = FMUtil.formatFrequency(this, mCurFreq);
        String stationName = null;
        Cursor cursor = getCurrentPresetCursor();

        if (cursor != null) {
            String name = cursor.getString(FMUtil.CHANNEL_COLUMN_NAME);
            String rdsName = cursor.getString(FMUtil.CHANNEL_COLUMN_RDSNAME);

            if (!TextUtils.isEmpty(name)) {
                stationName = name;
            } else if (!TextUtils.isEmpty(rdsName)) {
                stationName = rdsName;
            }
            cursor.close();
        }

        if (stationName == null && !TextUtils.isEmpty(mRdsStationName)) {
            stationName = mRdsStationName;
        }

        /* TODO: add hint if muted? */
        mNotification.setLatestEventInfo(this, stationName != null ? stationName : frequencyString,
                stationName != null ? frequencyString : "", mActivityIntent);
        startForeground(R.string.app_name, mNotification);

        updateFmStateBroadcast(true);

        /* fake a music state change to make the FM state appear on the lockscreen */
        if (mReady && !mMuted) {
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
        } else {
            updateMusicMetadata(null, null, false);
        }
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

    private boolean handleHeadsetChange(int state) {
        mHeadsetState = state;
        boolean available = isHeadsetConnected();

        Log.v(TAG, "Headset change: state " + state + " -> available " + available);
        if (!available && Preferences.isHeadsetRequired(this)) {
            Message msg = Message.obtain(mHandler, MSG_SHOW_NOTICE, R.string.fmradio_no_headset, 0, null);
            mHandler.sendMessage(msg);
            shutdownFM();
            return false;
        } else if (!available) {
            Message msg = Message.obtain(mHandler, MSG_SET_ROUTING, FM_ROUTING_SPEAKER, 0, null);
            mHandler.sendMessage(msg);
        }

        return true;
    }

    private void handlePrevNextButton(boolean next) {
        boolean shouldSeek = !Preferences.mediaButtonPrevNextSwitchesPresets(this);

        if (!shouldSeek) {
            Cursor cursor = getCurrentPresetCursor();
            if (cursor != null) {
                int currentPreset = cursor.getInt(FMUtil.CHANNEL_COLUMN_ID);
                int nextPresetFreq = getNextPresetFrequency(currentPreset, next);
                cursor.close();

                if (nextPresetFreq >= 0) {
                    setFMFrequency(nextPresetFreq);
                } else {
                    shouldSeek = true;
                }
            } else {
                shouldSeek = true;
            }
        }

        if (shouldSeek) {
            Message msg = Message.obtain(mHandler, MSG_SEEK_CHANNEL, next ? 0 : 1, 0, null);
            mHandler.sendMessage(msg);
        }
    }

    private void handleTuneComplete(boolean success, int frequency) {
        Log.v(TAG, "FM tune complete, success " + success + " frequency " + frequency);
        mCurFreq = frequency;
        resetRDSData();
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
                if (!setFMFrequency(lastFreq)) {
                    notifyTuneResult(false);
                }
            }
        } else {
            updateStateIndicators();
            notifyTuneResult(true);
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
                mCallbacks.onTuneChanged(success, mCurFreq);
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

    private boolean setSeekSensitivity(int value) {
        if (value < 0) {
            return false;
        }
        Log.d(TAG, "Setting RSSI level " + value);
        try {
            return mIFMRadioService.setRSSI(value);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not set RSSI", e);
        }
        return false;
    }
}
