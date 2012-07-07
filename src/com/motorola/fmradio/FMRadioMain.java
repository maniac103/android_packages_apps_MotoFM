package com.motorola.fmradio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class FMRadioMain extends Activity implements SeekBar.OnSeekBarChangeListener,
       View.OnClickListener, View.OnLongClickListener,
       View.OnTouchListener, ImageSwitcher.ViewFactory
{
    private static final String TAG = "FMRadioMain";
    public static String TAG_LISTVIEW = "FMRadioMain ChannelListView";

    public static final String PRESET_CHANGED = "com.motorola.fmradio.preset.changed";

    public static final String PRESET = "preset";

    public static int DEFAULT_VOICE_VOLUME = 0;
    private static int LIGHT_ON_TIME = 90000;
    private static int PRESET_NUM = 20;

    private static final int DIALOG_PROGRESS = 0;
    private static final int DIALOG_SCAN_FINISH = 1;
    private static final int DIALOG_SCAN_CANCEL = 2;
    private static final int DIALOG_IF_SCAN_FIRST = 3;
    private static final int DIALOG_IF_SCAN_NEXT = 4;
    private static final int DIALOG_SAVE_CHANNEL = 5;
    private static final int DIALOG_EDIT_CHANNEL = 6;

    private static final int SHORTPRESS_BUTTON_NONE = 0;
    private static final int SHORTPRESS_BUTTON_ADD = 1;
    private static final int SHORTPRESS_BUTTON_REDUCE = 2;

    private static final int LONGPRESS_BUTTON_NONE = 0;
    private static final int LONGPRESS_BUTTON_ADD = 1;
    private static final int LONGPRESS_BUTTON_REDUCE = 2;

    public static final int PLAY_MENU_ID = 1;
    public static final int EDIT_MENU_ID = 2;
    public static final int REPLACE_MENU_ID = 3;
    public static final int CLEAR_MENU_ID = 4;

    public static final int SAVE_ID = 1;
    public static final int EDIT_ID = 2;
    public static final int CLEAR_ID = 3;
    public static final int EXIT_ID = 4;
    public static final int SCAN_SAVE_ID = 5;
    public static final int BY_LOUDSPEAKER_ID = 6;
    public static final int BY_HEADSET_ID = 7;

    private static final int SAVE_CODE = 0;
    private static final int EDIT_CODE = 1;
    private static final int CLEAR_CODE = 2;

    private static final int START_FMRADIO = 1;
    private static final int STOP_FMRADIO = 2;
    private static final int QUIT = 3;
    private static final int FM_OPEN_FAILED = 4;
    private static final int FM_OPEN_SUCCEED = 5;
    private static final int FM_TUNE_SUCCEED = 6;
    private static final int FM_SEEK_SUCCEED = 7;
    private static final int FM_SEEK_FAILED = 8;
    private static final int FM_SEEK_SUCCEED_AND_REACHLIMIT = 9;
    private static final int FM_HW_ERROR_UNKNOWN = 10;
    private static final int FM_HW_ERROR_FRQ = 11;
    private static final int SEEK_NEXT = 12;
    private static final int FM_RDS_DATA_AVAILABLE = 13;
    private static final int FM_FREQ_ADD = 14;
    private static final int FM_FREQ_REDUCE = 15;
    private static final int FM_SCAN_SUCCEED = 16;
    private static final int FM_SCANNING = 17;
    private static final int FM_SCAN_FAILED = 18;
    private static final int FM_ABORT_COMPLETE = 19;

    private static final int SAVED_STATE_INDEX_LASTCHNUM = 1;
    private static final int SAVED_STATE_INDEX_LASTFREQ = 2;

    private int RANGE = 21000;
    private int RANGE_START = 87000;
    private int RATE = 1000;

    private static final int FREQ_RATE = 1000;

    private static final int LONG_PRESS_SEEK_TIMEOUT = 3000;
    private static final int LONG_PRESS_TUNE_TIMEOUT = 50;
    protected static final long SCAN_STOP_DELAY = 1000;

    private static final Integer[] NUMBER_IMAGES = new Integer[] {
        R.drawable.fm_number_0, R.drawable.fm_number_1, R.drawable.fm_number_2,
        R.drawable.fm_number_3, R.drawable.fm_number_4, R.drawable.fm_number_5,
        R.drawable.fm_number_6, R.drawable.fm_number_7, R.drawable.fm_number_8,
        R.drawable.fm_number_9
    };
    private Integer[] NUMBER_IMAGES_UNSELECTED = new Integer[] {
        R.drawable.fm_number_unselect_0, R.drawable.fm_number_unselect_1,
        R.drawable.fm_number_unselect_2, R.drawable.fm_number_unselect_3,
        R.drawable.fm_number_unselect_4, R.drawable.fm_number_unselect_5,
        R.drawable.fm_number_unselect_6, R.drawable.fm_number_unselect_7,
        R.drawable.fm_number_unselect_8, R.drawable.fm_number_unselect_9
    };
    private Integer[] NUMBER_IMAGES_PRESET = new Integer[] {
        R.drawable.fm_playing_list_0, R.drawable.fm_playing_list_1,
        R.drawable.fm_playing_list_2, R.drawable.fm_playing_list_3,
        R.drawable.fm_playing_list_4, R.drawable.fm_playing_list_5,
        R.drawable.fm_playing_list_6, R.drawable.fm_playing_list_7,
        R.drawable.fm_playing_list_8, R.drawable.fm_playing_list_9
    };
    private Integer[] PTY_STRINGS = new Integer[] {
        R.string.fm_pty_list_00, R.string.fm_pty_list_01, R.string.fm_pty_list_02,
        R.string.fm_pty_list_03, R.string.fm_pty_list_04, R.string.fm_pty_list_05,
        R.string.fm_pty_list_06, R.string.fm_pty_list_07, R.string.fm_pty_list_08,
        R.string.fm_pty_list_09, R.string.fm_pty_list_10, R.string.fm_pty_list_11,
        R.string.fm_pty_list_12, R.string.fm_pty_list_13, R.string.fm_pty_list_14,
        R.string.fm_pty_list_15, R.string.fm_pty_list_16, R.string.fm_pty_list_17,
        R.string.fm_pty_list_18, R.string.fm_pty_list_19, R.string.fm_pty_list_20,
        R.string.fm_pty_list_21, R.string.fm_pty_list_22, R.string.fm_pty_list_23,
        R.string.fm_pty_list_24, R.string.fm_pty_list_25, R.string.fm_pty_list_26,
        R.string.fm_pty_list_27, R.string.fm_pty_list_28, R.string.fm_pty_list_29,
        R.string.fm_pty_list_30, R.string.fm_pty_list_31, R.string.fm_pty_list_32,
        R.string.fm_pty_list_33, R.string.fm_pty_list_34, R.string.fm_pty_list_35,
        R.string.fm_pty_list_36, R.string.fm_pty_list_37, R.string.fm_pty_list_38,
        R.string.fm_pty_list_39, R.string.fm_pty_list_40, R.string.fm_pty_list_41,
        R.string.fm_pty_list_42, R.string.fm_pty_list_43, R.string.fm_pty_list_44,
        R.string.fm_pty_list_45, R.string.fm_pty_list_46, R.string.fm_pty_list_47,
        R.string.fm_pty_list_48, R.string.fm_pty_list_49, R.string.fm_pty_list_50,
        R.string.fm_pty_list_51, R.string.fm_pty_list_52, R.string.fm_pty_list_53,
        R.string.fm_pty_list_54, R.string.fm_pty_list_55, R.string.fm_pty_list_56,
        R.string.fm_pty_list_57, R.string.fm_pty_list_58, R.string.fm_pty_list_59,
        R.string.fm_pty_list_60, R.string.fm_pty_list_61, R.string.fm_pty_list_62,
        R.string.fm_pty_list_63
    };

    private static int lastPosition = 0;
    private static String rds_text_separator = "...";

    private ImageSwitcher Img_switcher1;
    private ImageSwitcher Img_switcher2;
    private ImageSwitcher Img_switcher3;
    private ImageSwitcher Img_switcher4;
    private ImageSwitcher Img_switcher5;
    private ChannelListView LvChannel;
    private int count_save = 0;
    private ImageView currentIcon = null;
    private TextView currentText = null;
    private boolean isClickable = true;
    private boolean isEdit = true;
    private boolean isExitFromUI = false;
    private boolean isHeadsetPlugIn = false;
    protected boolean isInitial = true;
    private boolean isPerformClick = false;
    private boolean isScanAll = false;
    private boolean isScanCanceled = false;
    private boolean isSeekStarted = false;
    private ImageView lastIcon = null;
    private TextView lastText = null;
    private ChannelListAdapter listModelView = null;
    private ArrayList<String> list_results;
    private AudioManager mAudioMgr = null;
    private int mCurFreq = FMUtil.MIN_FREQUENCY;
    private boolean mDirectQuit = true;
    private boolean mFirstClickEvent = true;
    private int mFirstCounter = FMUtil.MIN_FREQUENCY;
    private boolean mInitSuccess = true;
    private BroadcastReceiver mIntentReceiver = null;
    private boolean mIsBound = false;
    private ProgressBar mLevel;
    private int mLongPressButtonType = LONGPRESS_BUTTON_NONE;
    private Toast mPowerWaitingTS = null;
    private int mPreFreq = FMUtil.MIN_FREQUENCY;
    private int mRDSFreq = 0;
    private boolean mRdsAvailable = false;
    private MarqueeText mRdsMarqueeText;
    private String mRdsTextDisplay = "";
    private String mRdsTextID = "";
    private int mRdsValuePTY = 0;
    private ImageView mRingerStreamIcon;
    private AnimationDrawable mScanAnimation;
    private ImageView mScanBar;
    private SeekBar mSeekBar;
    private IFMRadioPlayerService mService = null;
    private int mShortPressButtonType = SHORTPRESS_BUTTON_NONE;
    private Timer mTimer;
    private TimerTask mTimerTask;
    private Toast mToast;
    protected boolean mTuneSucceed = true;
    private WakeLock mWakeLock;
    private boolean mbIsFMStart = false;
    private boolean mbIsLongPressed = false;
    private ImageButton nextButton;
    private ProgressDialog pDialog;
    private ProgressDialog pDialog_waitpoweron = null;
    private ImageSwitcher preset_swt1;
    private ImageSwitcher preset_swt2;
    private RelativeLayout preset_swt_layout;
    private ImageButton prevButton;
    private ImageButton seekBackButton;
    private ImageButton seekForwardButton;
    private int tempPosition = 0;
    private int volume = DEFAULT_VOICE_VOLUME;
    private View volumeView;

    private class ScanStopThread extends Thread {
        @Override
        public void run() {
            super.run();
            if (mScanBar != null && mScanBar.getBackground() != null) {
                ((AnimationDrawable) mScanBar.getBackground()).stop();
                mScanBar.setBackgroundDrawable(null);
                enableUI(true);
            }
        }
    }

    private class ChannelListView extends ListView {
        public ChannelListView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Log.d(TAG_LISTVIEW, "call onDraw");
            if (isPerformClick) {
                Log.d(TAG_LISTVIEW, "is perform click");
                Log.d(TAG_LISTVIEW, "lastPosition is " + lastPosition);
                int clickPosition = lastPosition - getFirstVisiblePosition();
                Log.d(TAG_LISTVIEW, "clickPosition is " + clickPosition);
                mFirstClickEvent = false;
                boolean click_result = performItemClick(getChildAt(clickPosition), lastPosition, getItemIdAtPosition(clickPosition));
                mFirstClickEvent = true;
                Log.d(TAG_LISTVIEW, "click_result is " + click_result);
                isPerformClick = false;
            }
        }
    }

    private class ChannelListAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        private class ViewHolder {
            ImageView icon;
            TextView text;
            TextView text2;
        }

        public ChannelListAdapter(Activity context, ChannelListView listview) {
            super();
            mInflater = LayoutInflater.from(context);
            listview.setAdapter(this);
        }

        @Override
        public int getCount() {
            return list_results.size();
        }

        @Override
        public Object getItem(int position) {
            return Integer.valueOf(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Log.d(TAG, "FMRadio ChannelListAdapter, getView(), position = " + position);
            ViewHolder holder;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.listview_row, null);
                if (convertView == null) {
                    return parent; // XXX ???
                }
                holder = new ViewHolder();
                holder.text = (TextView) convertView.findViewById(R.id.list_text);
                holder.text2 = (TextView) convertView.findViewById(R.id.list_text2);
                holder.icon = (ImageView) convertView.findViewById(R.id.list_icon);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.text.setText(String.format("%02d", position + 1));
            holder.text2.setText(list_results.get(position));
            holder.text2.setSingleLine(true);
            holder.text2.setEllipsize(TruncateAt.END);
            if (position == lastPosition) {
                Log.d(TAG, "FMRadio ChannelListAdapter getview, set visible position: " + lastPosition);
                holder.icon.setVisibility(View.VISIBLE);
                holder.text.setTextColor(Color.WHITE);
                convertView.setBackgroundColor(0);
            } else {
                Log.d(TAG, "FMRadio ChannelListAdapter getview, set gone");
                holder.icon.setVisibility(View.INVISIBLE);
                holder.text.setTextColor(Color.WHITE);
            }
            return convertView;
        }
    }

    private ServiceConnection mServConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.w(TAG, "onServiceConnected::fmradio java service started");
            mService = IFMRadioPlayerService.Stub.asInterface(service);
            mIsBound = true;

            boolean bPowerStatus = false;
            if (mService != null) {
                try {
                    bPowerStatus = mService.isPowerOn();
                } catch (RemoteException e) {
                    Log.e(TAG, "Justfy if chip power on failed");
                }
            }
            if (!bPowerStatus) {
                if (pDialog_waitpoweron == null) {
                    pDialog_waitpoweron = ProgressDialog.show(FMRadioMain.this, "", getResources().getText(R.string.fmradio_waiting_for_power_on), true, true);
                    Log.w(TAG, "servie is ready popup a wait dialog");
                }
            } else  if (!mbIsFMStart) {
                mHandler.sendEmptyMessage(START_FMRADIO);
                mbIsFMStart = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.w(TAG, "From FM ui fmradio service layer disconnected");
            mService = null;
            finish();
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            final Context context = FMRadioMain.this;

            switch (msg.what) {
                case START_FMRADIO:
                    // pswitch_0
                    enableUI(true);
                    Preferences.setEnabled(context, true);
                    isPerformClick = true;
                    volume = Preferences.getVolume(context);
                    if (isDBEmpty() || !Preferences.isScanned(context)) {
                        showDialog(DIALOG_IF_SCAN_FIRST);
                    }
                    break;
                case STOP_FMRADIO:
                    // pswitch_1
                    powerOffFMRadioDevice();
                    break;
                case QUIT:
                    // pswitch_2
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.service_start_error_title)
                            .setMessage(R.string.service_start_error_msg)
                            .setPositiveButton(R.string.service_start_error_button, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    isExitFromUI = true;
                                    finish();
                                }
                            })
                            .setCancelable(false)
                            .show();
                    break;
                case FM_OPEN_FAILED:
                    // pswitch_3
                    Preferences.setEnabled(context, false);
                    Toast.makeText(context, "FMRadio Open failed", Toast.LENGTH_SHORT).show();
                    break;
                case FM_OPEN_SUCCEED:
                    // pswitch_4
                    Log.d(TAG, "FM open succeed callback");
                    Preferences.setEnabled(context, true);
                    enableUI(true);
                    volume = Preferences.getVolume(context);
                    setFMRadioVolume(volume);
                    isPerformClick = true;
                    if (isDBEmpty() || !Preferences.isScanned(context)) {
                        showDialog(DIALOG_IF_SCAN_FIRST);
                    }
                    break;
                case FM_TUNE_SUCCEED:
                    // pswitch_5
                    displayRdsScrollByCurFreq(false);
                    enableUI(true);
                    ignoreRdsEvent(false);
                    mTuneSucceed = true;
                    signalPresetChanged(lastPosition);
                    Log.d(TAG, "FM Tune succeed callback");
                    break;
                case FM_SEEK_FAILED:
                case FM_SCAN_FAILED:
                    // pswitch_6
                    if (isScanAll) {
                        mHandler.sendEmptyMessage(SEEK_NEXT);
                        isScanCanceled = true;
                    } else {
                        ScanStopThread sThread = new ScanStopThread();
                        mHandler.postDelayed(sThread, SCAN_STOP_DELAY);
                    }
                    break;
                case FM_SEEK_SUCCEED:
                case FM_SCANNING:
                    // pswitch_7
                    Log.d(TAG, "seek forward/backward callback, the new frequency = " + mCurFreq);
                    isSeekStarted = false;
                    mShortPressButtonType = SHORTPRESS_BUTTON_NONE;
                    if (mbIsLongPressed) {
                        resetTimer();
                        mPreFreq = mCurFreq;
                        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                        mTimer = new Timer();
                        if (mLongPressButtonType == LONGPRESS_BUTTON_REDUCE) {
                            mTimerTask = new TimerTask() {
                                @Override
                                public void run() {
                                    isSeekStarted = true;
                                    mLongPressButtonType = LONGPRESS_BUTTON_REDUCE;
                                    seekFMRadioStation(0, 1);
                                }
                            };
                            mTimer.schedule(mTimerTask, LONG_PRESS_SEEK_TIMEOUT);
                        } else if (mLongPressButtonType == LONGPRESS_BUTTON_ADD) {
                            mTimerTask = new TimerTask() {
                                @Override
                                public void run() {
                                    isSeekStarted = true;
                                    mLongPressButtonType = LONGPRESS_BUTTON_ADD;
                                    seekFMRadioStation(0, 0);
                                }
                            };
                        } else {
                            Log.e(TAG, "Error, Long press seek type is unknonw;");
                        }
                    } else if (isScanAll) {
                        if (count_save <= PRESET_NUM) {
                            saveStationToDB(count_save, mCurFreq, "", "");
                            if (count_save == 0) {
                                mFirstCounter = mCurFreq;
                            }
                            count_save++;
                            if (pDialog != null) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(getString(R.string.fmradio_found));
                                sb.append(" ");
                                sb.append(count_save);
                                sb.append(" ");
                                sb.append(getString(count_save > 1 ? R.string.fmradio_stations : R.string.fmradio_station));
                                sb.append("...");
                                pDialog.setMessage(sb.toString());
                            }
                            if (!isScanCanceled && count_save >= PRESET_NUM) {
                                pDialog.cancel();
                            }
                        }
                    }
                    if (!isScanAll || isScanCanceled) {
                        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                        displayRdsScrollByCurFreq(false);
                        enableUI(true);
                        signalPresetChanged(lastPosition);
                        ScanStopThread sThread = new ScanStopThread();
                        mHandler.postDelayed(sThread, SCAN_STOP_DELAY);
                    }
                    break;
                case SEEK_NEXT:
                    // pswitch_8
                    Log.d(TAG, "SEEK_NEXT received");
                    if (count_save < PRESET_NUM && !isScanCanceled) {
                        Log.d(TAG, "scan not canceled, seek next station");
                        seekFMRadioStation(0, 0);
                    } else {
                        Log.d(TAG, "20 stations scaned out");
                        if (pDialog != null) {
                            pDialog.dismiss();
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append(getString(R.string.fmradio_save_canceled));
                        sb.append(" ");
                        sb.append(getString(R.string.saved));
                        sb.append(" ");
                        sb.append(count_save);
                        sb.append(" ");
                        sb.append(getString(count_save > 1 ? R.string.fmradio_stations : R.string.fmradio_station));
                        sb.append(".");
                        Toast ts = Toast.makeText(FMRadioMain.this, sb.toString(), Toast.LENGTH_SHORT);
                        ts.setGravity(Gravity.CENTER, 0, 0);
                        ts.show();

                        ScanStopThread sThread = new ScanStopThread();
                        mHandler.postDelayed(sThread, SCAN_STOP_DELAY);

                        if (lastIcon != null) {
                            lastIcon.setVisibility(View.INVISIBLE);
                        }
                        updateListView();
                        LvChannel.setSelection(0);
                        signalPresetChanged(lastPosition);
                        lastPosition = 0;
                        isPerformClick = true;
                        count_save = 0;
                        isScanAll = false;
                    }
                    break;
                case FM_SEEK_SUCCEED_AND_REACHLIMIT:
                case FM_SCAN_SUCCEED:
                    Log.d(TAG, "seek reach limit, mCurFreq = " + mCurFreq);
                    if (isScanAll) {
                        isScanCanceled = true;
                        mCurFreq = mFirstCounter;
                        if (pDialog != null) {
                            pDialog.dismiss();
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append(getString(R.string.saved));
                        sb.append(" ");
                        sb.append(count_save);
                        sb.append(" ");
                        sb.append(getString(count_save > 1 ? R.string.fmradio_stations : R.string.fmradio_station));
                        sb.append(".");
                        Toast ts = Toast.makeText(FMRadioMain.this, sb.toString(), Toast.LENGTH_SHORT);
                        ts.setGravity(Gravity.CENTER, 0, 0);
                        ts.show();
                        signalPresetChanged(lastPosition);
                        if (lastIcon != null) {
                            lastIcon.setVisibility(View.INVISIBLE);
                        }
                        lastPosition = 0;
                        ignoreRdsEvent(false);
                        updateListView();
                        LvChannel.setSelection(0);
                        isPerformClick = true;
                        count_save = 0;
                        isScanAll = false;
                        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                        displayRdsScrollByCurFreq(false);

                        ScanStopThread sThread = new ScanStopThread();
                        mHandler.postDelayed(sThread, SCAN_STOP_DELAY);
                        Log.d(TAG, "seek forward/backward reach limit callback, the new frequency = " + mCurFreq);
                    } else if (isSeekStarted || mbIsLongPressed) {
                        if (mLongPressButtonType == LONGPRESS_BUTTON_REDUCE) {
                            isSeekStarted = true;
                            seekFMRadioStation(FMUtil.MAX_FREQUENCY, 1);
                        } else if (mLongPressButtonType == LONGPRESS_BUTTON_ADD) {
                            isSeekStarted = true;
                            seekFMRadioStation(FMUtil.MIN_FREQUENCY, 0);
                        } else {
                            Log.e(TAG, "Error, Long press seek type is unknonw;");
                        }
                    } else if (isSeekStarted) {
                        isSeekStarted = false;
                        if (mShortPressButtonType == SHORTPRESS_BUTTON_ADD) {
                            seekFMRadioStation(FMUtil.MIN_FREQUENCY, 0);
                        } else if (mShortPressButtonType == SHORTPRESS_BUTTON_REDUCE) {
                            seekFMRadioStation(FMUtil.MAX_FREQUENCY, 1);
                        }
                    } else {
                        mCurFreq = mPreFreq;
                        setFMRadioFrequency();
                        isScanAll = false;
                        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                        displayRdsScrollByCurFreq(false);

                        ScanStopThread sThread = new ScanStopThread();
                        mHandler.postDelayed(sThread, SCAN_STOP_DELAY);
                        Log.d(TAG, "seek forward/backward reach limit callback, the new frequency = " + mCurFreq);
                    }
                    break;
                case FM_HW_ERROR_UNKNOWN:
                    Log.d(TAG, "FM hardware unknow error!");
                    enableUI(true);
                    break;
                case FM_HW_ERROR_FRQ:
                    Log.d(TAG, "FM Tune frequency error!");
                    enableUI(true);
                    break;
                case FM_RDS_DATA_AVAILABLE:
                    Log.d(TAG, "FM RDS data available!");
                    if (mRdsAvailable && mRdsTextID.length() > 0) {
                        Log.d(TAG, "station name available");
                        String sFreq = Float.toString((float) mCurFreq / 1000.0F);
                        Cursor cursor = getContentResolver().query(FMUtil.CONTENT_URI, FMUtil.PROJECTION, "CH_Freq=" + sFreq, null, null);
                        if (cursor != null && cursor.getCount() > 0) {
                            Log.d(TAG, "checking each preset");
                            startManagingCursor(cursor);
                            cursor.moveToFirst();
                            while (!cursor.isAfterLast()) {
                                Log.d(TAG, "getting cursor string");
                                String chName = cursor.getString(FMUtil.FM_RADIO_INDEX_CHNAME);
                                if (FMUtil.isEmptyStr(chName)) {
                                    int id = cursor.getInt(FMUtil.FM_RADIO_INDEX_ID);
                                    saveStationToDB(id, mCurFreq, null, mRdsTextID);
                                    updateDisplayPanel(mCurFreq, true);
                                    updatePresetSwitcher(id + 1);
                                    lastPosition = id;
                                    updateListView();
                                    signalPresetChanged(lastPosition);
                                }
                                cursor.moveToNext();
                            }
                            cursor.close();
                        }
                    }
                    String rds_full_text = "";
                    Log.d(TAG, "make RT marquee");
                    if (mRdsTextID.length() > 0) {
                        Log.d(TAG, "add the station name");
                        rds_full_text = mRdsTextID;
                    }
                    if (mRdsTextDisplay.length() > 0) {
                        Log.d(TAG, "add the RT text");
                        if (rds_full_text.length() > 0) {
                            rds_full_text += rds_text_separator;
                        }
                        rds_full_text += mRdsTextDisplay;
                    }
                    if (mRdsValuePTY >= 0 && mRdsValuePTY < PTY_STRINGS.length) {
                        String rds_pty = getString(PTY_STRINGS[mRdsValuePTY]);
                        if (rds_pty.length() > 0) {
                            Log.d(TAG, "add the program type");
                            if (rds_full_text.length() > 0) {
                                rds_full_text += rds_text_separator;
                            }
                            rds_full_text += rds_pty;
                        }
                    } else {
                        Log.d(TAG, "mRdsValuePTY is " + mRdsValuePTY + " which is incorrect!!!!!!!!!!!!!!!!");
                    }
                    mRdsMarqueeText.setText(rds_full_text);
                    displayRdsScrollByCurFreq(true);
                    break;
                case FM_FREQ_ADD:
                    if (mbIsLongPressed) {
                        resetTimer();
                        mCurFreq += FMUtil.STEP;
                        if (mCurFreq > FMUtil.MAX_FREQUENCY) {
                            mCurFreq = FMUtil.MIN_FREQUENCY;
                        }
                        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                        mTimer = new Timer();
                        mTimerTask = new TimerTask() {
                            @Override
                            public void run() {
                                mHandler.sendEmptyMessage(FM_FREQ_ADD);
                            }
                        };
                        mTimer.schedule(mTimerTask, LONG_PRESS_TUNE_TIMEOUT);
                    }
                    break;
                case FM_FREQ_REDUCE:
                    if (mbIsLongPressed) {
                        resetTimer();
                        mCurFreq -= FMUtil.STEP;
                        if (mCurFreq < FMUtil.MIN_FREQUENCY) {
                            mCurFreq = FMUtil.MAX_FREQUENCY;
                        }
                        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                        mTimer = new Timer();
                        mTimerTask = new TimerTask() {
                            @Override
                            public void run() {
                                mHandler.sendEmptyMessage(FM_FREQ_REDUCE);
                            }
                        };
                        mTimer.schedule(mTimerTask, LONG_PRESS_TUNE_TIMEOUT);
                    }
                    break;
            }
        }
    };

    private void bindListViewToAdapter(Activity ctx, ChannelListView listview) {
        Log.d(TAG, "Enter bindListViewToAdapter()");
        listModelView = new ChannelListAdapter(ctx, listview);
        listview.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuinfo) {
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuinfo;
                int pos = (int) info.id;
                menu.setHeaderTitle(list_results.get(pos));
                String posStr = Integer.toString(pos);
                Cursor cursor = getContentResolver().query(FMUtil.CONTENT_URI, FMUtil.PROJECTION, "ID=" + posStr, null, null);
                if (cursor != null) {
                    startManagingCursor(cursor);
                    cursor.moveToFirst();
                    String chFreq = cursor.getString(FMUtil.FM_RADIO_INDEX_CHFREQ);
                    if (FMUtil.isEmptyStr(chFreq)) {
                        saveChannel(pos);
                    } else {
                        menu.setHeaderTitle(list_results.get(pos));
                        if (mCurFreq != (int) (Float.parseFloat(chFreq) * 1000.0F)) {
                            menu.add(Menu.NONE, PLAY_MENU_ID, Menu.NONE, R.string.play_preset);
                        }
                        menu.add(Menu.NONE, EDIT_MENU_ID, Menu.NONE, R.string.edit_preset);
                        if (mCurFreq != (int) (Float.parseFloat(chFreq) * 1000.0F)) {
                            menu.add(Menu.NONE, REPLACE_MENU_ID, Menu.NONE, R.string.replace_preset);
                        }
                        menu.add(Menu.NONE, CLEAR_MENU_ID, Menu.NONE, R.string.clear_preset);
                    }
                    cursor.close();
                }
            }
        });
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (isClickable) {
                    playClickPreset(view, position);
                }
            }
        });
    }

    private boolean bindToService() {
        Log.d(TAG, "Start to bind to FMRadio service");
        boolean isLandscape = getResources().getConfiguration().orientation == 2;
        String strPreset = FMUtil.getPresetStr(getContentResolver(), Integer.valueOf(mCurFreq), lastPosition, isLandscape);
        Intent i = new Intent(this, FMRadioPlayerService.class);
        if (strPreset != null) {
            i.putExtra(PRESET, strPreset);
        }
        startService(i);
        return bindService(i, mServConnection, 0);
    }

    private void unbindFMRadioService() {
        if (mIsBound) {
            unbindService(mServConnection);
            mIsBound = false;
        }
        stopService(new Intent(this, FMRadioPlayerService.class));
    }

    private void clearPresetSwitcher() {
        if (preset_swt1 != null && preset_swt2 != null) {
            preset_swt_layout.setBackgroundDrawable(null);
            preset_swt1.setImageDrawable(null);
            preset_swt2.setImageDrawable(null);
        }
    }

    private void displayRdsScrollByCurFreq(boolean show) {
        if (mCurFreq != mRDSFreq) {
            Log.w(TAG, "Current station is not the frequency where RDS will be shown");
            Log.w(TAG, "mRDSFreq = " + mRDSFreq + " mCurFreq = " + mCurFreq);
            displayRdsScrollText(false);
        } else if (show) {
            displayRdsScrollText(true);
        }
    }

    private void displayRdsScrollText(boolean show) {
        Log.d(TAG, "displayRdsScrollText(" + show + ")");
        if (!show) {
            mRdsTextID = "";
            mRdsTextDisplay = "";
            mRdsValuePTY = 0;
            mRdsAvailable = false;
        }
        if (mSeekBar != null && mRdsMarqueeText != null) {
            if (show) {
                mSeekBar.setVisibility(View.GONE);
                mRdsMarqueeText.setVisibility(View.VISIBLE);
            } else {
                mSeekBar.setVisibility(View.VISIBLE);
                mRdsMarqueeText.setVisibility(View.GONE);
            }
        }
    }

    private void enableActiveButton(View v) {
        switch (v.getId()) {
            case R.id.btn_seekbackward:
                seekBackButton.setEnabled(true);
                seekBackButton.setBackgroundResource(R.drawable.fm_autosearch_reduce_enable);
                prevButton.setEnabled(false);
                prevButton.setBackgroundResource(R.drawable.fm_manualadjust_reduce_disable);
                nextButton.setEnabled(false);
                nextButton.setBackgroundResource(R.drawable.fm_manualadjust_plus_disable);
                seekForwardButton.setEnabled(false);
                seekForwardButton.setBackgroundResource(R.drawable.fm_autosearch_plus_disable);
                break;
            case R.id.btn_reduce:
                seekBackButton.setEnabled(false);
                seekBackButton.setBackgroundResource(R.drawable.fm_autosearch_reduce_disable);
                prevButton.setEnabled(true);
                prevButton.setBackgroundResource(R.drawable.fm_manualadjust_reduce_enable);
                nextButton.setEnabled(false);
                nextButton.setBackgroundResource(R.drawable.fm_manualadjust_plus_disable);
                seekForwardButton.setEnabled(false);
                seekForwardButton.setBackgroundResource(R.drawable.fm_autosearch_plus_disable);
                break;
            case R.id.btn_add:
                seekBackButton.setEnabled(false);
                seekBackButton.setBackgroundResource(R.drawable.fm_autosearch_reduce_disable);
                prevButton.setEnabled(false);
                prevButton.setBackgroundResource(R.drawable.fm_manualadjust_reduce_disable);
                nextButton.setEnabled(true);
                nextButton.setBackgroundResource(R.drawable.fm_manualadjust_plus_enable);
                seekForwardButton.setEnabled(false);
                seekForwardButton.setBackgroundResource(R.drawable.fm_autosearch_plus_disable);
                break;
            case R.id.btn_seekforward:
                seekBackButton.setEnabled(false);
                seekBackButton.setBackgroundResource(R.drawable.fm_autosearch_reduce_disable);
                prevButton.setEnabled(false);
                prevButton.setBackgroundResource(R.drawable.fm_manualadjust_reduce_disable);
                nextButton.setEnabled(false);
                nextButton.setBackgroundResource(R.drawable.fm_manualadjust_plus_disable);
                seekForwardButton.setEnabled(true);
                seekForwardButton.setBackgroundResource(R.drawable.fm_autosearch_plus_enable);
                break;
        }

        LvChannel.setEnabled(false);
        isClickable = false;
    }

    private void enableUI(boolean enabled) {
        seekBackButton.setEnabled(enabled);
        prevButton.setEnabled(enabled);
        nextButton.setEnabled(enabled);
        seekForwardButton.setEnabled(enabled);
        if (enabled) {
            seekBackButton.setBackgroundResource(R.drawable.fm_autosearch_reduce_enable);
            prevButton.setBackgroundResource(R.drawable.fm_manualadjust_reduce_enable);
            nextButton.setBackgroundResource(R.drawable.fm_manualadjust_plus_enable);
            seekForwardButton.setBackgroundResource(R.drawable.fm_autosearch_plus_enable);
        } else {
            seekBackButton.setBackgroundResource(R.drawable.fm_autosearch_reduce_disable);
            prevButton.setBackgroundResource(R.drawable.fm_manualadjust_reduce_disable);
            nextButton.setBackgroundResource(R.drawable.fm_manualadjust_plus_disable);
            seekForwardButton.setBackgroundResource(R.drawable.fm_autosearch_plus_disable);
        }
        if (LvChannel != null) {
            LvChannel.setEnabled(enabled);
        }
        isClickable = enabled;
    }

    private int getIndexOfEmptyItem() {
        Cursor cur = getContentResolver().query(FMUtil.CONTENT_URI, FMUtil.PROJECTION, null, null, null);
        int count = 0;

        if (cur == null) {
            Log.e(TAG, "Query DB return cur null!");
            return 0;
        }
        startManagingCursor(cur);
        cur.moveToFirst();
        while (!cur.isAfterLast()) {
            String chFreq = cur.getString(FMUtil.FM_RADIO_INDEX_CHFREQ);
            if (FMUtil.isEmptyStr(chFreq)) {
                return count;
            }
            count++;
            cur.moveToNext();
        }
        cur.close();
        return 0;
    }

    private void handleSeekMsg(View v, int direction) {
        isEdit = false;
        isScanAll = false;
        isSeekStarted = true;
        mPreFreq = mCurFreq;
        seekFMRadioStation(0, direction);
        mScanBar.setBackgroundDrawable(mScanAnimation);
        mScanAnimation.start();
        enableActiveButton(v);
        mRdsAvailable = false;
        displayRdsScrollText(false);
    }

    private void ignoreRdsEvent(boolean flag) {
        if (mService != null) {
            try {
                mService.ignoreRdsEvent(flag);
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    private void initButton() {
        nextButton = (ImageButton) findViewById(R.id.btn_add);
        if (nextButton != null) {
            nextButton.setOnClickListener(this);
            nextButton.setOnLongClickListener(this);
            nextButton.setOnTouchListener(this);
        }
        prevButton = (ImageButton) findViewById(R.id.btn_reduce);
        if (prevButton != null) {
            prevButton.setOnClickListener(this);
            prevButton.setOnLongClickListener(this);
            prevButton.setOnTouchListener(this);
        }
        seekBackButton = (ImageButton) findViewById(R.id.btn_seekbackward);
        if (seekBackButton != null) {
            seekBackButton.setOnClickListener(this);
            seekBackButton.setOnLongClickListener(this);
            seekBackButton.setOnTouchListener(this);
        }
        seekForwardButton = (ImageButton) findViewById(R.id.btn_seekforward);
        if (seekForwardButton != null) {
            seekForwardButton.setOnClickListener(this);
            seekForwardButton.setOnLongClickListener(this);
            seekForwardButton.setOnTouchListener(this);
        }
    }

    private void initImageSwitcher() {
        Img_switcher1 = (ImageSwitcher) findViewById(R.id.Img_switcher1);
        Img_switcher1.setFactory(this);
        Img_switcher2 = (ImageSwitcher) findViewById(R.id.Img_switcher2);
        Img_switcher2.setFactory(this);
        Img_switcher3 = (ImageSwitcher) findViewById(R.id.Img_switcher3);
        Img_switcher3.setFactory(this);
        Img_switcher4 = (ImageSwitcher) findViewById(R.id.Img_switcher4);
        Img_switcher4.setFactory(this);
        Img_switcher5 = (ImageSwitcher) findViewById(R.id.Img_switcher5);
        Img_switcher5.setFactory(this);

        preset_swt_layout = (RelativeLayout) findViewById(R.id.preset_swt_layout);

        preset_swt1 = (ImageSwitcher) findViewById(R.id.preset_swt1);
        preset_swt1.setFactory(this);
        preset_swt2 = (ImageSwitcher) findViewById(R.id.preset_swt2);
        preset_swt2.setFactory(this);
    }

    private void initListView() {
        LvChannel = new ChannelListView(this);
        if (LvChannel == null) {
            mInitSuccess = false;
            return;
        }
        LvChannel.setCacheColorHint(0);
        LvChannel.setVerticalFadingEdgeEnabled(true);
        LvChannel.setFadingEdgeLength(50);
        LvChannel.setSelection(lastPosition);

        getDataFromDB();
        if (list_results == null) {
            mInitSuccess = false;
            return;
        }
        bindListViewToAdapter(this, LvChannel);

        LinearLayout listLayout = (LinearLayout) findViewById(R.id.list_layout);
        listLayout.addView(LvChannel);
    }

    private void initResourceRefs() {
        Log.d(TAG, "enter initResourceRefs()");
        initVolumeView();
        initImageSwitcher();
        initSeekBar();
        initButton();
        initListView();
        enableUI(false);
        if (!mInitSuccess) {
            Toast t = Toast.makeText(this, R.string.error_mem_full, Toast.LENGTH_LONG);
            t.setGravity(Gravity.CENTER, 0, 0);
            t.show();
            finish();
        } else {
            Log.d(TAG, "leave initResourceRefs()");
        }
    }

    private void initSeekBar() {
        mSeekBar = (SeekBar) findViewById(R.id.seek);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setEnabled(true);

        mRdsMarqueeText = (MarqueeText) findViewById(R.id.rds_text);
        mRdsMarqueeText.setTextColor(Color.WHITE);
        mRdsMarqueeText.setTextSize(22);
    }

    private void initVolumeView() {
        LayoutInflater inflater  = LayoutInflater.from(this);
        volumeView = inflater.inflate(R.layout.volume_control, null);

        mLevel = (ProgressBar) volumeView.findViewById(R.id.level);
        mLevel.setMax(15);
        mScanBar = (ImageView) findViewById(R.id.scan_anim);
        mScanAnimation = (AnimationDrawable) getResources().getDrawable(R.drawable.fm_progress_red);
        mToast = new Toast(this);
        mRingerStreamIcon = (ImageView) volumeView.findViewById(R.id.ringer_stream_icon);
        mRingerStreamIcon.setImageResource(R.drawable.ic_mp_unmute);
    }

    private void onCreateInternal() {
        Log.d(TAG, "**************FMRadioMain Activity onCreateInternal() called!****************");
        setContentView(R.layout.main);

        mCurFreq = Preferences.getLastFrequency(this);
        lastPosition = Preferences.getLastChannel(this);

        initResourceRefs();
        bindToService();
        registerBroadcastReceiver();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(6, getClass().getName());
        mWakeLock.setReferenceCounted(false);
        isExitFromUI = false;
        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
        updateListView();
    }

    private void playClickPreset(View view, int position) {
        if (view == null) {
            Log.d("FMRadio", "view is null");
            return;
        }
        Log.d(TAG, "FMRadio onClick lastPosition is " + lastPosition);
        Log.d(TAG, "FMRadio onClick c_position is: " + position);
        lastPosition = position;

        String c_p = Integer.toString(position);
        Cursor cursor = getContentResolver().query(FMUtil.CONTENT_URI, FMUtil.PROJECTION, "ID=" + c_p, null, null);
        if (cursor == null) {
            Log.d(TAG, "not find item");
            return;
        }

        startManagingCursor(cursor);
        cursor.moveToFirst();

        String chFreq = cursor.getString(FMUtil.FM_RADIO_INDEX_CHFREQ);
        if (FMUtil.isEmptyStr(chFreq)) {
            if (mFirstClickEvent) {
                cursor.close();
                return;
            }
            Log.d(TAG, "Select an empty list item, go to save UI");
            saveChannel(position);
        } else {
            if (lastIcon != null) {
                Log.d(TAG, "lastIcon is not null");
                lastIcon.setVisibility(View.INVISIBLE);
            } else {
                Log.d(TAG, "lastIcon is null");
            }
            if (lastText != null) {
                Log.d(TAG, "lastText is not null");
                lastText.setTextColor(Color.WHITE);
            } else {
                Log.d(TAG, "lastText is null");
            }
            currentIcon = (ImageView) findViewById(R.id.list_icon);
            currentIcon.setVisibility(View.VISIBLE);
            currentText = (TextView) findViewById(R.id.list_text);
            currentText.setTextColor(Color.WHITE);
            lastIcon = currentIcon;
            lastText = currentText;
            isEdit = true;
            mCurFreq = (int) (Float.parseFloat(chFreq) * 1000.0F);
            updateDisplayPanel(mCurFreq, true);
            updatePresetSwitcher(position + 1);
            setFMRadioFrequency();
            updateListView();
        }
        cursor.close();
    }

    private void powerOffFMRadioDevice() {
        Log.d(TAG, "powerOffFMRadioDevice");
        if (mService != null) {
            try {
                Log.d(TAG, "mService.powerOff() called!");
                mService.powerOff();
            } catch (RemoteException e) {
                Log.d(TAG, "mService.powerOff() RemoteException!");
            }
        }
    }

    private void registerBroadcastReceiver() {
        if (mIntentReceiver != null) {
            return;
        }

        mIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.v(TAG, "Launcher broadcast Received " + action);
                if (action.equals(FMRadioPlayerService.FM_OPEN_FAILED)) {
                    mHandler.sendEmptyMessage(FM_OPEN_FAILED);
                } else if (action.equals(FMRadioPlayerService.FM_OPEN_SUCCEED)) {
                    mHandler.sendEmptyMessage(FM_OPEN_SUCCEED);
                } else if (action.equals(FMRadioPlayerService.FM_TUNE_SUCCEED)) {
                    mHandler.sendEmptyMessage(FM_TUNE_SUCCEED);
                } else if (action.equals(FMRadioPlayerService.FM_SEEK_SUCCEED)) {
                    mCurFreq = intent.getIntExtra("freq", FMUtil.MIN_FREQUENCY);
                    mHandler.sendEmptyMessage(FM_SEEK_SUCCEED);
                } else if (action.equals(FMRadioPlayerService.FM_SEEK_FAILED)) {
                    mHandler.sendEmptyMessage(FM_SEEK_FAILED);
                } else if (action.equals(FMRadioPlayerService.FM_SEEK_SUCCEED_AND_REACHLIMIT)) {
                    mCurFreq = intent.getIntExtra("freq", FMUtil.MAX_FREQUENCY);
                    mHandler.sendEmptyMessage(FM_SEEK_SUCCEED_AND_REACHLIMIT);
                } else if (action.equals(FMRadioPlayerService.FM_SCAN_SUCCEED)) {
                    mCurFreq = intent.getIntExtra("freq", FMUtil.MIN_FREQUENCY);
                    mHandler.sendEmptyMessage(FM_SCAN_SUCCEED);
                } else if (action.equals(FMRadioPlayerService.FM_SCANNING)) {
                    mCurFreq = intent.getIntExtra("freq", FMUtil.MIN_FREQUENCY);
                    mHandler.sendEmptyMessage(FM_SCANNING);
                } else if (action.equals(FMRadioPlayerService.FM_SCAN_FAILED)) {
                    mHandler.sendEmptyMessage(FM_SCAN_SUCCEED); // XXX: 0x10 in smali
                } else if (action.equals(FMRadioPlayerService.FM_ABORT_COMPLETE)) {
                    if (isScanCanceled) {
                        if (count_save < PRESET_NUM) {
                            mHandler.sendEmptyMessage(SEEK_NEXT);
                        } else {
                            mHandler.sendEmptyMessage(FM_SCAN_SUCCEED); // XXX: 0x10 in smali
                        }
                    }
                } else if (action.equals(FMRadioPlayerService.FM_HW_ERROR_UNKNOWN)) {
                    mHandler.sendEmptyMessage(FM_HW_ERROR_UNKNOWN);
                } else if (action.equals(FMRadioPlayerService.FM_HW_ERROR_FRQ)) {
                    mHandler.sendEmptyMessage(FM_HW_ERROR_FRQ);
                } else if (action.equals(FMRadioPlayerService.FM_RDS_DATA_AVAILABLE)) {
                    mRDSFreq = intent.getIntExtra("freq", 0);
                    mRdsTextID = intent.getStringExtra("rds_text_id");
                    mRdsTextDisplay = intent.getStringExtra("rds_text_display");
                    mRdsTextDisplay = mRdsTextDisplay.replaceAll("\n", " ");
                    mRdsValuePTY = intent.getIntExtra("rds_value_pty", 0);
                    mRdsAvailable = true;
                    mHandler.sendEmptyMessage(FM_RDS_DATA_AVAILABLE);
                } else if (action.equals(FMRadioPlayerService.FM_POWERON_SUCCESS)) {
                    Log.w(TAG, "Real FM power on success.");
                    if (pDialog_waitpoweron != null) {
                        Log.w(TAG, "poweron success, dismiss waitting dialog");
                        pDialog_waitpoweron.dismiss();
                        pDialog_waitpoweron = null;
                    }
                    if (!mbIsFMStart) {
                        mHandler.sendEmptyMessage(START_FMRADIO);
                        mbIsFMStart = true;
                    }
                } else if (action.equals(FMRadioPlayerService.FM_QUIT)) {
                    isExitFromUI = true;
                    finish();
                } else if (action.equals("com.motorola.fmradio.setvolume")) {
                    int eventAct = intent.getIntExtra("event_action", 0);
                    int eventKeyCode = intent.getIntExtra("event_keycode", 0);
                    if (eventAct == 0) {
                        onKeyDown(eventKeyCode, null);
                    } else if (eventAct == 1) {
                        onKeyUp(eventKeyCode, null);
                    }
                } else if (action.equals(FMRadioPlayerService.FM_AUDIO_MODE_CHANGED)) {
                    updateStereoStatus();
                }
            }
        };

        IntentFilter i = new IntentFilter(FMRadioPlayerService.FM_OPEN_SUCCEED);
        i.addAction(FMRadioPlayerService.FM_OPEN_FAILED);
        i.addAction(FMRadioPlayerService.FM_TUNE_SUCCEED);
        i.addAction(FMRadioPlayerService.FM_SEEK_SUCCEED);
        i.addAction(FMRadioPlayerService.FM_SEEK_FAILED);
        i.addAction(FMRadioPlayerService.FM_SEEK_SUCCEED_AND_REACHLIMIT);
        i.addAction(FMRadioPlayerService.FM_HW_ERROR_UNKNOWN);
        i.addAction(FMRadioPlayerService.FM_HW_ERROR_FRQ);
        i.addAction(FMRadioPlayerService.FM_RDS_DATA_AVAILABLE);
        i.addAction(FMRadioPlayerService.FM_QUIT);
        i.addAction(FMRadioPlayerService.FM_POWERON_SUCCESS);
        i.addAction("com.motorola.fmradio.setvolume");
        i.addAction(FMRadioPlayerService.FM_SCAN_SUCCEED);
        i.addAction(FMRadioPlayerService.FM_SCANNING);
        i.addAction(FMRadioPlayerService.FM_SCAN_FAILED);
        i.addAction(FMRadioPlayerService.FM_ABORT_COMPLETE);
        i.addAction(FMRadioPlayerService.FM_AUDIO_MODE_CHANGED);

        registerReceiver(mIntentReceiver, i);
    }

    private void resetTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
            mTimerTask = null;
        }
    }

    private void scanFMRadioStation() {
        displayRdsScrollText(false);
        if (mService != null) {
            try {
                isScanAll = mService.scan();
            } catch (RemoteException e) {
                Log.d(TAG, "Calling mService.scan(): RemoteException.!");
                isScanAll = false;
            }
        }
        if (isScanAll) {
            showDialog(DIALOG_PROGRESS);
        } else {
            Log.d(TAG, "scan request failed");
            Toast t = Toast.makeText(this, R.string.error_start_scan, Toast.LENGTH_LONG);
            t.setGravity(Gravity.CENTER, 0, 0);
            t.show();
        }
    }

    private void seekFMRadioStation(int freq, int direction) {
        Log.d(TAG, "seekFMRadioStation");
        displayRdsScrollText(false);
        if (mService != null) {
            try {
                mService.seek(freq, direction);
            } catch (RemoteException e) {
                Log.d(TAG, "In seekFMRadioStation(): RemoteException.!");
            }
        }
    }

    private void setFMRadioFrequency() {
        Log.d(TAG, "setFMRadioFrequency");
        displayRdsScrollText(false);
        if (mService != null) {
            try {
                mService.tune(mCurFreq);
            } catch (RemoteException e) {
                Log.d(TAG, "In setFMRadioFrequency(): RemoteException.!");
            }
        }
    }

    private void setFMRadioVolume(int volume) {
        Log.d(TAG, "setFMRadioVolume");
        if (volume > 15) {
            volume = 15;
        } else if (volume < 0) {
            volume = 0;
        }
        if (mService != null) {
            try {
                mService.setVolume(volume);
            } catch (RemoteException e) {
                Log.e(TAG, "set fmradio Volume failed");
            }
        }
    }

    private void setSeekBarProgress(int currentFreq) {
        float progress = currentFreq - RANGE_START;
        mSeekBar.setProgress((int) progress);
    }

    private void showNoticeDialog(int id) {
        Toast ts = Toast.makeText(this, id, Toast.LENGTH_LONG);
        ts.setGravity(Gravity.CENTER, 0, 0);
        ts.show();
    }

    private void showPowerWaitingToast(int id) {
        mPowerWaitingTS = Toast.makeText(this, id, Toast.LENGTH_LONG);
        mPowerWaitingTS.setGravity(Gravity.CENTER, 0, 0);
        mPowerWaitingTS.show();
    }

    private void updateDisplayPanel(int currentFreq, boolean isEditEnable) {
        Log.d(TAG, "enter updateDisplayPanel()");
        setSeekBarProgress(currentFreq);
        updateFrequencyDisplay(currentFreq, isEditEnable);
        updateStereoStatus();
        Log.d(TAG, "leave updateDisplayPanel()");
    }

    private void updateFrequencyDisplay(int currentFreq, boolean isEditEnable) {
        if (currentFreq < FMUtil.MIN_FREQUENCY || currentFreq > FMUtil.MAX_FREQUENCY) {
            return;
        }

        int digit1, digit2, digit3, digit4, freq = currentFreq;

        digit1 = freq / 100000;
        freq -= digit1 * 100000;
        digit2 = freq / 10000;
        freq -= digit2 * 10000;
        digit3 = freq / 1000;
        freq -= digit3 * 1000;
        digit4 = freq / 100;

        Log.v(TAG, "FMRadio updateDisplay: currentFreq " + currentFreq + " -> digits " +
                digit1 + " " + digit2 + " " + digit3 + " " + digit4);

        Integer[] numbers = isEditEnable ? NUMBER_IMAGES : NUMBER_IMAGES_UNSELECTED;
        int dot = isEditEnable ? R.drawable.fm_number_point : R.drawable.fm_number_unselect_point;

        Img_switcher1.setImageResource(numbers[digit1]);
        Img_switcher1.setVisibility(digit1 == 0 ? View.INVISIBLE : View.VISIBLE);
        Img_switcher2.setImageResource(numbers[digit2]);
        Img_switcher3.setImageResource(numbers[digit3]);
        Img_switcher4.setImageResource(dot);
        Img_switcher5.setImageResource(numbers[digit4]);
    }

    private void updateListView() {
        Log.d(TAG, "enter updateListView()");
        getDataFromDB();
        if (listModelView != null) {
            listModelView.notifyDataSetChanged();
        }
        Log.d(TAG, "leave updateListView()");
    }

    private void updatePresetSwitcher(int index) {
        if (index <= 0 || index > PRESET_NUM || preset_swt1 == null || preset_swt2 == null) {
            return;
        }
        int index1 = index / 10;
        int index2 = index - (index1 * 10);

        preset_swt_layout.setBackgroundResource(R.drawable.fm_playing_list_bg);
        preset_swt1.setImageResource(NUMBER_IMAGES_PRESET[index1]);
        preset_swt2.setImageResource(NUMBER_IMAGES_PRESET[index2]);
    }

    private boolean updatePresetSwitcher() {
        int index = -1;
        boolean rst = false;
        boolean isLandscape = getResources().getConfiguration().orientation == 2;
        if (mCurFreq > 0 && list_results != null && list_results.size() != 0) {
            String strPreset = FMUtil.getPresetStr(getContentResolver(), mCurFreq, isLandscape);
            if (strPreset != null) {
                for (int i = 0; i < list_results.size(); i++) {
                    if (strPreset.equals(list_results.get(i))) {
                        index = i + 1;
                        rst = true;
                    }
                }
            }
        }
        if (index > 0 && index <= PRESET_NUM) {
            updatePresetSwitcher(index);
            lastPosition = index - 1;
        } else {
            clearPresetSwitcher();
            lastPosition = -1;
        }
        updateListView();
        if (lastPosition >= 0) {
            isEdit = true;
            LvChannel.setSelection(lastPosition);
        } else {
            isEdit = false;
            LvChannel.setSelection(0);
        }

        return rst;
    }

    private void updateStereoStatus() {
        if (mService == null) {
            return;
        }
        ImageView imageStereoStatus = (ImageView) findViewById(R.id.stereo_status);
        if (imageStereoStatus == null) {
            return;
        }

        int mode = 0;
        try {
            mode = mService.getAudioMode();
        } catch (RemoteException e) {
            Log.e(TAG, "getAudioMode failed");
        }
        if (mode == 1) {
            imageStereoStatus.setVisibility(View.VISIBLE);
        } else if (mode == 0) {
            imageStereoStatus.setVisibility(View.INVISIBLE);
        }
    }

    public void clearDB() {
        for (int i = 0; i < PRESET_NUM; i++) {
            saveStationToDB(i, null, "", "");
        }
    }

    public boolean getDataFromDB() {
        Log.d(TAG, "Enter getDataFromDB(), will update data in list_results");
        boolean isLandscape = getResources().getConfiguration().orientation == 2;
        Cursor cursor = getContentResolver().query(FMUtil.CONTENT_URI, FMUtil.PROJECTION, null, null, null);
        if (cursor == null) {
            Log.e(TAG, "Query DB return cur_l null!");
            return false;
        }

        startManagingCursor(cursor);
        list_results = new ArrayList<String>();
        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            String chFreq = cursor.getString(FMUtil.FM_RADIO_INDEX_CHFREQ);
            String chName = cursor.getString(FMUtil.FM_RADIO_INDEX_CHNAME);
            String chRdsName = cursor.getString(FMUtil.FM_RADIO_INDEX_CHRDSNAME);
            String result_item = null;

            if (FMUtil.isEmptyStr(chName)) {
                if (FMUtil.isEmptyStr(chFreq)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("(");
                    sb.append(getString(R.string.empty));
                    sb.append(")");
                    result_item = sb.toString();
                } else {
                    if (!isLandscape) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(chFreq);
                        sb.append(getString(R.string.mhz));
                        if (!TextUtils.isEmpty(chRdsName)) {
                            sb.append(" ");
                            sb.append(chRdsName);
                        }
                        result_item = sb.toString();
                    } else if (!TextUtils.isEmpty(chRdsName)) {
                        result_item = chRdsName;
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append(chFreq);
                        sb.append(getString(R.string.mhz));
                        result_item = sb.toString();
                    }
                }
            }
            list_results.add(result_item.replaceAll("\n", " "));
            cursor.moveToNext();
        }
        Log.d(TAG, "Leave getDataFromDB()");
        cursor.close();
        return true;
    }

    public boolean isDBEmpty() {
        boolean ret = false;
        Cursor cur = getContentResolver().query(FMUtil.CONTENT_URI, FMUtil.PROJECTION, null, null, null);
        if (cur == null) {
            Log.d(TAG, "It was not possible to verify if DB is empty");
            return ret;
        }
        cur.moveToFirst();
        int i = 0;
        while (!cur.isAfterLast()) {
            String chFreq = cur.getString(FMUtil.FM_RADIO_INDEX_CHFREQ);
            if (FMUtil.isEmptyStr(chFreq)) {
                break;
            }
            i++;
            cur.moveToNext();
        }
        if (i + 1 == PRESET_NUM) {
            Log.d(TAG, "DB is Empty!");
            ret = true;
        }
        cur.close();
        return ret;
    }

    public View makeView() {
        ImageView i = new ImageView(this);
        i.setScaleType(ScaleType.CENTER_INSIDE);
        i.setLayoutParams(new ImageSwitcher.LayoutParams(-1, -1));
        return i;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SAVE_CODE:
                if (resultCode == RESULT_OK) {
                    if (data == null) {
                        return;
                    }
                    isEdit = true;
                    updateDisplayPanel(mCurFreq, true);
                    updatePresetSwitcher(lastPosition + 1);
                    updateListView();
                    int id = data.getIntExtra(FMSaveChannel.EXTRA_PRESET_ID, 0);
                    LvChannel.setSelection(id);
                    lastPosition = id;
                    int clickPosition = lastPosition - LvChannel.getFirstVisiblePosition();
                    isPerformClick = true;
                    Log.d("FMRadio save result", "click position " + clickPosition);
                } else {
                    isPerformClick = true;
                }
                break;
            case EDIT_CODE:
                if (resultCode == RESULT_OK) {
                    isEdit = true;
                    isPerformClick = false;
                    updateListView();
                } else {
                    lastPosition = tempPosition;
                    isPerformClick = true;
                }
                break;
            case CLEAR_CODE:
                if (resultCode == RESULT_OK) {
                    boolean clearedAll = data.getBooleanExtra(FMClearChannel.EXTRA_CLEARED_ALL, false);
                    if (clearedAll) {
                        Log.d(TAG, "Cleared all FM stations");
                        Preferences.setScanned(this, false);
                        lastPosition = 0;
                        isEdit = false;
                    }
                    updateListView();
                    LvChannel.setSelection(lastPosition);
                }
                isPerformClick = true;
                break;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_seekbackward:
                mShortPressButtonType = SHORTPRESS_BUTTON_REDUCE;
                handleSeekMsg(view, 1);
                enableUI(false);
                break;
            case R.id.btn_seekforward:
                mShortPressButtonType = SHORTPRESS_BUTTON_ADD;
                handleSeekMsg(view, 0);
                enableUI(false);
                break;
            case R.id.btn_reduce:
            case R.id.btn_add:
                enableUI(false);
                mRdsAvailable = false;
                displayRdsScrollText(false);
                isEdit = false;
                if (view.getId() == R.id.btn_reduce) {
                    mCurFreq -= FMUtil.STEP;
                    if (mCurFreq < FMUtil.MIN_FREQUENCY) {
                        mCurFreq = FMUtil.MAX_FREQUENCY;
                    }
                } else {
                    mCurFreq += FMUtil.STEP;
                    if (mCurFreq > FMUtil.MAX_FREQUENCY) {
                        mCurFreq = FMUtil.MIN_FREQUENCY;
                    }
                }
                setFMRadioFrequency();
                updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged() called");
        super.onConfigurationChanged(newConfig);

        if (mDirectQuit) {
            Log.w(TAG, "onDestroy() called mDirectQuit");
        } else {
            setContentView(R.layout.main);
            initResourceRefs();
            updateDisplayPanel(mCurFreq, updatePresetSwitcher());
            updateListView();
            enableUI(true);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        int pos = (int) info.id;
        ContentValues cv;
        Log.d(TAG, "onContextItemSelected, info.id = " + pos);

        switch (item.getItemId()) {
            case PLAY_MENU_ID:
                playClickPreset(LvChannel, pos);
                break;
            case EDIT_MENU_ID:
                editChannel(pos);
                break;
            case REPLACE_MENU_ID:
                boolean hasRds = mRdsAvailable && mRdsTextID.length() > 0;
                saveStationToDB(pos, mCurFreq, hasRds ? null : "", hasRds ? mRdsTextID : "");
                updateDisplayPanel(mCurFreq, true);
                updatePresetSwitcher();
                lastPosition = pos;
                isEdit = true;
                updateListView();
                break;
            case CLEAR_MENU_ID:
                saveStationToDB(pos, null, "", "");
                isPerformClick = true;
                updateListView();
                updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                break;
        }

        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "**************FMRadioMain Activity onCreate() called!****************");
        super.onCreate(savedInstanceState);

        requestWindowFeature(1);
        mDirectQuit = true;

        boolean isAirplaneModeOn = Settings.System.getInt(getContentResolver(), "airplane_mode_on", 0) == 1;
        if (isAirplaneModeOn) {
            showNoticeDialog(R.string.fmradio_airplane_mode_enable_at_begin);
            Log.e(TAG, "airplane mode enable, fmradio can not start!");
            finish();
            return;
        }

        mAudioMgr = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (!mAudioMgr.isWiredHeadsetOn()) {
            showNoticeDialog(R.string.fmradio_no_headset_at_begin);
            Log.w(TAG, "no headset, fmradio can not start!");
            finish();
            return;
        }

        mDirectQuit = false;
        onCreateInternal();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        final DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    onKeyDown(keyCode, event);
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    onKeyUp(keyCode, event);
                    return true;
                }
                return false;
            }
        };

        switch (id) {
            case DIALOG_PROGRESS:
                pDialog = new ProgressDialog(this);
                pDialog.setOnKeyListener(keyListener);
                pDialog.setTitle(getString(R.string.fmradio_scanning_title));
                pDialog.setMessage(getString(R.string.fmradio_scan_begin_msg));
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(true);
                isScanCanceled = false;
                pDialog.setButton(getText(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        pDialog.cancel();
                    }
                });
                pDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        boolean result = false;
                        Log.d("FMRadio Progress_Dialog", "call onCancel");
                        try {
                            result = mService.stopScan();
                        } catch (RemoteException e) {
                            Log.e(TAG, "stopScan Failed: " + e.getMessage());
                        }
                        if (result) {
                            pDialog.dismiss();
                            isScanAll = false;
                            isScanCanceled = true;
                            ignoreRdsEvent(false);
                            if (lastIcon != null) {
                                lastIcon.setVisibility(View.INVISIBLE);
                            }
                            updateListView();
                            LvChannel.setSelection(0);
                            lastPosition = 0;
                            enableUI(true);
                        } else {
                            Log.e(TAG, "stopScan Failed so do not dismiss or update UI. Scan continuing");
                        }
                    }
                });
                return pDialog;
            case DIALOG_SCAN_FINISH:
                return new AlertDialog.Builder(this)
                        .setOnKeyListener(keyListener)
                        .setTitle("Scan Completed")
                        .setMessage("Scaned and Saved " + count_save + " channels")
                        .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        })
                        .create();
            case DIALOG_SCAN_CANCEL:
                return new AlertDialog.Builder(this)
                        .setOnKeyListener(keyListener)
                        .setTitle("Scan Canceled")
                        .setMessage("Scaned and Saved " + count_save + " channels")
                        .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        })
                        .create();
            case DIALOG_IF_SCAN_FIRST:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.scan)
                        .setOnKeyListener(keyListener)
                        .setMessage(R.string.fmradio_scan_confirm_msg)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                Preferences.setScanned(FMRadioMain.this, true);
                                mCurFreq = FMUtil.MIN_FREQUENCY;
                                ignoreRdsEvent(true);
                                mWakeLock.acquire(LIGHT_ON_TIME);
                                scanFMRadioStation();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                isScanAll = false;
                                ignoreRdsEvent(false);
                            }
                        })
                        .create();
            case DIALOG_IF_SCAN_NEXT:
                return new AlertDialog.Builder(this)
                        .setOnKeyListener(keyListener)
                        .setTitle(R.string.scan)
                        .setMessage(R.string.fmradio_clear_confirm_msg)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                clearDB();
                                mCurFreq = FMUtil.MIN_FREQUENCY;
                                ignoreRdsEvent(true);
                                mWakeLock.acquire(LIGHT_ON_TIME);
                                scanFMRadioStation();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ignoreRdsEvent(false);
                            }
                        })
                        .create();
        }

        return null;
     }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy() called");
        super.onDestroy();

        resetTimer();
        mHandler.removeMessages(SEEK_NEXT);
        if (mDirectQuit) {
            Log.w(TAG, "onDestroy() called mDirectQuit");
            return;
        }

        ignoreRdsEvent(false);
        unbindFMRadioService();
        mbIsFMStart = false;
        mService = null;
        mPowerWaitingTS = null;
        unregisterReceiver(mIntentReceiver);
        mWakeLock.release();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyDown(keyCode, event);
        }
        int index = volume;
        if (mRingerStreamIcon != null) {
            mRingerStreamIcon.setImageResource(index == 0 ? R.drawable.ic_mp_mute : R.drawable.ic_mp_unmute);
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volume++;
            if (volume > 15) {
                volume = 15;
            }
        } else {
            volume--;
            if (volume < 0) {
                volume = 0;
            }
        }

        setFMRadioVolume(volume);
        Preferences.setVolume(this, volume);
        mLevel.setProgress(index);
        mToast.setView(volumeView);
        mToast.setDuration(Toast.LENGTH_SHORT);
        mToast.setGravity(Gravity.CENTER, 0, 0);
        mToast.show();
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyUp(keyCode, event);
        }
        if (mRingerStreamIcon != null) {
            mRingerStreamIcon.setImageResource(volume == 0 ? R.drawable.ic_mp_mute : R.drawable.ic_mp_unmute);
        }
        mLevel.setProgress(volume);

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onLongClick(View v) {
        switch (v.getId()) {
            case R.id.btn_seekbackward:
                Log.d(TAG, "Start long press seek backward.");
                mbIsLongPressed = true;
                mLongPressButtonType = LONGPRESS_BUTTON_REDUCE;
                ignoreRdsEvent(true);
                handleSeekMsg(v, 1);
                return true;
            case R.id.btn_reduce:
                enableActiveButton(v);
                mRdsAvailable = false;
                displayRdsScrollText(false);
                mbIsLongPressed = true;
                mCurFreq -= FMUtil.STEP;
                if (mCurFreq < FMUtil.MIN_FREQUENCY) {
                    mCurFreq = FMUtil.MAX_FREQUENCY;
                }
                updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                mTimer = new Timer();
                mTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        mHandler.sendEmptyMessage(FM_FREQ_REDUCE);
                    }
                };
                mTimer.schedule(mTimerTask, LONG_PRESS_TUNE_TIMEOUT);
                return true;
            case R.id.btn_add:
                enableActiveButton(v);
                mRdsAvailable = false;
                displayRdsScrollText(false);
                mbIsLongPressed = true;
                mCurFreq += FMUtil.STEP;
                if (mCurFreq > FMUtil.MAX_FREQUENCY) {
                    mCurFreq = FMUtil.MIN_FREQUENCY;
                }
                updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                mTimer = new Timer();
                mTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        mHandler.sendEmptyMessage(FM_FREQ_ADD);
                    }
                };
                mTimer.schedule(mTimerTask, LONG_PRESS_TUNE_TIMEOUT);
                return true;
            case R.id.btn_seekforward:
                Log.d(TAG, "Start long press seek forward.");
                mbIsLongPressed = true;
                ignoreRdsEvent(true);
                mLongPressButtonType = LONGPRESS_BUTTON_ADD;
                handleSeekMsg(v, 0);
                return true;
        }

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mService == null) {
            Log.e(TAG, "The connection between FMRadioUI and service had been disconnected.");
            return false;
        }
        switch (item.getItemId()) {
            case SAVE_ID:
                tempPosition = lastPosition;
                saveChannel(getIndexOfEmptyItem());
                break;
            case EDIT_ID:
                tempPosition = lastPosition;
                editChannel(lastPosition);
                break;
            case CLEAR_ID:
                Intent clearIntent = new Intent(this, FMClearChannel.class);
                startActivityForResult(clearIntent, CLEAR_CODE);
                break;
            case EXIT_ID:
                Log.d(TAG, "User click Exit Menu to exit FM");
                Preferences.setEnabled(this, false);
                isExitFromUI = true;
                finish();
                break;
            case SCAN_SAVE_ID:
                if (isDBEmpty()) {
                    mCurFreq = FMUtil.MIN_FREQUENCY;
                    mWakeLock.acquire(LIGHT_ON_TIME);
                    scanFMRadioStation();
                } else {
                    showDialog(4);
                }
                break;
            case BY_LOUDSPEAKER_ID:
                Log.d(TAG, "setRouting done in java FMRadioPlayer service!");
                try {
                    mService.setAudioRouting(FMRadioPlayerService.AudioManager_ROUTE_FM_SPEAKER);
                } catch (RemoteException e) {
                    Log.e(TAG, "set Audio Routing failed");
                }
                break;
            case BY_HEADSET_ID:
                Log.d(TAG, "setRouting done in java FMRadioPlayer service!");
                try {
                    mService.setAudioRouting(FMRadioPlayerService.AudioManager_ROUTE_FM_HEADSET);
                } catch (RemoteException e) {
                    Log.e(TAG, "set Audio Routing failed");
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause() called");
        super.onPause();

        if (mDirectQuit) {
            Log.w(TAG, "onDestroy() called mDirectQuit");
            return;
        }

        ignoreRdsEvent(false);

        Preferences.setLastFrequency(this, mCurFreq);
        Preferences.setLastChannel(this, lastPosition);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case 0:
                isScanCanceled = false;
                if (pDialog != null) {
                    pDialog.setMessage(getString(R.string.fmradio_scan_begin_msg));
                }
                break;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mService == null) {
            Log.e(TAG, "The connection between FMRadioUI and service had been disconnected.");
            return false;
        }
        super.onPrepareOptionsMenu(menu);

        boolean fmRadioEnabled = Preferences.isEnabled(this);

        menu.clear();
        menu.add(Menu.NONE, CLEAR_ID, Menu.FIRST + 1, R.string.clear_presets).setIcon(R.drawable.ic_menu_clear_channel);
        menu.add(Menu.NONE, EXIT_ID, Menu.FIRST + 4, R.string.exit).setIcon(R.drawable.ic_menu_exit);
        if (isEdit && fmRadioEnabled) {
            menu.add(Menu.NONE, EDIT_ID, Menu.FIRST, R.string.edit_preset).setIcon(R.drawable.ic_menu_edit_preset);
        } else if (!isEdit) {
            menu.add(Menu.NONE, SAVE_ID, Menu.FIRST, R.string.save_preset).setIcon(R.drawable.ic_menu_save_channel);
        }
        if (fmRadioEnabled) {
            menu.add(Menu.NONE, SCAN_SAVE_ID, Menu.FIRST + 3, R.string.scan).setIcon(R.drawable.ic_menu_save_channel);
        }

        int audioRouting = 0;
        try {
            audioRouting = mService.getAudioRouting();
        } catch (RemoteException e) {
            Log.e(TAG, "getAudioRouting failed");
        }
        if (audioRouting == FMRadioPlayerService.AudioManager_ROUTE_FM_HEADSET) {
            menu.add(Menu.NONE, BY_LOUDSPEAKER_ID, Menu.FIRST + 2, R.string.by_loudspeaker).setIcon(R.drawable.ic_menu_loud_speaker);
        } else if (audioRouting == FMRadioPlayerService.AudioManager_ROUTE_FM_SPEAKER) {
            menu.add(Menu.NONE, BY_HEADSET_ID, Menu.FIRST + 2, R.string.by_headset).setIcon(R.drawable.ic_menu_header);
        }
        return true;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        Log.d(TAG, "progress is " + progress);
        Log.d(TAG, "Current Frequency is " + mCurFreq);
        Log.d(TAG, "progress fromTouch " + fromTouch);

        if (fromTouch) {
            int step = (progress / FMUtil.STEP) * FMUtil.STEP;
            Log.d(TAG, "step is " + step);
            mCurFreq = RANGE_START + step;
            Log.d(TAG, "Updated Frequency is " + mCurFreq);
            if (mCurFreq < FMUtil.MIN_FREQUENCY) {
                setProgress(FMUtil.MIN_FREQUENCY - RANGE_START);
                mCurFreq = FMUtil.MIN_FREQUENCY;
            } else if (mCurFreq > FMUtil.MAX_FREQUENCY) {
                setProgress(RANGE);
                mCurFreq = FMUtil.MAX_FREQUENCY;
            }
            updateFrequencyDisplay(mCurFreq, false);
            updateDisplayPanel(mCurFreq, updatePresetSwitcher());
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart() called");
        super.onStart();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() called");
        super.onStop();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (mTuneSucceed) {
            setFMRadioFrequency();
            mTuneSucceed = false;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.d(TAG, "event:" + event.getAction() + ";v.getId:" + v.getId());
        if (event.getAction() == 1 && mbIsLongPressed) {
            mbIsLongPressed = false;
            mLongPressButtonType = LONGPRESS_BUTTON_NONE;
            enableUI(false);
            if (v.getId() == R.id.btn_add || v.getId() == R.id.btn_reduce) {
                Log.d(TAG, "Release button of frequency add or reduce.");
                resetTimer();
                setFMRadioFrequency();
                return true;
            } else if (v.getId() == R.id.btn_seekbackward || v.getId() == R.id.btn_seekforward) {
                Log.d(TAG, "Release button of seek forward or backward.");
                boolean bSeekAbortStatus = false;
                isSeekStarted = false;
                resetTimer();
                if (mService != null) {
                    try {
                        bSeekAbortStatus = mService.stopSeek();
                    } catch (RemoteException e) {
                        Log.e(TAG, "There is a exception to stop seek.");
                    }
                    if (bSeekAbortStatus) {
                        Log.w(TAG, "Abort seek success.");
                        mCurFreq = mPreFreq;
                        setFMRadioFrequency();
                    } else {
                        Log.e(TAG, "Abort seek failed.");
                    }
                    ignoreRdsEvent(false);
                }
                Log.w(TAG, "Seek was aborted, start stop annimation.");
                updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                ScanStopThread sThread = new ScanStopThread();
                mHandler.postDelayed(sThread, SCAN_STOP_DELAY);
                return true;
            }
        }
        return false;
    }

    private void saveStationToDB(int id, Integer freq, String name, String rdsName) {
        ContentValues cv = new ContentValues();
        cv.put("ID", Integer.toString(id));
        if (freq != null) {
            cv.put("CH_Freq", Float.toString((float) freq / 1000.0F));
        } else {
            cv.put("CH_Freq", "");
        }
        if (name != null) {
            cv.put("CH_Name", name);
        }
        if (rdsName != null) {
            cv.put("CH_RdsName", rdsName != null ? rdsName : "");
        }

        getContentResolver().update(FMUtil.CONTENT_URI, cv, "ID=" + id, null);
    }

    private void signalPresetChanged(int preset) {
        Intent intent = new Intent(PRESET_CHANGED);
        if (preset >= 0) {
            intent.putExtra(PRESET, preset);
        }
        sendBroadcast(intent);
    }

    private void saveChannel(int position) {
        Intent saveIntent = new Intent(this, FMSaveChannel.class);
        saveIntent.putExtra(FMSaveChannel.EXTRA_FREQUENCY, mCurFreq);
        saveIntent.putExtra(FMSaveChannel.EXTRA_PRESET_ID, position);
        saveIntent.putExtra(FMSaveChannel.EXTRA_RDS_NAME, mRdsTextID);
        startActivityForResult(saveIntent, SAVE_CODE);
    }

    private void editChannel(int position) {
        Intent editIntent = new Intent(this, FMEditChannel.class);
        editIntent.putExtra(FMEditChannel.EXTRA_PRESET, position);
        startActivityForResult(editIntent, EDIT_CODE);
    }
}
