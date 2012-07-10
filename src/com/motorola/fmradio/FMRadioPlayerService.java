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
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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
    private static final String TAG = "JAVA:FMRadioPlayerService";

    public static final String ACTION_AUDIOPATH_BUSY = "android.intent.action.AudioPathBusy";
    public static final String ACTION_AUDIOPATH_FREE = "android.intent.action.AudioPathFree";
    private static final String ACTION_FMRADIO_COMMAND = "com.motorola.fmradio.command";
    public static final String ACTION_MUSIC_META_CHANGED = "com.android.music.metachanged";
    public static final String ACTION_MUSIC_PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
    public static final String ACTION_MUSIC_SERVICE_COMMAND = "com.android.music.musicservicecommand";

    public static int AudioManager_ROUTE_FM_HEADSET = 0;
    public static int AudioManager_ROUTE_FM_SPEAKER = 1;

    public static final String ROUTING_KEY = "FM_routing";
    public static final String ROUTING_VALUE_HEADSET = "DEVICE_OUT_WIRED_HEADPHONE";
    public static final String ROUTING_VALUE_SPEAKER = "DEVICE_OUT_SPEAKER";

    public static final String LAUNCH_KEY = "FM_launch";
    public static final String LAUNCH_VALUE_OFF = "off";
    public static final String LAUNCH_VALUE_ON = "on";

    private static final String COMMAND = "command";
    public static final String CMDSTART = "start";
    private static final String STOP = "stop";
    private static final String MUTE = "mute";
    public static final String MUSIC_COMMAND = "command";
    public static final String MUSIC_PAUSE = "pause";
    private static final String UNMUTE = "unmute";

    protected static final int STEREO_HEADSET = 1;
    protected static final int STEREO_HEADSET2 = 2;
    protected static final int OMTP_HEADSET = 3;

    public static final int SERVICE_UNINIT = -1;
    public static final int SERVICE_INIT = 0;
    public static final int SERVICE_INITED = 1;
    public static final int SERVICE_READY = 2;
    public static final int SERVICE_STANDBY = 3;

    private static final int MSG_TUNE_CHANNEL = 1;
    private static final int MSG_SEEK_CHANNEL = 2;
    private static final int MSG_SET_VOLUME = 3;

    private IFMRadioService mIFMRadioService = null;
    private FMServiceStateBase mServiceState = null;
    private IFMRadioPlayerServiceCallbacks mCallbacks = null;

    private AudioManager am;
    private Notification mNotification;
    private PendingIntent mActivityIntent;

    private boolean mIgnoreRdsEvent = false;
    private int mHeadsetState = -1;
    private boolean mTuneFirst = false;
    private boolean misPowerOn = false;
    private boolean mBound = false;

    private int mAudioMode = 0;
    private int mAudioRouting = AudioManager_ROUTE_FM_HEADSET;
    private int mCurFreq = 0;
    private String mRdsTextDisplay = "";
    private String mRdsTextID = "";
    private int mRdsValuePTY = 0;
    private WakeLock mWakeLock;

    private BroadcastReceiver mReceiver = null;
    private ContentObserver mObserver = null;

    private class FMServiceStateBase {
        protected int curState;

        public FMServiceStateBase() {
            curState = SERVICE_UNINIT;
        }

        protected int curServiceState() {
            return curState;
        }

        protected int getAudioMode() {
            Log.w(TAG, "FMServiceStateBase:getAudioMode()");
            return mAudioMode;
        }

        protected int getAudioRouting() {
            Log.w(TAG, "FMServiceStateBase:getAudioRouting()");
            return mAudioRouting;
        }

        public void ignoreRdsEvent(boolean isIgnoreRds) {
            mIgnoreRdsEvent = isIgnoreRds;
        }

        protected boolean isPowerOn() {
            Log.d(TAG, "FMServiceStateBase:FM chip power state... Power is : " + misPowerOn);
            return misPowerOn;
        }

        protected boolean powerOff() {
            Log.d(TAG, "FMServiceStateBase:close() rejected ... SM State is : " + curState);
            return false;
        }

        protected boolean powerOn(int freq) {
            Log.d(TAG, "FMServiceStateBase:powerOn() rejected ... SM State is : " + curState);
            return false;
        }

        protected boolean prepare() {
            Log.d(TAG, "FMServiceStateBase:prepare() rejected ... SM State is : " + curState);
            return false;
        }

        protected boolean scan() {
            Log.d(TAG, "FMServiceStateBase:scan() rejected ... SM State is : " + curState);
            return false;
        }

        protected boolean seek(int freq, int direction) {
            Log.d(TAG, "FMServiceStateBase:seek() rejected ... SM State is : " + curState);
            return false;
        }

        protected boolean setAudioRouting(int routing) {
            Log.w(TAG, "FMServiceStateBase:setAudioRouting(" + routing + ")");
            return false;
        }

        protected boolean stopScan() {
            Log.d(TAG, "FMServiceStateBase:stopScan() rejected ... SM State is : " + curState);
            return false;
        }

        protected boolean stopSeek() {
            Log.d(TAG, "FMServiceStateBase:stopSeek() rejected ... SM State is : " + curState);
            return false;
        }

        protected boolean tune (int freq) {
            Log.d(TAG, "FMServiceStateBase:tune() rejected ... SM State is : " + curState);
            return false;
        }
    }

    private class FMStateUNInit extends FMServiceStateBase {
        public FMStateUNInit() {
            super();
            curState = SERVICE_UNINIT;
        }

        @Override
        protected boolean isPowerOn() {
            Log.d(TAG, "FMStateUNInit:isPowerOn(), return misPowerOn value to UI");
            return misPowerOn;
        }

        @Override
        protected boolean powerOn(int freq) {
            Log.d(TAG, "FMStateUNInit:powerOn(): Power on fmradio device");
            if (misPowerOn) {
                return false;
            }
            mBound = bindService(new Intent("com.motorola.android.fmradio.FMRADIO_SERVICE"), mConnection, 1);
            return mBound;
        }
    }

    private class FMStateInitED extends FMServiceStateBase {
        public FMStateInitED() {
            super();
            curState = SERVICE_INITED;
        }

        @Override
        protected boolean isPowerOn() {
            Log.d(TAG, "FMStateInitED:isPowerOn(), return misPowerOn value to UI");
            return misPowerOn;
        }

        @Override
        protected boolean powerOff() {
            Log.d(TAG, "FMStateInitED:close(), close fmradio server stack first");
            if (mBound) {
                unbindService(mConnection);
                mBound = false;
            }
            return true;
        }

        @Override
        protected boolean prepare() {
            Log.d(TAG, "FMStateInitED:prepare(), setBand and setDemphersize for fmradio BT device");
            return true;
        }
    }

    private class FMStateReady extends FMServiceStateBase {
        public FMStateReady() {
            super();
            curState = SERVICE_READY;
        }

        @Override
        protected int getAudioMode() {
            Log.w(TAG, "FMStateReady:getAudioMode()");
            return mAudioMode;
        }

        @Override
        protected int getAudioRouting() {
            Log.w(TAG, "FMStateReady:getAudioRouting()");
            return mAudioRouting;
        }

        @Override
        protected boolean isPowerOn() {
            Log.d(TAG, "FMStateReady:isPowerOn(), return misPowerOn value to UI");
            return misPowerOn;
        }

        @Override
        protected boolean powerOff() {
            Log.d(TAG, "FMStateReady:close(), close fmradio server stack first");
            am.setMode(AudioManager.MODE_NORMAL);
            if (mBound) {
                unbindService(mConnection);
                mBound = false;
            }
            return true;
        }

        @Override
        protected boolean powerOn(int freq) {
            Log.d(TAG, "FMStateReady:powerOn(), Already opened, just return OK");
            return true;
        }

        @Override
        protected boolean scan() {
            try {
                return mIFMRadioService.scan();
            } catch (RemoteException e) {
                Log.e(TAG, "tune Failed: " + e.getMessage());
            }
            return false;
        }

        @Override
        protected boolean seek(int freq, int direction) {
            Log.d(TAG, "FMStateReady:seek(), seek start = " + freq + ", seek direction = " + direction);
            Message msg = Message.obtain(mHandler, MSG_SEEK_CHANNEL, Integer.valueOf(direction));
            mHandler.sendMessage(msg);
            return true;
        }

        @Override
        protected boolean setAudioRouting(int routing) {
            Log.w(TAG, "FMStateReady:setAudioRouting(" + routing + ")");
            mAudioRouting = routing;
            audioPrepare(routing);
            return true;
        }

        @Override
        protected boolean stopScan() {
            Log.d(TAG, "FMStateReady:stopScan(), Abort can");
            try {
                return mIFMRadioService.stopScan();
            } catch (RemoteException e) {
                Log.e(TAG, "stopScan Failed: " + e.getMessage());
            }
            return false;
        }

        @Override
        protected boolean stopSeek() {
            Log.d(TAG, "FMStateReady:stopSeek(), Abort seek");
            try {
                return mIFMRadioService.stopSeek();
            } catch (RemoteException e) {
                Log.e(TAG, "stopSeek Failed: " + e.getMessage());
            }
            return false;
        }

        @Override
        protected boolean tune(int freq) {
            boolean result = false;
            try {
                result = mIFMRadioService.tune(freq);
            } catch (RemoteException e) {
                Log.e(TAG, "tune Failed: " + e.getMessage());
            }
            if (result) {
                resetRDSData();
            }
            return result;
        }
    }

    protected ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "connect to service");
            mIFMRadioService = IFMRadioService.Stub.asInterface(service);
            try {
                mIFMRadioService.registerCallback(mCallback);
                Log.v(TAG, "register callback");
            } catch (RemoteException e) {
                Log.e(TAG, "registerCallback Failed: " + e.getMessage());
            }
            Log.w(TAG, "Open fmradio stack succeed!");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            try {
                mIFMRadioService.unregisterCallback(mCallback);
                Log.v(TAG, "unregister callback");
            } catch (RemoteException e) {
                Log.e(TAG, "unregisterCallback Failed: " + e.getMessage());
            }

            mIFMRadioService = null;
            Log.w(TAG, "Power off fmradio device cmd complete!");
        }
    };

    protected IFMRadioServiceCallback mCallback = new IFMRadioServiceCallback.Stub() {
        @Override
        public void onCommandComplete(int cmd, int status, String value) throws RemoteException {
            switch (cmd) {
                case 0:
                    mCurFreq = Integer.parseInt(value);
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_TUNE_COMPLETE = " + status + " frequency " + value);
                    if (status == 0) {
                        notifyTuneResult(false);
                    } else if (mTuneFirst) {
                        updateStateIndicators();
                        notifyTuneResult(true);
                        Log.w(TAG, "OnCommandCompleteListener : fmradio set frequency succeed!");
                    } else {
                        int savedtune = Preferences.getLastFrequency(FMRadioPlayerService.this);
                        if (mCurFreq == savedtune) {
                            Log.w(TAG, "This is first tune, need to set volume, unmute.");
                            try {
                                mIFMRadioService.getAudioMode();
                            } catch (RemoteException e) {
                                Log.e(TAG, "getAudioMode Failed: " + e.getMessage());
                                mAudioMode = 0;
                                notifyTuneResult(false);
                            }
                            try {
                                int vol = Preferences.getVolume(FMRadioPlayerService.this);
                                mIFMRadioService.setVolume(vol);
                            } catch (RemoteException e) {
                                Log.e(TAG, "setVolume Failed: " + e.getMessage());
                            }
                            updateStateIndicators();
                        } else {
                            Log.w(TAG, "Need to re-tune to last remembered value.");
                            try {
                                mIFMRadioService.tune(savedtune);
                            } catch (RemoteException e) {
                                notifyTuneResult(false);
                            }
                        }
                    }
                    break;
                case 1:
                    int preFreq = mCurFreq;
                    mCurFreq = Integer.parseInt(value);
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SEEK_COMPLETE = " + status);
                    Log.d(TAG, "OnCommandCompleteListener : seek completed, frequency = " + mCurFreq);
                    resetRDSData();
                    notifySeekResult(true);
                    if (preFreq != mCurFreq) {
                        updateStateIndicators();
                    }
                    break;
                case 2:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SCAN_COMPLETE = " + status);
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
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_ABORT_COMPLETE = " + status);
                    if (status == 0) {
                        notifyTuneResult(false);
                    } else if (mCallbacks != null) {
                        try {
                            mCallbacks.onAbortComplete();
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report abort complete", e);
                        }
                    }
                    break;
                case 4:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_PS_AVAILABLE = " + status);
                    Log.w(TAG, "PS value = " + value);
                    if (mIgnoreRdsEvent) {
                        Log.w(TAG, "RDS information was ignored by UI.");
                    } else {
                        String newName = mIFMRadioService.getRDSStationName();
                        if (!TextUtils.equals(mRdsTextID, newName)) {
                            mRdsTextID = newName;
                            updateStateIndicators();
                            notifyRdsUpdate();
                        }
                    }
                    break;
                case 5:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_RT_AVAILABLE = " + status);
                    Log.w(TAG, "RT value = " + value);
                    if (mIgnoreRdsEvent) {
                        Log.w(TAG, "RDS information was ignored by UI.");
                    } else if (!TextUtils.equals(mRdsTextDisplay, value)) {
                        mRdsTextDisplay = value;
                        notifyRdsUpdate();
                    }
                    break;
                case 6:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_PI_AVAILABLE = " + status);
                    Log.w(TAG, "PI value = " + value);
                    if (mIgnoreRdsEvent) {
                        Log.w(TAG, "RDS information was ignored by UI.");
                    } else if (mIFMRadioService.getBand() == 0) {
                        Log.w(TAG, "US Band: Get Station Call Name and send to UI layer");
                        String newName = mIFMRadioService.getRDSStationName();
                        if (!TextUtils.equals(mRdsTextID, newName)) {
                            mRdsTextID = mIFMRadioService.getRDSStationName();
                            updateStateIndicators();
                            notifyRdsUpdate();
                        }
                    }
                    break;
                case 7:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_PTY_AVAILABLE = " + status);
                    Log.w(TAG, "PTY value = " + value);
                    if (mIgnoreRdsEvent) {
                        Log.w(TAG, "RDS information was ignored by UI.");
                    } else {
                        int newPty = Integer.parseInt(value);
                        if (mIFMRadioService.getBand() == 0) {
                            newPty += 32;
                        }
                        if (mRdsValuePTY != newPty) {
                            Log.w(TAG, "PTY value after adjusting for band = " + mRdsValuePTY);
                            notifyRdsUpdate();
                        }
                    }
                    break;
                case 8:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_RTPLUS_AVAILABLE = " + status);
                    Log.w(TAG, "RTPLUS value = " + value);
                    break;
                case 9:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_ENABLE_COMPLETE = " + status);
                    if (status == 0) {
                        notifyEnableChangeComplete(true, false);
                    } else {
                        misPowerOn = true;
                        Log.w(TAG, "Bind to FMRadioService success!");
                    }
                    break;
                case 10:
                    Log.w(TAG, "Power off fmradio device cmd complete! = " + status);
                    misPowerOn = false;
                    break;
               case 15:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_GET_AUDIOMODE_DONE = " + status);
                    mAudioMode = Integer.parseInt(value);
                    if (!mTuneFirst) {
                        int band = mIFMRadioService.getBand();
                        boolean result;
                        if (band == 0) {
                            Log.w(TAG, "enabling RDS in RBDS mode");
                            try {
                                result = mIFMRadioService.setRdsEnable(true, 1);
                            } catch (RemoteException e) {
                                Log.e(TAG, "enableRDS Failed: " + e.getMessage());
                                result = false;
                            }
                        } else {
                            Log.w(TAG, "enabling RDS in RDS mode");
                            try {
                                result = mIFMRadioService.setRdsEnable(true, 0);
                            } catch (RemoteException e) {
                                Log.e(TAG, "enableRDS Failed: " + e.getMessage());
                                result = false;
                            }
                        }
                        if (!result) {
                            notifyTuneResult(false);
                        }
                    }
                    break;
                case 20:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_ENABLE_RDS_DONE = " + status);
                    resetRDSData();
                    if (!mTuneFirst) {
                        mTuneFirst = true;
                        Log.w(TAG, "Complete FM Radio PowerOn Sequence Succeeded!");
                        TelephonyManager mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        if (mTelephonyManager.getCallState() != 2) {
                            Log.d(TAG, "ACTION_PHONE_STATE_CHANGED CALL_STATE_OFFHOOK don\'t start FM Audio");
                            am.setParameters(LAUNCH_KEY + "=" + LAUNCH_VALUE_ON);
                            am.setParameters(ROUTING_KEY + "=" + ROUTING_VALUE_HEADSET);
                        }
                        mServiceState = new FMStateInitED();
                        mServiceState.prepare();
                        mServiceState = new FMStateReady();
                        notifyEnableChangeComplete(true, true);
                    }
                    break;
                case 24:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_AUDIO_MODE_CHANGED = " + status);
                    mAudioMode = Integer.parseInt(value);
                    if (mCallbacks != null) {
                        try {
                            mCallbacks.onAudioModeChanged(mAudioMode);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not report audio mode change", e);
                        }
                    }
                    break;
                case 25:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SCANNING = " + status);
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
                case 23:
                    Log.w(TAG, "OnCommandCompleteListener : fmradio default cmd, value = " + value);
                    if (cmd == -1) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_NONE = " + status);
                    } else if (cmd == 19) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SET_BAND_DONE! = " + status);
                    } else if (cmd == 11) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_GET_AUDIOTYPE_DONE = " + status);
                    } else if (cmd == 12) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_GET_FREQ_DONE = " + status);
                    } else if (cmd == 13) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_GET_MUTE_DONE = " + status);
                    } else if (cmd == 14) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_GET_VOLUME_DONE = " + status);
                    } else if (cmd == 16) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_GET_RSSI_DONE = " + status);
                    } else if (cmd == 17) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SET_AUDIOMODE_DONE = " + status);
                    } else if (cmd == 18) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SET_AUDIOMUTE_DONE = " + status);
                    } else if (cmd == 21) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_DISABLE_RDS_DONE = " + status);
                    } else if (cmd == 22) {
                        Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SET_VOLUME_DONE = " + status);
                    }
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
        public int getAudioMode() {
            Log.w(TAG, "IFMRadioPlayerService.Stub : getAudioMode");
            return mServiceState.getAudioMode();
        }

        @Override
        public int getAudioRouting() {
            Log.w(TAG, "IFMRadioPlayerService.Stub : getAudioRouting");
            return mServiceState.getAudioRouting();
        }

        @Override
        public void ignoreRdsEvent(boolean isIgnoreRds) {
            mServiceState.ignoreRdsEvent(isIgnoreRds);
        }

        @Override
        public boolean isPowerOn() {
            Log.w(TAG, "IFMRadioPlayerService.Stub : isPowerOn");
            return mServiceState.isPowerOn();
        }

        @Override
        public boolean powerOff() {
            Log.w(TAG, "IFMRadioPlayerService.Stub : powerOff");
            return mServiceState.powerOff();
        }

        @Override
        public boolean powerOn(int freq) {
            Log.w(TAG, "IFMRadioPlayerService.Stub : powerOn");
            return mServiceState.powerOn(freq);
        }

        @Override
        public boolean scan() {
            Log.w(TAG, "IFMRadioPlayerService.Stub : scan");
            return mServiceState.scan();
        }

        @Override
        public boolean seek(int freq, int direction) {
            Log.w(TAG, "IFMRadioPlayerService.Stub : seek");
            return mServiceState.seek(freq, direction);
        }

        @Override
        public boolean setAudioRouting(int routing) {
            Log.w(TAG, "IFMRadioPlayerService.Stub : setAudioRouting");
            return mServiceState.setAudioRouting(routing);
        }

        @Override
        public boolean stopScan() {
            Log.w(TAG, "IFMRadioPlayerService.Stub : stopScan");
            return mServiceState.stopScan();
        }

        @Override
        public boolean stopSeek() {
            Log.w(TAG, "IFMRadioPlayerService.Stub : stopSeek");
            return mServiceState.stopSeek();
        }

        @Override
        public boolean tune(int freq) {
            Log.w(TAG, "IFMRadioPlayerService.Stub : tune");
            return mServiceState.tune(freq);
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TUNE_CHANNEL:
                    Log.d(TAG, "Serv-mHandler: ACTION_TUNE_CHANNEL");
                    try {
                        mIFMRadioService.tune((Integer) msg.obj);
                    } catch (RemoteException e) {
                        Log.e(TAG, "tune Failed: " + e.getMessage());
                        notifyTuneResult(false);
                    }
                    break;
                case MSG_SEEK_CHANNEL:
                    Log.d(TAG, "Serv-mHandler: ACTION_SEEK_CHANNEL");
                    try {
                        mIFMRadioService.seek((Integer) msg.obj);
                    } catch (RemoteException e) {
                        Log.e(TAG, "seek Failed: " + e.getMessage());
                        notifySeekResult(false);
                    }
                    break;
                case MSG_SET_VOLUME:
                    Log.d(TAG, "Serv-mHandler: ACTION_SET_VOLUME to " + msg.arg1);
                    try {
                        mIFMRadioService.setVolume(msg.arg1);
                    } catch (RemoteException e) {
                        Log.e(TAG, "setVolume Failed: " + e.getMessage());
                    }
                    break;
            }
        }
    };

    private void audioPrepare(int routing) {
        Log.d(TAG, "setRouting to headset in java FMRadioPlayer service! = ");
        if (routing == AudioManager_ROUTE_FM_SPEAKER) {
            am.setParameters(ROUTING_KEY + "="  + ROUTING_VALUE_SPEAKER);
        } else if (routing == AudioManager_ROUTE_FM_HEADSET) {
            am.setParameters(ROUTING_KEY + "=" + ROUTING_VALUE_HEADSET);
        }
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
                Log.d(TAG, "Received intent: " + action);
                if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                    int state = intent.getIntExtra("state", 0);
                    Log.d(TAG, "HEADSET is pluged in/out.");
                    handleHeadsetChange(state);
                } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                    int state = intent.getIntExtra("state", 0);
                    Log.v(TAG, "Got airplane mode change message, new state " + state);
                    if (state != 0) {
                        showNoticeDialog(R.string.fmradio_airplane_mode_enable_in_listen);
                        shutdown();
                    }
                } else if (action.equals(ACTION_AUDIOPATH_FREE)) {
                    Log.v(TAG, "Audio path is available again");
                    setFMMuteState(false);
                } else if (action.equals(ACTION_AUDIOPATH_BUSY)) {
                    Log.d(TAG, "Audio path is busy");
                    setFMMuteState(true);
                } else if (action.equals(ACTION_MUSIC_PLAYSTATE_CHANGED)) {
                    if (isMusicPlaying()) {
                        showNoticeDialog(R.string.fmradio_music_playing_in_listen);
                        shutdown();
                    }
                } else if (action.equals(ACTION_FMRADIO_COMMAND)) {
                    Log.d(TAG, "receive fmradio command");
                    String cmd = intent.getStringExtra(COMMAND);
                    Log.w(TAG, "receive ACTION_FMRADIO_COMMAND cmd=" + cmd);
                    if (cmd.equals(MUTE)) {
                        Log.d(TAG, "set fmradio to mute");
                        setFMMuteState(true);
                    } else if (cmd.equals(UNMUTE)) {
                        Log.d(TAG, "set fmradio to unmute");
                        setFMMuteState(false);
                    } else if (cmd.equals(STOP)) {
                        Log.d(TAG, "FM will exit for Video/audio player");
                        shutdown();
                    }
                    // XXX: CMD_START?
                } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                    String phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    Log.d(TAG, "ACTION_PHONE_STATE_CHANGED " + phoneState);
                    if (phoneState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "sleep Failed: " + e.getMessage());
                            Log.d(TAG, "FMSRV EXCEPTION:After ignore calling sleep 2500ms error.");
                        }
                        if (mAudioRouting == AudioManager_ROUTE_FM_HEADSET) {
                            audioPrepare(AudioManager_ROUTE_FM_SPEAKER);
                            audioPrepare(AudioManager_ROUTE_FM_HEADSET);
                        } else {
                            audioPrepare(AudioManager_ROUTE_FM_HEADSET);
                            audioPrepare(AudioManager_ROUTE_FM_SPEAKER);
                        }
                        setFMMuteState(false);
                        audioPrepare(mAudioRouting);
                        resetVolume();
                    } else {
                        setFMMuteState(true);
                    }
                } else if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                    if (intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1) == AudioManager.STREAM_FM) {
                        int volume = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, 0);
                        Log.d(TAG, "Received FM volume change intent, setting volume to " + volume);
                        Preferences.setVolume(FMRadioPlayerService.this, volume);
                        Message msg = Message.obtain(mHandler, MSG_SET_VOLUME, volume, 0, null);
                        mHandler.sendMessage(msg);
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
        filter.addAction(ACTION_FMRADIO_COMMAND);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(mReceiver, filter);
    }

    private void resetRDSData() {
        mRdsTextID = "";
        mRdsValuePTY = 0;
        mRdsTextDisplay = "";
    }

    private void resetVolume() {
        if (mReceiver == null) {
            return;
        }
        try {
            mIFMRadioService.setVolume(Preferences.getVolume(this));
        } catch (RemoteException e) {
            Log.e(TAG, "setVolume Failed: " + e.getMessage());
        }
    }

    private void restoreAudioRoute() {
        if (am != null) {
            am.setParameters(LAUNCH_KEY + "=" + LAUNCH_VALUE_OFF);
            am.setMode(AudioManager.MODE_NORMAL);
        }
    }

    private void showNoticeDialog(int id) {
        Toast ts = Toast.makeText(this, id, Toast.LENGTH_LONG);
        ts.setGravity(Gravity.CENTER, 0, 0);
        ts.show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind(), current service state " + mServiceState.curServiceState());

        if (isAirplaneModeOn()) {
            showNoticeDialog(R.string.fmradio_airplane_mode_enable_at_begin);
            shutdown();
            return null;
        }

        Intent headsetIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
        if (headsetIntent != null) {
            handleHeadsetChange(headsetIntent.getIntExtra("state", 0));
        }

        if (!isHeadsetConnected()) {
            return null;
        }

        if (mServiceState.curServiceState() == SERVICE_UNINIT) {
            Log.v(TAG, "Starting FM radio service");
            if (!mServiceState.powerOn(Preferences.getLastFrequency(this))) {
                Log.w(TAG, "Powering on FM radio failed");
                return null;
            }
            registerBroadcastListener();
            registerObserver();
            updateStateIndicators();
        }
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate() called");
        super.onCreate();
        am = (AudioManager) getSystemService(AUDIO_SERVICE);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(1, getClass().getName());
        mWakeLock.setReferenceCounted(false);

        Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.setComponent(new ComponentName("com.motorola.fmradio", "com.motorola.fmradio.FMRadioMain"));
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        mActivityIntent = PendingIntent.getActivity(this, 0, launchIntent, 0);
        mNotification = new Notification(R.drawable.fm_statusbar_icon, null, System.currentTimeMillis());
        mNotification.flags |= Notification.FLAG_ONGOING_EVENT;

        mServiceState = new FMStateUNInit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w(TAG, "onDestroy() called, curState = " + mServiceState.curServiceState());
        cancelStateIndicators();
        if (mReceiver != null) {
            Log.d(TAG, "unregister Receiver.");
            unregisterReceiver(mReceiver);
        }
        if (mObserver != null) {
            getContentResolver().unregisterContentObserver(mObserver);
        }
        if (mServiceState.curServiceState() == SERVICE_READY) {
            restoreAudioRoute();
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        mTuneFirst = false;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind() called");
        super.onUnbind(intent);

        if (mServiceState.curServiceState() != SERVICE_READY) {
            cancelStateIndicators();
            if (mReceiver != null) {
                Log.d(TAG, "unregister Receiver.");
                unregisterReceiver(mReceiver);
                mReceiver = null;
            }
        }
        return true;
    }

    private void setFMMuteState(boolean mute) {
        Log.d(TAG, "setFMMuteState (" + mute + ")");
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
            cursor.moveToFirst();

            String name = cursor.getString(FMUtil.CHANNEL_COLUMN_NAME);
            String rdsName = cursor.getString(FMUtil.CHANNEL_COLUMN_RDSNAME);

            if (!TextUtils.isEmpty(name)) {
                stationName = name;
            } else if (!TextUtils.isEmpty(rdsName)) {
                stationName = rdsName;
            }
            cursor.close();
        }

        if (stationName == null && !TextUtils.isEmpty(mRdsTextID)) {
            stationName = mRdsTextID;
        }

        mNotification.setLatestEventInfo(this, stationName != null ? stationName : frequencyString,
                stationName != null ? frequencyString : "", mActivityIntent);
        startForeground(R.string.fmradio_service_label, mNotification);

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
        int oldState = mHeadsetState;
        mHeadsetState = state;
        boolean available = isHeadsetConnected();

        Log.v(TAG, "Headset change: state " + state + " -> available " + available);
        if (available) {
            if (isMusicPlaying()) {
                Intent i = new Intent(ACTION_MUSIC_SERVICE_COMMAND);
                i.putExtra(MUSIC_COMMAND, MUSIC_PAUSE);
                sendBroadcast(i);
            }
        } else {
            showNoticeDialog(oldState < 0 ?
                    R.string.fmradio_no_headset_at_begin : R.string.fmradio_no_headset_in_listen);
            shutdown();
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
                mCallbacks.onRdsDataChanged(mCurFreq, mRdsTextID, mRdsTextDisplay, mRdsValuePTY);
            } catch (RemoteException e) {
                Log.e(TAG, "Could not report RDS change", e);
            }
        }
    }

    private void shutdown() {
        notifyEnableChangeComplete(false, true);
        restoreAudioRoute();
        stopSelf();
    }
}
