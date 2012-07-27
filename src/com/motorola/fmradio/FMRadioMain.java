
package com.motorola.fmradio;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.motorola.fmradio.FMDataProvider.Channels;

import java.text.MessageFormat;

public class FMRadioMain extends Activity implements SeekBar.OnSeekBarChangeListener,
        View.OnClickListener, View.OnLongClickListener, View.OnTouchListener,
        ImageSwitcher.ViewFactory, OnSharedPreferenceChangeListener {
    private static final String TAG = "FMRadioMain";

    private static int LIGHT_ON_TIME = 90000;
    private static int PRESET_NUM = 20;

    private static final int DIALOG_POWERON = 0;
    private static final int DIALOG_SCAN_PROGRESS = 1;
    private static final int DIALOG_IF_SCAN_FIRST = 2;
    private static final int DIALOG_IF_SCAN_NEXT = 3;
    private static final int DIALOG_SAVE_CHANNEL = 4;
    private static final int DIALOG_EDIT_CHANNEL = 5;

    private static final String ARG_FREQUENCY = "frequency";
    private static final String ARG_PRESET = "preset";
    private static final String ARG_NAME = "name";

    public static final int PLAY_MENU_ID = 1;
    public static final int EDIT_MENU_ID = 2;
    public static final int REPLACE_MENU_ID = 3;
    public static final int CLEAR_MENU_ID = 4;

    public static final int SAVE_ID = 1;
    public static final int EDIT_ID = 2;
    public static final int CLEAR_ID = 3;
    public static final int PREFS_ID = 4;
    public static final int EXIT_ID = 5;
    public static final int SCAN_SAVE_ID = 6;
    public static final int BY_LOUDSPEAKER_ID = 7;
    public static final int BY_HEADSET_ID = 8;

    private static final int CLEAR_CODE = 0;

    private static final int MSG_POWERON_COMPLETE = 1;
    private static final int MSG_TUNE_FINISHED = 2;
    private static final int MSG_SEEK_FINISHED = 3;
    private static final int MSG_SCAN_FINISHED = 4;
    private static final int MSG_STATION_SCANNED = 5;
    private static final int MSG_SEEK_SCAN_ABORTED = 6;
    private static final int MSG_ERROR = 7;
    private static final int MSG_AUDIO_MODE_CHANGED = 8;
    private static final int MSG_CONTINUE_SEEK = 9;
    private static final int MSG_CONTINUE_TUNE = 10;
    private static final int MSG_STOP_SCAN_ANIMATION = 11;

    private int RANGE = 21000;
    private int RANGE_START = 87000;
    private int RATE = 1000;

    private static final String RDS_TEXT_SEPARATOR = "..:";

    private static final int LONG_PRESS_SEEK_TIMEOUT = 1500;
    private static final int LONG_PRESS_TUNE_TIMEOUT = 50;
    private static final long SCAN_STOP_DELAY = 500;

    private static final int[] NUMBER_IMAGES = new int[] {
        R.drawable.fm_number_0, R.drawable.fm_number_1, R.drawable.fm_number_2,
        R.drawable.fm_number_3, R.drawable.fm_number_4, R.drawable.fm_number_5,
        R.drawable.fm_number_6, R.drawable.fm_number_7, R.drawable.fm_number_8,
        R.drawable.fm_number_9
    };
    private static final int[] NUMBER_IMAGES_UNSELECTED = new int[] {
        R.drawable.fm_number_unselect_0, R.drawable.fm_number_unselect_1,
        R.drawable.fm_number_unselect_2, R.drawable.fm_number_unselect_3,
        R.drawable.fm_number_unselect_4, R.drawable.fm_number_unselect_5,
        R.drawable.fm_number_unselect_6, R.drawable.fm_number_unselect_7,
        R.drawable.fm_number_unselect_8, R.drawable.fm_number_unselect_9
    };
    private static final int[] NUMBER_IMAGES_PRESET = new int[] {
        R.drawable.fm_playing_list_0, R.drawable.fm_playing_list_1,
        R.drawable.fm_playing_list_2, R.drawable.fm_playing_list_3,
        R.drawable.fm_playing_list_4, R.drawable.fm_playing_list_5,
        R.drawable.fm_playing_list_6, R.drawable.fm_playing_list_7,
        R.drawable.fm_playing_list_8, R.drawable.fm_playing_list_9
    };
    private static final int[] PTY_STRINGS = new int[] {
        0,                       R.string.fm_pty_list_01, R.string.fm_pty_list_02,
        R.string.fm_pty_list_03, R.string.fm_pty_list_04, R.string.fm_pty_list_05,
        R.string.fm_pty_list_06, R.string.fm_pty_list_07, R.string.fm_pty_list_08,
        R.string.fm_pty_list_09, R.string.fm_pty_list_10, R.string.fm_pty_list_11,
        R.string.fm_pty_list_12, R.string.fm_pty_list_13, R.string.fm_pty_list_14,
        R.string.fm_pty_list_15, R.string.fm_pty_list_16, R.string.fm_pty_list_17,
        R.string.fm_pty_list_18, R.string.fm_pty_list_19, R.string.fm_pty_list_20,
        R.string.fm_pty_list_21, R.string.fm_pty_list_22, R.string.fm_pty_list_23,
        R.string.fm_pty_list_24, R.string.fm_pty_list_25, R.string.fm_pty_list_26,
        R.string.fm_pty_list_27, R.string.fm_pty_list_28, R.string.fm_pty_list_29,
        R.string.fm_pty_list_30, R.string.fm_pty_list_31, 0,
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

    private ImageButton[] mSeekButtons;
    private ImageSwitcher[] mFreqDigits;
    private ImageSwitcher[] mPresetDigits;
    private LinearLayout mPresetLayout;
    private SeekBar mSeekBar;
    private TextView mRdsMarqueeText;
    private ImageView mScanBar;
    private AnimationDrawable mScanAnimation;
    private ImageView mStereoStatus;

    private Cursor mCursor;
    private ListView mChannelList;
    private ActionBar mActionBar;

    private IFMRadioPlayerService mService = null;
    private boolean mIsBound = false;
    private WakeLock mWakeLock;
    private AudioManager mAM;

    private String mRdsStationName;
    private String mRdsRadioText;
    private int mRdsPTYValue;

    private int mCurFreq = FMUtil.MIN_FREQUENCY;
    private int mPreFreq = FMUtil.MIN_FREQUENCY;
    private boolean mScanning = false;
    private int mScannedStations = -1;
    private int mLongPressedButton = 0;

    private class ChannelListAdapter extends ResourceCursorAdapter {
        private class ViewHolder {
            private TextView mName;
            private ImageView mPeakOne;
            private ImageView mPeakTwo;
            private TextView mFrequency;
            private AnimationDrawable mPeakOneAnimation;
            private AnimationDrawable mPeakTwoAnimation;

            public ViewHolder(View view) {
                mName = (TextView) view.findViewById(R.id.list_name);
                // mIcon = (ImageView) view.findViewById(R.id.list_icon);
                mFrequency = (TextView) view.findViewById(R.id.list_frequency);
                mPeakOne = (ImageView) view.findViewById(R.id.peak_one);
                mPeakTwo = (ImageView) view.findViewById(R.id.peak_two);
            }

            public void bind(Context context, Cursor cursor) {
                int frequency = cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ);
                boolean selected = cursor.getPosition() == mChannelList.getCheckedItemPosition();

                mFrequency.setText(FMUtil.formatFrequency(mContext, frequency));
                mName.setText(FMUtil.getPresetListString(context, cursor));

                if (selected && Integer.valueOf(frequency) != 0) {
                    mPeakOne.setVisibility(View.VISIBLE);
                    mPeakTwo.setVisibility(View.VISIBLE);
                    mPeakOne.setImageResource(R.anim.peak_meter_1);
                    mPeakTwo.setImageResource(R.anim.peak_meter_2);
                    mPeakOneAnimation = (AnimationDrawable) mPeakOne.getDrawable();
                    mPeakTwoAnimation = (AnimationDrawable) mPeakTwo.getDrawable();
                    mPeakOneAnimation.start();
                    mPeakTwoAnimation.start();
                } else {
                    mPeakOne.setVisibility(View.INVISIBLE);
                    mPeakTwo.setVisibility(View.INVISIBLE);
                    mPeakOne.setImageResource(0);
                    mPeakTwo.setImageResource(0);
                }
            }
        }

        public ChannelListAdapter(Context context, Cursor cursor) {
            super(context, R.layout.listview_row, cursor);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder holder = (ViewHolder) view.getTag();
            if (holder == null) {
                holder = new ViewHolder(view);
                view.setTag(holder);
            }
            holder.bind(context, cursor);
        }
    }

    private ServiceConnection mServConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "Connected to player service");
            mService = IFMRadioPlayerService.Stub.asInterface(service);

            if (mService != null) {
                try {
                    mService.registerCallbacks(mServiceCallbacks);
                } catch (RemoteException e) {
                    Log.e(TAG, "Could not register with service", e);
                    mService = null;
                    return;
                }
            }
            if (mService == null) {
                unbindService();
                finish();
                return;
            }

            boolean success = false;
            try {
                success = mService.powerOn();
            } catch (RemoteException e) {
                Log.e(TAG, "Could not check FM power status", e);
            }
            if (success) {
                Log.v(TAG, "Waiting for FM service to come up");
                showDialog(DIALOG_POWERON);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.v(TAG, "Disconnected from player service");
            mService = null;
            finish();
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            final Context context = FMRadioMain.this;

            switch (msg.what) {
                case MSG_POWERON_COMPLETE:
                    if (msg.arg1 != 0) {
                        Log.d(TAG, "FM radio powered on successfully");
                        dismissDialog(DIALOG_POWERON);
                        enableUI(true);
                        mAM.setStreamVolume(AudioManager.STREAM_FM, Preferences.getVolume(context), 0);
                        if (isDBEmpty() || !Preferences.isScanned(context)) {
                            showDialog(DIALOG_IF_SCAN_FIRST);
                        }
                    }
                    Preferences.setEnabled(context, msg.arg1 != 0);
                    break;
                case MSG_TUNE_FINISHED:
                    mCurFreq = msg.arg1;
                    Log.d(TAG, "FM tune succeeded");
                    displayRdsScrollText(false);
                    enableUI(true);
                    updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                    break;
                case MSG_STATION_SCANNED:
                    handleScannedStation(msg.arg1);
                    break;
                case MSG_SCAN_FINISHED:
                    mCurFreq = msg.arg1;
                    handleScanFinished(false);
                    break;
                case MSG_SEEK_SCAN_ABORTED:
                    mCurFreq = msg.arg1;
                    if (mScannedStations >= 0) {
                        /* scan was aborted */
                        handleScanFinished(true);
                    } else {
                        /* seek was aborted */
                        handleSeekFinished(true);
                    }
                    break;
                case MSG_SEEK_FINISHED:
                    mCurFreq = msg.arg1;
                    handleSeekFinished(false);
                    break;
                case MSG_AUDIO_MODE_CHANGED:
                    boolean stereo = msg.arg1 != 0;
                    mStereoStatus.setVisibility(stereo ? View.VISIBLE : View.INVISIBLE);
                    break;
                case MSG_ERROR:
                    Log.d(TAG, "FM error");
                    enableUI(true);
                    break;
                case MSG_CONTINUE_TUNE:
                    if (msg.arg1 != 0) {
                        mCurFreq += FMUtil.STEP;
                        if (mCurFreq > FMUtil.MAX_FREQUENCY) {
                            mCurFreq = FMUtil.MIN_FREQUENCY;
                        }
                    } else {
                        mCurFreq -= FMUtil.STEP;
                        if (mCurFreq < FMUtil.MIN_FREQUENCY) {
                            mCurFreq = FMUtil.MAX_FREQUENCY;
                        }
                    }
                    updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                    mHandler.sendMessageDelayed(Message.obtain(msg), LONG_PRESS_TUNE_TIMEOUT);
                    break;
                case MSG_CONTINUE_SEEK:
                    startSeek(0, msg.arg1 != 0);
                    break;
                case MSG_STOP_SCAN_ANIMATION:
                    if (mScanBar.getVisibility() == View.VISIBLE) {
                        mScanAnimation.stop();
                        showSeekBar(false);
                        showSeekAnimation(false);
                        enableUI(true);
                    }
                    break;
            }
        }
    };

    private IFMRadioPlayerServiceCallbacks.Stub mServiceCallbacks = new IFMRadioPlayerServiceCallbacks.Stub() {
        @Override
        public void onEnabled(boolean success) {
            Message msg = Message.obtain(mHandler, MSG_POWERON_COMPLETE, success ? 1 : 0, 0, null);
            mHandler.sendMessage(msg);
        }

        @Override
        public void onDisabled() {
            finish();
        }

        @Override
        public void onTuneChanged(boolean success, int newFrequency) {
            if (success) {
                Message msg = Message.obtain(mHandler, MSG_TUNE_FINISHED, newFrequency, 0, null);
                mHandler.sendMessage(msg);
            } else {
                mHandler.sendEmptyMessage(MSG_ERROR);
            }
        }

        @Override
        public void onSeekFinished(boolean success, int newFrequency) {
            Message msg = Message.obtain(mHandler, MSG_SEEK_FINISHED, newFrequency, success ? 1 : 0, null);
            mHandler.sendMessage(msg);
        }

        @Override
        public void onScanUpdate(int newFrequency) {
            Message msg = Message.obtain(mHandler, MSG_STATION_SCANNED, newFrequency, 0, null);
            mHandler.sendMessage(msg);
        }

        @Override
        public void onScanFinished(boolean success, int newFrequency) {
            Message msg = Message.obtain(mHandler, MSG_SCAN_FINISHED, newFrequency, success ? 1 : 0, null);
            mHandler.sendMessage(msg);
        }

        @Override
        public void onAbortComplete(int newFrequency) {
            Message msg = Message.obtain(mHandler, MSG_SEEK_SCAN_ABORTED, newFrequency, 0, null);
            mHandler.sendMessage(msg);
        }

        @Override
        public void onError() {
            mHandler.sendEmptyMessage(MSG_ERROR);
        }

        @Override
        public void onRdsDataChanged(final int frequency, final String stationName,
                final String radioText, final int pty) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    boolean valid = frequency == mCurFreq;
                    mRdsStationName = valid ? stationName : null;
                    mRdsRadioText = valid && radioText != null ? radioText.replaceAll("\n", " ") : null;
                    mRdsPTYValue = valid ? pty : 0;
                    handleRdsDataChanged();
                }
            });
        }

        @Override
        public void onAudioModeChanged(boolean stereo) {
            Message msg = Message.obtain(mHandler, MSG_AUDIO_MODE_CHANGED, stereo ? 1 : 0, 0, null);
            mHandler.sendMessage(msg);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        mActionBar = getActionBar();

        setContentView(R.layout.main);
        setVolumeControlStream(AudioManager.STREAM_FM);

        mAM = (AudioManager) getSystemService(AUDIO_SERVICE);
        mCurFreq = Preferences.getLastFrequency(this);

        initUI();
        mIsBound = bindToService();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, getClass().getName());
        mWakeLock.setReferenceCounted(false);
        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();

        unbindService();
        mService = null;
        mWakeLock.release();
    }

    @Override
    protected void onResume() {
        Preferences.getPrefs(this).registerOnSharedPreferenceChangeListener(this);

        // Hide Action bar if user prefers so.
        if (Preferences.isActionBarHidden(this)) {
            mActionBar.hide();
        } else {
            mActionBar.show();
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();

        Preferences.getPrefs(this).unregisterOnSharedPreferenceChangeListener(this);
        Preferences.setLastFrequency(this, mCurFreq);
        Preferences.setLastChannel(this, getSelectedPreset());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged()");
        super.onConfigurationChanged(newConfig);

        setContentView(R.layout.main);
        initUI();
        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
        enableUI(true);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        if (id == DIALOG_SAVE_CHANNEL) {
            SaveChannelDialog d = (SaveChannelDialog) dialog;
            int frequency = args.getInt(ARG_FREQUENCY, FMUtil.MIN_FREQUENCY);
            int preset = args.getInt(ARG_PRESET, 0);
            String name = args.getString(ARG_NAME);

            d.initialize(frequency, preset, name);
        } else if (id == DIALOG_EDIT_CHANNEL) {
            EditChannelDialog d = (EditChannelDialog) dialog;
            int preset = args.getInt(ARG_PRESET, 0);

            d.setPreset(preset);
        } else if (id == DIALOG_SCAN_PROGRESS) {
            ProgressDialog d = (ProgressDialog) dialog;
            final String message = MessageFormat.format(getString(R.string.scan_progress), mScannedStations);

            d.setMessage(message);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case DIALOG_SAVE_CHANNEL:
                return new SaveChannelDialog(this, new SaveChannelDialog.OnSaveListener() {
                    @Override
                    public void onPresetSaved(int id) {
                        updatePresetSwitcher(id + 1);
                        setSelectedPreset(id);
                    }

                    @Override
                    public void onSaveCanceled() {
                        updatePresetSwitcher();
                    }
                });
            case DIALOG_EDIT_CHANNEL:
                return new EditChannelDialog(this);
            case DIALOG_POWERON: {
                ProgressDialog dialog = new ProgressDialog(this);
                dialog.setMessage(getString(R.string.fmradio_waiting_for_power_on));
                dialog.setIndeterminate(true);
                dialog.setCancelable(true);
                return dialog;
            }
            case DIALOG_SCAN_PROGRESS: {
                ProgressDialog dialog = new ProgressDialog(this);
                dialog.setTitle(getString(R.string.fmradio_scanning_title));
                dialog.setIndeterminate(false);
                dialog.setCancelable(true);
                dialog.setButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.cancel();
                    }
                });
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        cancelScan();
                    }
                });
                return dialog;
            }
            case DIALOG_IF_SCAN_FIRST:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.scan)
                        .setMessage(R.string.fmradio_scan_confirm_msg)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                startScanning();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
            case DIALOG_IF_SCAN_NEXT:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.scan)
                        .setMessage(R.string.fmradio_clear_confirm_msg)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                clearDB();
                                startScanning();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
        }

        return null;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mService == null) {
            return false;
        }
        super.onPrepareOptionsMenu(menu);

        boolean fmRadioEnabled = Preferences.isEnabled(this);
        boolean canEditPreset = getSelectedPreset() >= 0;

        menu.clear();
        menu.add(Menu.NONE, CLEAR_ID, Menu.FIRST + 1, R.string.clear_presets).setIcon(R.drawable.ic_menu_clear_channel);
        menu.add(Menu.NONE, PREFS_ID, Menu.FIRST + 4, R.string.settings_title).setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(Menu.NONE, EXIT_ID, Menu.FIRST + 5, R.string.exit).setIcon(R.drawable.ic_menu_exit);
        if (canEditPreset && fmRadioEnabled) {
            menu.add(Menu.NONE, EDIT_ID, Menu.FIRST, R.string.edit_preset).setIcon(R.drawable.ic_menu_edit_preset);
        } else if (!canEditPreset) {
            menu.add(Menu.NONE, SAVE_ID, Menu.FIRST, R.string.save_preset).setIcon(R.drawable.ic_menu_save_channel);
        }
        if (fmRadioEnabled) {
            menu.add(Menu.NONE, SCAN_SAVE_ID, Menu.FIRST + 3, R.string.scan).setIcon(R.drawable.ic_menu_save_channel);
        }

        int audioRouting = 0;
        try {
            audioRouting = mService.getAudioRouting();
        } catch (RemoteException e) {
            Log.e(TAG, "Getting audio routing failed", e);
        }
        if (audioRouting == FMRadioPlayerService.FM_ROUTING_HEADSET) {
            menu.add(Menu.NONE, BY_LOUDSPEAKER_ID, Menu.FIRST + 2, R.string.by_loudspeaker).setIcon(R.drawable.ic_menu_loud_speaker);
        } else {
            MenuItem headsetItem = menu.add(Menu.NONE, BY_HEADSET_ID, Menu.FIRST + 2, R.string.by_headset);
            headsetItem.setIcon(R.drawable.ic_menu_header);
            if (audioRouting == FMRadioPlayerService.FM_ROUTING_SPEAKER_ONLY) {
                headsetItem.setEnabled(false);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mService == null) {
            return false;
        }
        switch (item.getItemId()) {
            case SAVE_ID:
                saveChannel(getIndexOfEmptyItem());
                break;
            case EDIT_ID:
                editChannel(getSelectedPreset());
                break;
            case CLEAR_ID:
                Intent clearIntent = new Intent(this, FMClearChannel.class);
                startActivityForResult(clearIntent, CLEAR_CODE);
                break;
            case EXIT_ID:
                Preferences.setEnabled(this, false);
                if (mService != null) {
                    try {
                        mService.powerOff();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Could not power down FM radio", e);
                    }
                }
                finish();
                break;
            case PREFS_ID:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case SCAN_SAVE_ID:
                if (isDBEmpty()) {
                    startScanning();
                } else {
                    showDialog(DIALOG_IF_SCAN_NEXT);
                }
                break;
            case BY_LOUDSPEAKER_ID:
            case BY_HEADSET_ID:
                int routing = item.getItemId() == BY_LOUDSPEAKER_ID
                        ? FMRadioPlayerService.FM_ROUTING_SPEAKER
                        : FMRadioPlayerService.FM_ROUTING_HEADSET;
                try {
                    mService.setAudioRouting(routing);
                } catch (RemoteException e) {
                    Log.e(TAG, "Setting audio routing failed", e);
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        int pos = (int) info.id;

        switch (item.getItemId()) {
            case PLAY_MENU_ID:
                playClickPreset(pos);
                break;
            case EDIT_MENU_ID:
                editChannel(pos);
                break;
            case REPLACE_MENU_ID:
                boolean hasRds = !TextUtils.isEmpty(mRdsStationName);
                saveStationToDB(pos, mCurFreq, hasRds ? null : "", hasRds ? mRdsStationName : "");
                updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                break;
            case CLEAR_MENU_ID:
                saveStationToDB(pos, 0, "", "");
                updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                break;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CLEAR_CODE:
                if (resultCode == RESULT_OK) {
                    boolean clearedAll = data.getBooleanExtra(FMClearChannel.EXTRA_CLEARED_ALL, false);
                    if (clearedAll) {
                        Log.d(TAG, "Cleared all FM stations");
                        Preferences.setScanned(this, false);
                    }
                    updatePresetSwitcher();
                }
                break;
        }
    }

    @Override
    public View makeView() {
        ImageView i = new ImageView(this);
        i.setScaleType(ScaleType.CENTER_INSIDE);
        i.setLayoutParams(new ImageSwitcher.LayoutParams(-1, -1));
        return i;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        switch (id) {
            case R.id.btn_seekbackward:
            case R.id.btn_seekforward:
                initiateSeek(view, id == R.id.btn_seekforward);
                break;
            case R.id.btn_reduce:
            case R.id.btn_add:
                enableUI(false);
                displayRdsScrollText(false);
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
                updateFrequency();
                updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                break;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        mLongPressedButton = v.getId();

        switch (mLongPressedButton) {
            case R.id.btn_seekbackward:
            case R.id.btn_seekforward:
                initiateSeek(v, mLongPressedButton == R.id.btn_seekforward);
                return true;
            case R.id.btn_reduce:
            case R.id.btn_add:
                initiateTune(v, mLongPressedButton == R.id.btn_add);
                return true;
        }

        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP && event.getAction() != MotionEvent.ACTION_CANCEL) {
            return false;
        }
        if (mLongPressedButton == 0) {
            return false;
        }

        mLongPressedButton = 0;

        switch (v.getId()) {
            case R.id.btn_seekbackward:
            case R.id.btn_seekforward:
                if (mHandler.hasMessages(MSG_CONTINUE_SEEK)) {
                    mHandler.removeMessages(MSG_CONTINUE_SEEK);
                } else {
                    if (mService != null) {
                        try {
                            mService.stopSeek();
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not stop seek", e);
                        }
                    }

                    mCurFreq = mPreFreq;
                    updateDisplayPanel(mCurFreq, updatePresetSwitcher());
                    enableUI(false);
                    updateFrequency();
                }
                mHandler.sendEmptyMessageDelayed(MSG_STOP_SCAN_ANIMATION, SCAN_STOP_DELAY);
                break;
            case R.id.btn_reduce:
            case R.id.btn_add:
                mHandler.removeMessages(MSG_CONTINUE_TUNE);
                enableUI(false);
                updateFrequency();
                break;
        }

        return true;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        showSeekBar(true);
        mPreFreq = mCurFreq;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        if (fromTouch) {
            int step = (progress / FMUtil.STEP) * FMUtil.STEP;
            mCurFreq = RANGE_START + step;
            Log.d(TAG, "Changed frequency via progress change, now " + mCurFreq);
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
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (mCurFreq != mPreFreq) {
            updateFrequency();
        }
        showSeekBar(false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "Shared preference " + key + " changed");
        if (Preferences.KEY_HIDE_ACTIONBAR.equals(key)) {
            if (Preferences.isActionBarHidden(this)) {
                mActionBar.hide();
            } else {
                mActionBar.show();
            }
        }
    }

    private void initUI() {
        initImageSwitcher();
        initSeekBar();
        initButtons();
        initListView();
        enableUI(false);
    }

    private void initButtons() {
        mSeekButtons = new ImageButton[4];
        mSeekButtons[0] = (ImageButton) findViewById(R.id.btn_seekbackward);
        mSeekButtons[1] = (ImageButton) findViewById(R.id.btn_reduce);
        mSeekButtons[2] = (ImageButton) findViewById(R.id.btn_add);
        mSeekButtons[3] = (ImageButton) findViewById(R.id.btn_seekforward);
        for (ImageButton button : mSeekButtons) {
            button.setOnClickListener(this);
            button.setOnLongClickListener(this);
            button.setOnTouchListener(this);
        }
    }

    private void initImageSwitcher() {
        mFreqDigits = new ImageSwitcher[5];
        mFreqDigits[0] = (ImageSwitcher) findViewById(R.id.Img_switcher1);
        mFreqDigits[1] = (ImageSwitcher) findViewById(R.id.Img_switcher2);
        mFreqDigits[2] = (ImageSwitcher) findViewById(R.id.Img_switcher3);
        mFreqDigits[3] = (ImageSwitcher) findViewById(R.id.Img_switcher4);
        mFreqDigits[4] = (ImageSwitcher) findViewById(R.id.Img_switcher5);
        for (ImageSwitcher switcher : mFreqDigits) {
            switcher.setFactory(this);
        }

        mPresetLayout = (LinearLayout) findViewById(R.id.preset_swt_layout);
        mPresetDigits = new ImageSwitcher[2];
        mPresetDigits[0] = (ImageSwitcher) findViewById(R.id.preset_swt1);
        mPresetDigits[1] = (ImageSwitcher) findViewById(R.id.preset_swt2);
        for (ImageSwitcher switcher : mPresetDigits) {
            switcher.setFactory(this);
        }

        mStereoStatus = (ImageView) findViewById(R.id.stereo_status);
        mStereoStatus.setVisibility(View.INVISIBLE);
    }

    private void initListView() {
        mChannelList = (ListView) findViewById(R.id.channel_list);

        setSelectedPreset(Preferences.getLastChannel(this));

        mCursor = getContentResolver().query(Channels.CONTENT_URI, FMUtil.PROJECTION, null, null, null);
        if (mCursor == null) {
            Log.e(TAG, "Could not fetch channel data");
            return;
        }
        startManagingCursor(mCursor);

        mChannelList.setAdapter(new ChannelListAdapter(this, mCursor));
        mChannelList.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuinfo) {
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuinfo;
                int pos = (int) info.id;
                Uri uri = Uri.withAppendedPath(Channels.CONTENT_URI, String.valueOf(pos));
                Cursor cursor = getContentResolver().query(uri, FMUtil.PROJECTION, null, null, null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    int frequency = cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ);
                    if (frequency == 0) {
                        saveChannel(pos);
                    } else {
                        menu.setHeaderTitle(FMUtil.getPresetListString(FMRadioMain.this, cursor));
                        if (mCurFreq != frequency) {
                            menu.add(Menu.NONE, PLAY_MENU_ID, Menu.FIRST, R.string.play_preset);
                            menu.add(Menu.NONE, REPLACE_MENU_ID, Menu.FIRST + 2, R.string.replace_preset);
                        }
                        menu.add(Menu.NONE, EDIT_MENU_ID, Menu.FIRST + 1, R.string.edit_preset);
                        menu.add(Menu.NONE, CLEAR_MENU_ID, Menu.FIRST + 3, R.string.clear_preset);
                    }
                    cursor.close();
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(getString(R.string.preset));
                    sb.append(" ");
                    sb.append(pos);
                    menu.setHeaderTitle(sb.toString());
                }
            }
        });
        mChannelList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playClickPreset(position);
            }
        });
    }

    private void initSeekBar() {
        mSeekBar = (SeekBar) findViewById(R.id.seek);
        mSeekBar.setVisibility(View.VISIBLE);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setEnabled(true);
        showSeekBar(false);

        mScanBar = (ImageView) findViewById(R.id.scan_anim);
        mScanAnimation = (AnimationDrawable) getResources().getDrawable(R.drawable.fm_progress);

        mRdsMarqueeText = (TextView) findViewById(R.id.rds_text);
    }

    private boolean bindToService() {
        Log.v(TAG, "Binding to player service");
        Intent i = new Intent(this, FMRadioPlayerService.class);
        startService(i);
        return bindService(i, mServConnection, 0);
    }

    private void unbindService() {
        if (mService != null) {
            try {
                mService.unregisterCallbacks();
            } catch (RemoteException e) {
            }
        }
        if (mIsBound) {
            unbindService(mServConnection);
            mIsBound = false;
        }
    }

    /**
     * Start auto seeking
     *
     * @param v The button that was pressed
     * @param upward Scan up or down
     */
    private void initiateSeek(View v, boolean upward) {
        mPreFreq = mCurFreq;
        // disableUIExceptButton(v);
        displayRdsScrollText(false);
        showSeekBar(false);
        showSeekAnimation(true);
        startSeek(0, upward);
        mScanBar.setBackgroundDrawable(mScanAnimation);
        mScanAnimation.start();
    }

    /**
     * Show Seek Bar
     *
     * @param show show or not.
     */
    private void showSeekBar(boolean show) {
        mSeekBar.setBackgroundDrawable(show ?
                getResources().getDrawable(R.drawable.fm_background_pointer) : null);
        mSeekBar.setThumb(show ?
                getResources().getDrawable(R.drawable.fm_pointer) : null);
    }

    /**
     * Show Seek animation
     *
     * @param show show or not
     */
    private void showSeekAnimation(boolean show) {
        mScanBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    private void initiateTune(View v, boolean upward) {
        // disableUIExceptButton(v);
        displayRdsScrollText(false);
        Message msg = Message.obtain(mHandler,
                MSG_CONTINUE_TUNE, upward ? 1 : 0, 0, null);
        mHandler.sendMessage(msg);
    }

    private void displayRdsScrollText(boolean show) {
        Log.v(TAG, "displayRdsScrollText(" + show + ")");
        if (!show) {
            mRdsStationName = null;
            mRdsRadioText = null;
            mRdsPTYValue = 0;
        }
        if (mRdsMarqueeText != null) {
            if (show) {
                mRdsMarqueeText.setVisibility(View.VISIBLE);
            } else {
                mRdsMarqueeText.setVisibility(View.GONE);
            }
        }
    }

    private void enableUI(boolean enabled) {
        for (ImageButton button : mSeekButtons) {
            button.setEnabled(enabled);
        }
        // updateButtonDrawables();
        if (mChannelList != null) {
            mChannelList.setEnabled(enabled);
        }
    }

    @SuppressWarnings("unused")
    private void disableUIExceptButton(View v) {
        long id = v.getId();
        for (ImageButton button : mSeekButtons) {
            button.setEnabled(button.getId() == id);
        }
        // updateButtonDrawables();
        mChannelList.setEnabled(false);
    }

    @SuppressWarnings("unused")
    private void updateButtonDrawables() {
        for (ImageButton button : mSeekButtons) {
            boolean enabled = button.isEnabled();
            int resId = 0;

            switch (button.getId()) {
                case R.id.btn_seekbackward:
                    resId = enabled ? R.drawable.fm_autosearch_reduce_enable : R.drawable.fm_autosearch_reduce_disable;
                    break;
                case R.id.btn_reduce:
                    resId = enabled ? R.drawable.fm_manualadjust_reduce_enable : R.drawable.fm_manualadjust_reduce_disable;
                    break;
                case R.id.btn_add:
                    resId = enabled ? R.drawable.fm_manualadjust_plus_enable : R.drawable.fm_manualadjust_plus_disable;
                    break;
                case R.id.btn_seekforward:
                    resId = enabled ? R.drawable.fm_autosearch_plus_enable : R.drawable.fm_autosearch_plus_disable;
                    break;
            }
            button.setImageResource(resId);
        }
    }

    private void playClickPreset(int position) {
        Uri uri = Uri.withAppendedPath(Channels.CONTENT_URI, String.valueOf(position));
        Cursor cursor = getContentResolver().query(uri, FMUtil.PROJECTION, null, null, null);

        if (cursor == null) {
            return;
        }

        if (cursor.moveToFirst()) {
            int frequency = cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ);
            if (frequency == 0) {
                Log.d(TAG, "Selected an empty channel, saving...");
                saveChannel(position);
            } else {
                mCurFreq = frequency;
                updateDisplayPanel(mCurFreq, true);
                updatePresetSwitcher(position + 1);
                updateFrequency();
            }
        }
        cursor.close();
    }

    private void startScanning() {
        boolean scanning = false;

        if (mService != null) {
            try {
                scanning = mService.scan();
            } catch (RemoteException e) {
                Log.e(TAG, "Initiating scan failed", e);
            }
        }
        if (scanning) {
            Preferences.setScanned(FMRadioMain.this, true);
            mCurFreq = FMUtil.MIN_FREQUENCY;
            mWakeLock.acquire(LIGHT_ON_TIME);
            mScanning = true;
            mScannedStations = 0;
            displayRdsScrollText(false);
            showDialog(DIALOG_SCAN_PROGRESS);
        } else {
            Log.d(TAG, "Scan request failed");
            FMUtil.showNoticeDialog(this, R.string.error_start_scan);
        }
    }

    private void startSeek(int freq, boolean upward) {
        displayRdsScrollText(false);
        if (mService != null) {
            try {
                mService.seek(freq, upward);
            } catch (RemoteException e) {
                Log.e(TAG, "Seeking failed", e);
            }
        }
    }

    private void updateFrequency() {
        displayRdsScrollText(false);
        if (mService != null) {
            try {
                mService.tune(mCurFreq);
            } catch (RemoteException e) {
                Log.e(TAG, "Tuning failed", e);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void updateDisplayPanel(int currentFreq, boolean isEditEnable) {
        float progress = currentFreq - RANGE_START;
        mSeekBar.setProgress((int) progress);

        updateFrequencyDisplay(currentFreq, isEditEnable);
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

        int[] numbers = isEditEnable ? NUMBER_IMAGES : NUMBER_IMAGES_UNSELECTED;
        int dot = isEditEnable ? R.drawable.fm_number_point : R.drawable.fm_number_unselect_point;

        mFreqDigits[0].setImageResource(numbers[digit1]);
        mFreqDigits[0].setVisibility(digit1 == 0 ? View.GONE : View.VISIBLE);
        mFreqDigits[1].setImageResource(numbers[digit2]);
        mFreqDigits[2].setImageResource(numbers[digit3]);
        mFreqDigits[3].setImageResource(dot);
        mFreqDigits[4].setImageResource(numbers[digit4]);
    }

    private void clearPresetSwitcher() {
        mPresetLayout.setBackgroundDrawable(null);
        mPresetDigits[0].setImageDrawable(null);
        mPresetDigits[1].setImageDrawable(null);
    }

    private void updatePresetSwitcher(int index) {
        if (index <= 0 || index > PRESET_NUM) {
            return;
        }
        int index1 = index / 10;
        int index2 = index - (index1 * 10);
        mPresetLayout.setBackgroundDrawable(getResources()
                .getDrawable(R.drawable.fm_playing_list_bg));
        mPresetDigits[0].setImageResource(NUMBER_IMAGES_PRESET[index1]);
        mPresetDigits[1].setImageResource(NUMBER_IMAGES_PRESET[index2]);
    }

    private boolean updatePresetSwitcher() {
        int index = -1;

        if (mCurFreq > 0) {
            Cursor cursor = getContentResolver().query(Channels.CONTENT_URI, FMUtil.PROJECTION,
                    Channels.FREQUENCY + "=?", new String[] { String.valueOf(mCurFreq) }, null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    index = cursor.getInt(FMUtil.CHANNEL_COLUMN_ID);
                }
                cursor.close();
            }
        }

        if (index >= 0 && index < PRESET_NUM) {
            updatePresetSwitcher(index + 1);
            setSelectedPreset(index);
        } else {
            clearPresetSwitcher();
            setSelectedPreset(-1);
        }

        return index >= 0;
    }

    private void setSelectedPreset(int preset) {
        mChannelList.setSelection(preset);
        if (preset < 0) {
            mChannelList.clearChoices();
        } else {
            mChannelList.setItemChecked(preset, true);
        }
    }

    private int getSelectedPreset() {
        int checked = mChannelList.getCheckedItemPosition();
        if (checked == ListView.INVALID_POSITION) {
            return -1;
        }
        return (int) mChannelList.getItemIdAtPosition(checked);
    }

    private int getIndexOfEmptyItem() {
        Cursor cursor = getContentResolver().query(Channels.CONTENT_URI, FMUtil.PROJECTION, null, null, null);
        int count = 0;

        if (cursor != null) {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                if (cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ) == 0) {
                    break;
                }
                count++;
                cursor.moveToNext();
            }
            if (cursor.isAfterLast()) {
                count = -1;
            }
            cursor.close();
        }

        return count;
    }

    public void clearDB() {
        ContentValues cv = new ContentValues();

        cv.put(Channels.FREQUENCY, 0);
        cv.put(Channels.NAME, "");
        cv.put(Channels.RDS_NAME, "");

        getContentResolver().update(Channels.CONTENT_URI, cv, null, null);
    }

    public boolean isDBEmpty() {
        Cursor cursor = getContentResolver().query(Channels.CONTENT_URI, FMUtil.PROJECTION, null, null, null);
        boolean empty = true;

        if (cursor != null) {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                if (cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ) != 0) {
                    empty = false;
                    break;
                }
                cursor.moveToNext();
            }
            cursor.close();
        }
        return empty;
    }

    private void saveStationToDB(int id, int freq, String name, String rdsName) {
        final Uri uri = Uri.withAppendedPath(Channels.CONTENT_URI, String.valueOf(id));
        ContentValues cv = new ContentValues();

        cv.put(Channels.FREQUENCY, freq);
        if (name != null) {
            cv.put(Channels.NAME, name);
        }
        if (rdsName != null) {
            cv.put(Channels.RDS_NAME, rdsName);
        }

        getContentResolver().update(uri, cv, null, null);
    }

    private void saveChannel(int position) {
        Bundle args = new Bundle();
        args.putInt(ARG_FREQUENCY, mCurFreq);
        args.putInt(ARG_PRESET, position);
        args.putString(ARG_NAME, mRdsStationName);
        showDialog(DIALOG_SAVE_CHANNEL, args);
    }

    private void editChannel(int position) {
        Bundle args = new Bundle();
        args.putInt(ARG_PRESET, position);
        showDialog(DIALOG_EDIT_CHANNEL, args);
    }

    private void handleRdsDataChanged() {
        Log.v(TAG, "RDS data changed, station " + mRdsStationName + " radio text " +
                mRdsRadioText + " pty " + mRdsPTYValue);

        if (!TextUtils.isEmpty(mRdsStationName)) {
            Cursor cursor = getContentResolver().query(Channels.CONTENT_URI, FMUtil.PROJECTION,
                    Channels.FREQUENCY + "=?", new String[] { String.valueOf(mCurFreq) }, null);
            if (cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    int id = cursor.getInt(FMUtil.CHANNEL_COLUMN_ID);
                    saveStationToDB(id, mCurFreq, null, mRdsStationName);
                    updateDisplayPanel(mCurFreq, true);
                    updatePresetSwitcher(id + 1);
                    cursor.moveToNext();
                }
                cursor.close();
            }
        }
        StringBuilder rdsText = new StringBuilder();
        if (!TextUtils.isEmpty(mRdsStationName)) {
            rdsText.append(mRdsStationName);
        }
        if (!TextUtils.isEmpty(mRdsRadioText)) {
            if (rdsText.length() > 0) {
                rdsText.append(RDS_TEXT_SEPARATOR);
            }
            rdsText.append(mRdsRadioText);
        }
        if (mRdsPTYValue >= 0 && mRdsPTYValue < PTY_STRINGS.length) {
            int resId = PTY_STRINGS[mRdsPTYValue];
            if (resId != 0) {
                if (rdsText.length() > 0) {
                    rdsText.append(RDS_TEXT_SEPARATOR);
                }
                rdsText.append(getString(resId));
            }
        }

        final String text = rdsText.toString();
        Log.v(TAG, "Setting RDS marquee text to " + text);
        mRdsMarqueeText.setText(text);
        displayRdsScrollText(!text.isEmpty());
    }

    private void handleScanFinished(boolean canceled) {
        if (!canceled) {
            dismissDialog(DIALOG_SCAN_PROGRESS);
        }
        StringBuilder sb = new StringBuilder();
        if (canceled && mScannedStations < PRESET_NUM) {
            sb.append(getString(R.string.fmradio_save_canceled));
            sb.append("\n");
        }
        sb.append(MessageFormat.format(getString(R.string.scan_result), mScannedStations));

        Toast.makeText(FMRadioMain.this, sb.toString(), Toast.LENGTH_SHORT).show();

        mHandler.sendEmptyMessageDelayed(MSG_STOP_SCAN_ANIMATION, SCAN_STOP_DELAY);

        updateFrequency();
        setSelectedPreset(-1);
        mScannedStations = -1;
        mScanning = false;
    }

    private void handleScannedStation(int frequency) {
        Log.d(TAG, "Scanned station on frequency " + frequency + ", scanned so far " + mScannedStations);
        mCurFreq = frequency;

        if (!mScanning) {
            return;
        }

        if (mScannedStations < PRESET_NUM) {
            saveStationToDB(mScannedStations, frequency, "", "");
            mScannedStations++;
            showDialog(DIALOG_SCAN_PROGRESS);
        }

        if (mScannedStations >= PRESET_NUM) {
            cancelScan();
        }

        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
        displayRdsScrollText(false);
    }

    private void cancelScan() {
        Log.v(TAG, "Cancelling progress dialog");
        try {
            mService.stopScan();
        } catch (RemoteException e) {
            Log.e(TAG, "Stopping scan failed", e);
        }
        dismissDialog(DIALOG_SCAN_PROGRESS);
        setSelectedPreset(-1);
        enableUI(true);
        mHandler.sendEmptyMessageDelayed(MSG_STOP_SCAN_ANIMATION, SCAN_STOP_DELAY);
        mScanning = false;
    }

    private void handleSeekFinished(boolean aborted) {
        mPreFreq = mCurFreq;
        updateDisplayPanel(mCurFreq, updatePresetSwitcher());
        displayRdsScrollText(false);
        enableUI(true);
        mHandler.sendEmptyMessageDelayed(MSG_STOP_SCAN_ANIMATION, SCAN_STOP_DELAY);
        if (!aborted) {
            if (mLongPressedButton == R.id.btn_seekbackward) {
                Message msg = Message.obtain(mHandler, MSG_CONTINUE_SEEK, 0, 0, null);
                mHandler.sendMessageDelayed(msg, LONG_PRESS_SEEK_TIMEOUT);
            } else if (mLongPressedButton == R.id.btn_seekforward) {
                Message msg = Message.obtain(mHandler, MSG_CONTINUE_SEEK, 1, 0, null);
                mHandler.sendMessageDelayed(msg, LONG_PRESS_SEEK_TIMEOUT);
            }
        }
    }
}
