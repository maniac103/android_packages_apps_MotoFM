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
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.motorola.android.fmradio.IFMRadioService;
import com.motorola.android.fmradio.IFMRadioServiceCallback;

public class FMRadioPlayerService extends Service {
    private static final String TAG = "JAVA:FMRadioPlayerService";

    public static final String FM_ABORT_COMPLETE = "com.motorola.fmradio.abortcomplete";
    public static final String FM_AUDIO_MODE_CHANGED = "com.motorola.fmradio.audiomodechanged";
    public static final String FM_HW_ERROR_FRQ = "com.motorola.fmradio.freqerror";
    public static final String FM_HW_ERROR_UNKNOWN = "com.motorola.fmradio.hwerror";
    public static final String FM_OPEN_FAILED = "com.motorola.fmradio.openfailed";
    public static final String FM_OPEN_SUCCEED = "com.motorola.fmradio.opensucceed";
    public static final String FM_POWERON_SUCCESS = "com.motorola.fmradio.poweronsuccess";
    public static final String FM_QUIT = "com.motorola.fmradio.quit";
    public static final String FM_RDS_DATA_AVAILABLE = "com.motorola.fmradio.rdsdataavailable";
    public static final String FM_SCANNING = "com.motorola.fmradio.scanning";
    public static final String FM_SCAN_FAILED = "com.motorola.fmradio.scanfailed";
    public static final String FM_SCAN_SUCCEED = "com.motorola.fmradio.scansucceed";
    public static final String FM_SEEK_FAILED = "com.motorola.fmradio.seekfailed";
    public static final String FM_SEEK_SUCCEED = "com.motorola.fmradio.seeksucceed";
    public static final String FM_SEEK_SUCCEED_AND_REACHLIMIT = "com.motorola.fmradio.seeklimit";
    public static final String FM_TUNE_SUCCEED = "com.motorola.fmradio.tunesucceed";
    public static final String ACTION_AUDIOPATH_BUSY = "android.intent.action.AudioPathBusy";
    public static final String ACTION_AUDIOPATH_FREE = "android.intent.action.AudioPathFree";
    private static final String ACTION_FMRADIO_COMMAND = "com.motorola.fmradio.command";
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

    private static final int CLOSE_SERVICE = 1;
    private static final int HEADSET_PLUG_STATE_CHANGED_MSG = 2;
    private static final int AUDIO_PATH_FREE = 3;
    private static final int AUDIO_PATH_BUSY = 4;
    private static final int ACTION_POWERON_FMBT = 6;
    private static final int ACTION_AIRPLANE_MODE_CHANGED_MSG = 5;
    private static final int ACTION_POWEROFF_FMBT = 7;
    private static final int ACTION_INIT_FIRMWARE = 8;
    private static final int ACTION_SET_BAND = 9;
    private static final int ACTION_DEINIT_FIRMWARE = 11;
    private static final int ACTION_TUNE_CHANNEL = 12;
    private static final int ACTION_SEEK_CHANNEL = 13;
    private static final int ACTION_SET_VOLUME = 14;
    private static final int ACTION_SET_MUTE = 15;
    private static final int ACTION_GET_RDS_TEXT = 16;
    private static final int ACTION_GET_RDS_VALUE = 17;
    private static final int MUSIC_PLAYSTATE_CHANGED = 18;
    private static final int SERVICE_SELFSTOP = 19;

    private static String mRdsText = "";
    private static int mRdsValue = 0;

    private IFMRadioService mIFMRadioService = null;
    private FMServiceStateBase mServiceState = null;

    private AudioManager am;
    private NotificationManager mNM;

    private boolean isInitial = true;
    protected boolean mBindSucceed = false;
    private boolean mIgnoreRdsEvent = false;
    protected boolean mIsBGMode = false;
    private boolean mIsHeadsetPlugged = true;
    private boolean mTuneFirst = false;
    private boolean misMuted = false;
    private boolean misPowerOn = false;

    private int mAudioMode = 0;
    private int mAudioRouting = AudioManager_ROUTE_FM_HEADSET;
    private int mCurFreq = FMUtil.MIN_FREQUENCY;
    private int mPreFreq = FMUtil.MIN_FREQUENCY;
    private int mCurVolume = 0;
    protected int mHeadset = -1;
    private int mRdsStatus = 0;
    private String mRdsTextDisplay = "";
    private String mRdsTextID = "";
    private int mRdsValuePTY = 0;
    private int mSeekStartFreq = 0;

    private final Object mLock = new Object();
    private WakeLock mWakeLock;

    private BroadcastReceiver mReceiver = null;

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

        protected String getRdsText(int id) {
            return "";
        }

        protected int getRdsValue(int id) {
            return 0;
        }

        protected int getVolume() {
            Log.d(TAG, "FMServiceStateBase:getVolume() volume is : " + mCurVolume);
            return mCurVolume;
        }

        public void ignoreRdsEvent(boolean isIgnoreRds) {
            mIgnoreRdsEvent = isIgnoreRds;
        }

        protected boolean isMute() {
            Log.d(TAG, "FMServiceStateBase:setMute() ... Mute is : " + misMuted);
            return misMuted;
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

        public void setBGMode(boolean background) {
            mIsBGMode = background;
        }

        protected boolean setMute(int mode) {
            Log.d(TAG, "FMServiceStateBase:setMute() rejected ... SM State is : " + curState);
            return false;
        }

        protected boolean setVolume(int volume) {
            Log.d(TAG, "FMServiceStateBase:setVolume() rejected ... SM State is : " + curState);
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
            boolean ret = false;
            Log.d(TAG, "FMStateUNInit:powerOn(): Power on fmradio device");
            if (!misPowerOn) {
                ret = bindService(new Intent("com.motorola.android.fmradio.FMRADIO_SERVICE"), mConnection, 1);
            }
            return ret;
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
            unbindService(mConnection);
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
        protected String getRdsText(int id) {
            Log.d(TAG, "FMStateReady:getRdsText(): id = " + id);
            synchronized (mLock) {
                try {
                    Message msg = Message.obtain(mHandler, ACTION_GET_RDS_TEXT, Integer.valueOf(id));
                    mHandler.sendMessage(msg);
                } catch (Exception e) {
                }
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "wait Failed: " + e.getMessage());
                    Log.d(TAG, "FMStateReady:getRdsText() interrupted while trying to read RDS data");
                } catch (Exception e) {
                }
            }

            return mRdsText;
        }

        @Override
        protected int getRdsValue(int id) {
            Log.d(TAG, "FMStateReady:getRdsValue(): id = " + id);
            synchronized (mLock) {
                try {
                    Message msg = Message.obtain(mHandler, ACTION_GET_RDS_VALUE, Integer.valueOf(id));
                    mHandler.sendMessage(msg);
                } catch (Exception e) {
                }
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "wait Failed: " + e.getMessage());
                    Log.d(TAG, "FMStateReady:getRdsValue() interrupted while trying to red RDS data");
                } catch (Exception e) {
                }
            }

            return mRdsValue;
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
            unbindService(mConnection);
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
            Message msg = Message.obtain(mHandler, ACTION_SEEK_CHANNEL, Integer.valueOf(direction));
            mSeekStartFreq = freq;
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
        protected boolean setMute(int mode) {
            Log.d(TAG, "FMStateReady:setMute(): mode = " + mode);
            Message msg = Message.obtain(mHandler, ACTION_SET_MUTE, Integer.valueOf(mode));
            misMuted = mode == 1;
            mHandler.sendMessage(msg);
            return true;
        }

        @Override
        protected boolean setVolume(int volume) {
            Log.d(TAG, "FMStateReady:setVolume(), setVolume looped = " + volume);
            Message msg = Message.obtain(mHandler, ACTION_SET_VOLUME, Integer.valueOf(volume));
            mCurVolume = volume;
            mHandler.sendMessage(msg);
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
            try {
                return mIFMRadioService.tune(freq);
            } catch (RemoteException e) {
                Log.e(TAG, "tune Failed: " + e.getMessage());
            }
            return false;
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
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_TUNE_COMPLETE = " + status);
                    if (status == 0) {
                        notifyCmdResults(FM_HW_ERROR_FRQ, null);
                    } else if (mTuneFirst) {
                        notifyCmdResults(FM_TUNE_SUCCEED, null);
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
                                notifyCmdResults(FM_HW_ERROR_FRQ, null);
                            }
                            try {
                                int vol = Preferences.getVolume(FMRadioPlayerService.this);
                                mIFMRadioService.setVolume(vol);
                            } catch (RemoteException e) {
                                Log.e(TAG, "setVolume Failed: " + e.getMessage());
                            }
                        } else {
                            Log.w(TAG, "Need to re-tune to last remembered value.");
                            try {
                                mIFMRadioService.tune(savedtune);
                            } catch (RemoteException e) {
                                notifyCmdResults(FM_HW_ERROR_FRQ, null);
                            }
                        }
                    }
                    break;
                case 1:
                    mPreFreq = mCurFreq;
                    mCurFreq = Integer.parseInt(value);
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SEEK_COMPLETE = " + status);
                    if (mPreFreq == mCurFreq) {
                        if (mCurFreq == FMUtil.MAX_FREQUENCY || mCurFreq == FMUtil.MIN_FREQUENCY) {
                            status = 0;
                        }
                    }
                    resetRDSData();
                    if (status == 0) {
                        Log.d(TAG, "OnCommandCompleteListener : seek opt reach limit");
                        notifyCmdResults(FM_SEEK_SUCCEED_AND_REACHLIMIT, value);
                    } else {
                        Log.d(TAG, "OnCommandCompleteListener : seek completed, frequency = " + mCurFreq);
                        notifyCmdResults(FM_SEEK_SUCCEED, value);
                    }
                    break;
                case 2:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SCAN_COMPLETE = " + status);
                    if (status == 0) {
                        notifyCmdResults(FM_SCAN_FAILED, null);
                    } else {
                        Log.d(TAG, "OnCommandCompleteListener : scan completed");
                        resetRDSData();
                        notifyCmdResults(FM_SCAN_SUCCEED, value);
                    }
                    break;
                case 3:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_ABORT_COMPLETE = " + status);
                    if (status == 0) {
                        notifyCmdResults(FM_HW_ERROR_FRQ, null);
                    } else {
                        notifyCmdResults(FM_ABORT_COMPLETE, value);
                    }
                    break;
                case 4:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_PS_AVAILABLE = " + status);
                    Log.w(TAG, "PS value = " + value);
                    if (mIgnoreRdsEvent) {
                        Log.w(TAG, "RDS information was ignored by UI.");
                    } else {
                        mRdsStatus = cmd;
                        mRdsTextID = mIFMRadioService.getRDSStationName();
                        notifyCmdResults(FM_RDS_DATA_AVAILABLE, Integer.valueOf(mCurFreq));
                    }
                    break;
                case 5:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_RT_AVAILABLE = " + status);
                    Log.w(TAG, "RT value = " + value);
                    if (mIgnoreRdsEvent) {
                        Log.w(TAG, "RDS information was ignored by UI.");
                    } else {
                        mRdsStatus = cmd;
                        mRdsTextDisplay = value;
                        notifyCmdResults(FM_RDS_DATA_AVAILABLE, Integer.valueOf(mCurFreq));
                    }
                    break;
                case 6:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_PI_AVAILABLE = " + status);
                    Log.w(TAG, "PI value = " + value);
                    if (mIgnoreRdsEvent) {
                        Log.w(TAG, "RDS information was ignored by UI.");
                    } else if (mIFMRadioService.getBand() == 0) {
                        Log.w(TAG, "US Band: Get Station Call Name and send to UI layer");
                        mRdsStatus = 4;
                        mRdsTextID = mIFMRadioService.getRDSStationName();
                        notifyCmdResults(FM_RDS_DATA_AVAILABLE, Integer.valueOf(mCurFreq));
                    }
                    break;
                case 7:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_PTY_AVAILABLE = " + status);
                    Log.w(TAG, "PTY value = " + value);
                    if (mIgnoreRdsEvent) {
                        Log.w(TAG, "RDS information was ignored by UI.");
                    } else {
                        mRdsStatus = cmd;
                        if (mIFMRadioService.getBand() == 0) {
                            mRdsValuePTY = Integer.parseInt(value) + 32;
                        } else {
                            mRdsValuePTY = Integer.parseInt(value);
                        }
                        Log.w(TAG, "PTY value after adjusting for band = " + mRdsValuePTY);
                        notifyCmdResults(FM_RDS_DATA_AVAILABLE, Integer.valueOf(mCurFreq));
                    }
                    break;
                case 8:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_RDS_RTPLUS_AVAILABLE = " + status);
                    Log.w(TAG, "RTPLUS value = " + value);
                    break;
                case 9:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_ENABLE_COMPLETE = " + status);
                    if (status == 0) {
                        notifyCmdResults(FM_OPEN_FAILED, null);
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
                            notifyCmdResults(FM_HW_ERROR_FRQ, null);
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
                        Intent i = new Intent(FM_POWERON_SUCCESS);
                        sendBroadcast(i);
                    }
                    break;
                case 24:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_AUDIO_MODE_CHANGED = " + status);
                    mAudioMode = Integer.parseInt(value);
                    notifyCmdResults(FM_AUDIO_MODE_CHANGED, null);
                    break;
                case 25:
                    Log.w(TAG, "OnCommandCompleteListener : FM_CMD_SCANNING = " + status);
                    notifyCmdResults(FM_SCANNING, value);
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
                    if (status == 0) {
                        notifyCmdResults(FM_HW_ERROR_UNKNOWN, null);
                    }
                    break;
             }
        }
    };

    private final IFMRadioPlayerService.Stub mBinder = new IFMRadioPlayerService.Stub() {
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
        public String getRdsText(int id) {
            return mServiceState.getRdsText(id);
        }

        @Override
        public int getRdsValue(int id) {
            return mServiceState.getRdsValue(id);
        }

        @Override
        public int getServiceStatus() {
            Log.d(TAG, "IFMRadioPlayerService.Stub : getServiceStatus, state=" + mServiceState.curServiceState());
            return mServiceState.curServiceState();
        }

        @Override
        public int getVolume() {
            Log.d(TAG, "IFMRadioPlayerService.Stub : getVolume");
            return mServiceState.getVolume();
        }

        @Override
        public void ignoreRdsEvent(boolean isIgnoreRds) {
            mServiceState.ignoreRdsEvent(isIgnoreRds);
        }

        @Override
        public boolean isMute() {
            Log.w(TAG, "IFMRadioPlayerService.Stub : isMute");
            return mServiceState.isMute();
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
        public void setBGMode(boolean background) {
            mServiceState.setBGMode(background);
        }

        @Override
        public boolean setMute(int mode) {
            Log.w(TAG, "IFMRadioPlayerService.Stub : setMute");
            return mServiceState.setMute(mode);
        }

        @Override
        public boolean setVolume(int volume) {
            Log.w(TAG, "IFMRadioPlayerService.Stub : setVolume");
            return mServiceState.setVolume(volume);
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
                case 2:
                    Log.v(TAG, "handleMessage headset plug begin");
                    mHeadset = msg.arg1;
                    Log.v(TAG, "mHeadset = " + mHeadset);
                    mIsHeadsetPlugged = (mHeadset == STEREO_HEADSET || mHeadset == STEREO_HEADSET2 || mHeadset == OMTP_HEADSET);
                    Log.v(TAG, "mIsHeadsetPlugged = " + mIsHeadsetPlugged);
                    if (mIsHeadsetPlugged) {
                        if (isMusicPlaying()) {
                            Intent i = new Intent(ACTION_MUSIC_SERVICE_COMMAND);
                            i.putExtra(MUSIC_COMMAND, MUSIC_PAUSE);
                            sendBroadcast(i);
                        }
                        isInitial = false;
                        Log.w(TAG, "Headset is pluged in!");
                    } else {
                        if (isInitial) {
                            isInitial = false;
                            showNoticeDialog(R.string.fmradio_no_headset_at_begin);
                        } else {
                            Log.w(TAG, "Headset is pluged out while listening!");
                            showNoticeDialog(R.string.fmradio_no_headset_in_listen);
                        }
                        notifyCmdResults(FM_QUIT, null);
                    }
                    break;
                case 3:
                    Log.d(TAG, "Serv-mHandler: AUDIO_PATH_FREE");
                    try {
                        mIFMRadioService.setMute(1);
                    } catch (RemoteException e) {
                        Log.e(TAG, "setMute Failed: " + e.getMessage());
                    }
                    break;
                case 4:
                    Log.d(TAG, "Serv-mHandler: AUDIO_PATH_BUSY");
                    try {
                        mIFMRadioService.setMute(1);
                    } catch (RemoteException e) {
                        Log.e(TAG, "setMute Failed: " + e.getMessage());
                    }
                    break;
                case 5:
                    Log.d(TAG, "Send message to UI for airplane mode is true");
                    showNoticeDialog(R.string.fmradio_airplane_mode_enable_in_listen);
                    notifyCmdResults(FM_QUIT, null);
                    break;
                case 6:
                    Log.d(TAG, "Serv-mHandler: ACTION_POWERON_FMBT");
                    if (!misPowerOn) {
                        bindService(new Intent("com.motorola.android.fmradio.FMRADIO_SERVICE"), mConnection, 1);
                    }
                    break;
                case 7:
                    Log.d(TAG, "Serv-mHandler: ACTION_POWEROFF_FMBT");
                    am.setMode(AudioManager.MODE_NORMAL);
                    unbindService(mConnection);
                    break;
                case 8:
                    Log.d(TAG, "Serv-mHandler: ACTION_INIT_FIRMWARE");
                    if (!misPowerOn) {
                        bindService(new Intent("com.motorola.android.fmradio.FMRADIO_SERVICE"), mConnection, 1);
                    }
                    break;
                case 9:
                    Log.d(TAG, "Serv-mHandler: ACTION_SET_BAND");
                    if (mIsHeadsetPlugged) {
                        audioPrepare(AudioManager_ROUTE_FM_HEADSET);
                    } else {
                        audioPrepare(AudioManager_ROUTE_FM_SPEAKER);
                    }
                    try {
                        mIFMRadioService.setBand((Integer) msg.obj);
                    } catch (RemoteException e) {
                        Log.e(TAG, "setBand Failed: " + e.getMessage());
                    }
                    break;
                case 10:
                    break;
                case 11:
                    Log.d(TAG, "Serv-mHandler: ACTION_DEINIT_FIRMWARE");
                    am.setMode(AudioManager.MODE_NORMAL);
                    unbindService(mConnection);
                    break;
                case 12:
                    Log.d(TAG, "Serv-mHandler: ACTION_TUNE_CHANNEL");
                    try {
                        mIFMRadioService.tune((Integer) msg.obj);
                    } catch (RemoteException e) {
                        Log.e(TAG, "tune Failed: " + e.getMessage());
                        notifyCmdResults(FM_HW_ERROR_FRQ, null);
                    }
                    break;
                case 13:
                    Log.d(TAG, "Serv-mHandler: ACTION_SEEK_CHANNEL");
                    try {
                        mIFMRadioService.seek((Integer) msg.obj);
                    } catch (RemoteException e) {
                        Log.e(TAG, "seek Failed: " + e.getMessage());
                        notifyCmdResults(FM_SEEK_FAILED, null);
                    }
                    break;
                case 14:
                    Log.d(TAG, "Serv-mHandler: ACTION_SET_VOLUME to " + msg.obj);
                    try {
                        mIFMRadioService.setVolume((Integer) msg.obj);
                    } catch (RemoteException e) {
                        Log.e(TAG, "setVolume Failed: " + e.getMessage());
                    }
                    break;
                case 15:
                    Log.d(TAG, "Serv-mHandler: ACTION_SET_MUTE");
                    try {
                        mIFMRadioService.setMute((Integer) msg.obj);
                    } catch (RemoteException e) {
                        Log.e(TAG, "setMute Failed: " + e.getMessage());
                    }
                    break;
                case 16:
                    Log.d(TAG, "Serv-mHandler: ACTION_GET_RDS_TEXT");
                    synchronized (mLock) {
                        mLock.notifyAll();
                    }
                    break;
                case 17:
                    Log.d(TAG, "Serv-mHandler: ACTION_GET_RDS_VALUE");
                    synchronized (mLock) {
                        mLock.notifyAll();
                    }
                    break;
                case 18:
                    if (isMusicPlaying()) {
                        showNoticeDialog(R.string.fmradio_music_playing_in_listen);
                        notifyCmdResults(FM_QUIT, null);
                    }
                    break;
                case 19:
                    stopSelf();
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

    private void notifyCmdResults(String what, Object value) {
        Intent i = new Intent(what);
        if (what.equals(FM_QUIT)) {
            restoreAudioRoute();
            if (mIsBGMode) {
                mHandler.sendEmptyMessage(SERVICE_SELFSTOP);
                mIsBGMode = false;
                return;
            }
        } else if (what.equals(FM_SEEK_SUCCEED) || what.equals(FM_SEEK_SUCCEED_AND_REACHLIMIT) || what.equals(FM_SCANNING) || what.equals(FM_SCAN_SUCCEED)) {
            Log.d(TAG, "in notifyCmdResults adding frequency value = " + Integer.parseInt(value.toString()));
            i.putExtra("freq", Integer.parseInt(value.toString()));
        } else if (what.equals(FM_RDS_DATA_AVAILABLE)) {
            i.putExtra("freq", Integer.parseInt(value.toString()));
            i.putExtra("rds_status", mRdsStatus);
            i.putExtra("rds_text_display", mRdsTextDisplay);
            i.putExtra("rds_text_id", mRdsTextID);
            i.putExtra("rds_value_pty", mRdsValuePTY);
        }
        sendBroadcast(i);
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
                if (action.equals(FMRadioMain.BIND_SERVICE_SUCCEED)) {
                    Log.d(TAG, "bind service succeed");
                    mBindSucceed = true;
                    if (isAirplaneModeOn()) {
                        showNoticeDialog(R.string.fmradio_airplane_mode_enable_at_begin);
                        notifyCmdResults(FM_QUIT, null);
                    }
                    Log.w(TAG, "Notice home to show update current preset name on the notice bar. ");
                    String preset = intent.getStringExtra(FMRadioMain.PRESET);
                    updateNotification(true, preset);
                } else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                    int state = intent.getIntExtra("state", 0);
                    Log.d(TAG, "HEADSET is pluged in/out.");
                    Message message = Message.obtain(mHandler, HEADSET_PLUG_STATE_CHANGED_MSG, state, 0);
                    mHandler.sendMessage(message);
                } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                    int state = intent.getIntExtra("state", 0);
                    Log.d(TAG, "Aireplane mode changed." + state);
                    if (state != 0) {
                        Log.d("FMRadioService", "Aireplane mode is enabled.");
                        mHandler.sendEmptyMessage(ACTION_AIRPLANE_MODE_CHANGED_MSG);
                    }
                } else if (action.equals(ACTION_AUDIOPATH_FREE)) {
                    Log.d(TAG, "Audio Path is availabel." + intent.getIntExtra("state", 0));
                    mHandler.sendEmptyMessage(AUDIO_PATH_FREE);
                } else if (action.equals(ACTION_AUDIOPATH_BUSY)) {
                    Log.d(TAG, "Audio Path is unavailabel." + intent.getIntExtra("state", 0));
                    mHandler.sendEmptyMessage(AUDIO_PATH_BUSY);
                } else if (action.equals(ACTION_MUSIC_PLAYSTATE_CHANGED) && mBindSucceed) {
                    mHandler.sendEmptyMessage(MUSIC_PLAYSTATE_CHANGED);
                } else if (action.equals(FMRadioMain.PRESET_CHANGED)) {
                    String preset = intent.getStringExtra(FMRadioMain.PRESET);
                    updateNotification(true, preset);
                } else if (action.equals(ACTION_FMRADIO_COMMAND)) {
                    Log.d(TAG, "receive fmradio command");
                    String cmd = intent.getStringExtra(COMMAND);
                    Log.w(TAG, "receive ACTION_FMRADIO_COMMAND cmd=" + cmd);
                    if (cmd.equals(MUTE)) {
                        Log.d(TAG, "set fmradio to mute");
                        try {
                            mIFMRadioService.setMute(1);
                        } catch (RemoteException e) {
                            Log.e(TAG, "setMute Failed: " + e.getMessage());
                        }
                    } else if (cmd.equals(UNMUTE)) {
                        Log.d(TAG, "set fmradio to unmute");
                        try {
                            mIFMRadioService.setMute(0);
                        } catch (RemoteException e) {
                            Log.e(TAG, "setMute Failed: " + e.getMessage());
                        }
                    } else if (cmd.equals(STOP)) {
                        Log.d(TAG, "FM will exit for Video/audio player");
                        notifyCmdResults(FM_QUIT, null);
                    }
                    // XXX: CMD_START?
                } else if (action.equals(FMRadioMain.SERVICE_STOP)) {
                    mHandler.sendEmptyMessage(SERVICE_SELFSTOP);
                } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                    String phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    Log.d(TAG, "ACTION_PHONE_STATE_CHANGED " + phoneState);
                    try {
                        mIFMRadioService.setMute(1);
                    } catch (RemoteException e) {
                        Log.e(TAG, "setMute Failed: " + e.getMessage());
                    }
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
                        try {
                            mIFMRadioService.setMute(misMuted ? 1 : 0);
                        } catch (RemoteException e) {
                            Log.e(TAG, "setMute Failed: " + e.getMessage());
                        }
                        audioPrepare(mAudioRouting);
                        resetVolume();
                    }
                }
            }
        };

        IntentFilter iFilter = new IntentFilter();
        Log.d(TAG, "register airplane on/off broadcasts");
        iFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        Log.d(TAG, "register headset plug in/out broadcasts");
        iFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        iFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

        registerReceiver(mReceiver, iFilter);

        Log.d(TAG, "register the audio path change message");
        registerReceiver(mReceiver, new IntentFilter(ACTION_AUDIOPATH_FREE));
        registerReceiver(mReceiver, new IntentFilter(ACTION_AUDIOPATH_BUSY));
        registerReceiver(mReceiver, new IntentFilter(ACTION_MUSIC_PLAYSTATE_CHANGED));
        registerReceiver(mReceiver, new IntentFilter(FMRadioMain.BIND_SERVICE_SUCCEED));
        registerReceiver(mReceiver, new IntentFilter(FMRadioMain.PRESET_CHANGED));
        registerReceiver(mReceiver, new IntentFilter(FMRadioMain.SERVICE_STOP));
        Log.d(TAG, "register fmradio command");
        registerReceiver(mReceiver, new IntentFilter(ACTION_FMRADIO_COMMAND));
    }

    private void resetRDSData() {
        mRdsStatus = 0;
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
        Log.d(TAG, "onBind() called");
        updateNotification(true, "");
        registerBroadcastListener();
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate() called");
        super.onCreate();
        isInitial = true;
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(1, getClass().getName());
        mWakeLock.setReferenceCounted(false);

        mServiceState = new FMStateUNInit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w(TAG, "onDestroy() called, curState = " + mServiceState.curServiceState());
        updateNotification(false, null);
        if (mReceiver != null) {
            Log.d(TAG, "unregister Receiver.");
            unregisterReceiver(mReceiver);
        }
        if (mServiceState.curServiceState() == SERVICE_READY) {
            restoreAudioRoute();
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (mServiceState.curServiceState() == SERVICE_READY) {
            unbindService(mConnection);
        }
        mTuneFirst = false;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind() called");
        updateNotification(true, "");
        registerBroadcastListener();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.d(TAG, "onStart() called");
        Log.d(TAG, "mServiceState.curServiceState() = " + mServiceState.curServiceState());
        if (!isAirplaneModeOn() && mServiceState.curServiceState() == -1) {
            Log.d(TAG, "Before fm radio power on");
            boolean ret = mServiceState.powerOn(Preferences.getLastFrequency(this));
            Log.d(TAG, "After fm radio power on");
            if (ret) {
                Log.d(TAG, "fm radio power on request sucessfully, wait for callback");
            } else {
                Log.d(TAG, "fm radio power on fail");
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind() called");
        super.onUnbind(intent);

        if (mServiceState.curServiceState() != 2) {
            updateNotification(false, null);
            if (mReceiver != null) {
                Log.d(TAG, "unregister Receiver.");
                unregisterReceiver(mReceiver);
                mReceiver = null;
            }
        }
        return true;
    }

    private void updateNotification(boolean show, String text) {
        final int id = R.string.fmradio_service_label;
        if (show) {
            Intent launchIntent = new Intent();
            launchIntent.setAction(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setComponent(new ComponentName("com.motorola.fmradio", "com.motorola.fmradio.FMRadioMain"));
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, launchIntent, 0);

            Notification notification = new Notification(R.drawable.fm_statusbar_icon, "", System.currentTimeMillis());
            notification.setLatestEventInfo(this, text, null, contentIntent);
            notification.flags |= Notification.DEFAULT_VIBRATE;

            mNM.notify(id, notification);
        } else {
            mNM.cancel(id);
        }
    }
}
