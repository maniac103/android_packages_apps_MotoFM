package com.motorola.fmradio;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

public class SettingsActivity extends PreferenceActivity implements OnPreferenceChangeListener {
    public static final String ACTION_RSSI_UPDATED = "com.motorola.fmradio.action.RSSI_SETTING_UPDATED";
    public static final String EXTRA_RSSI = "rssi";

    private static final int DIALOG_WARN_AIRPLANE = 0;
    private static final int DIALOG_INFO_HEADSET = 1;

    private static final String BACKUP_PREFIX = "presets-";

    private CheckBoxPreference mIgnoreAirplanePref;
    private CheckBoxPreference mIgnoreNoHeadsetPref;
    private ListPreference mSeekSensitivityPref;
    private EditTextPreference mBackupPresetsPref;
    private ListPreference mRestorePresetsPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        PreferenceScreen prefs = getPreferenceScreen();

        mIgnoreAirplanePref = (CheckBoxPreference) prefs.findPreference("ignore_airplane_mode");
        mIgnoreAirplanePref.setOnPreferenceChangeListener(this);
        mIgnoreNoHeadsetPref = (CheckBoxPreference) prefs.findPreference("ignore_no_headset");
        mIgnoreNoHeadsetPref.setOnPreferenceChangeListener(this);
        mSeekSensitivityPref = (ListPreference) prefs.findPreference("seek_sensitivity");
        mSeekSensitivityPref.setOnPreferenceChangeListener(this);
        mBackupPresetsPref = (EditTextPreference) prefs.findPreference("backup_presets");
        mBackupPresetsPref.setOnPreferenceChangeListener(this);
        mBackupPresetsPref.setText(DateFormat.format("yyyy-MM-dd", new Date()).toString());
        mRestorePresetsPref = (ListPreference) prefs.findPreference("restore_presets");
        mRestorePresetsPref.setOnPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePresetBackupList();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference == mRestorePresetsPref) {
            updatePresetBackupList();
        }

        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mIgnoreAirplanePref) {
            final Boolean value = (Boolean) newValue;
            if (value) {
                showDialog(DIALOG_WARN_AIRPLANE);
                return false;
            }
        } else if (preference == mIgnoreNoHeadsetPref) {
            final Boolean value = (Boolean) newValue;
            if (value) {
                showDialog(DIALOG_INFO_HEADSET);
            }
        } else if (preference == mSeekSensitivityPref) {
            final int value = Integer.parseInt((String) newValue);
            Intent i = new Intent(ACTION_RSSI_UPDATED);
            i.putExtra(EXTRA_RSSI, value);
            sendBroadcast(i);
        } else if (preference == mBackupPresetsPref) {
            final String value = (String) newValue;
            if (!TextUtils.isEmpty(value)) {
                File backup = buildBackupFileFromName(this, value);
                if (backup != null) {
                    int resId;
                    if (PresetBackupHelper.backupPresets(this, backup)) {
                        resId = R.string.backup_presets_success_toast;
                        updatePresetBackupList();
                    } else {
                        resId = R.string.backup_presets_failure_toast;
                    }
                    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
                }
            }
        } else if (preference == mRestorePresetsPref) {
            final String fileName = (String) newValue;
            final File restore = buildBackupFileFromName(this, fileName);
            if (restore != null && restore.exists()) {
                int presets = PresetBackupHelper.restorePresets(this, restore);
                String message;

                if (presets >= 0) {
                    message = getString(R.string.restore_presets_success_toast, presets);
                } else {
                    message = getString(R.string.restore_presets_failure_toast);
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            return false;
        }

        return true;
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case DIALOG_WARN_AIRPLANE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.warning)
                        .setMessage(R.string.airplane_ignore_warning_message)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mIgnoreAirplanePref.setChecked(true);
                            }
                        })
                        .setNegativeButton(R.string.no, null)
                        .create();
            case DIALOG_INFO_HEADSET:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.notice)
                        .setMessage(R.string.no_headset_ignore_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
        }

        return null;
    }

    private void updatePresetBackupList() {
        File backupDir = getPresetBackupDirectory(this);
        File[] files = backupDir != null ? backupDir.listFiles() : null;
        ArrayList<String> items = new ArrayList<String>();

        if (files != null) {
            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                final String name = file.getName();
                if (!name.startsWith(BACKUP_PREFIX) || !name.endsWith(".xml")) {
                    continue;
                }
                items.add(name.substring(BACKUP_PREFIX.length(), name.length() - 4));
            }
        }

        final String[] itemArray = items.toArray(new String[items.size()]);
        mRestorePresetsPref.setEntries(itemArray);
        mRestorePresetsPref.setEntryValues(itemArray);
        mRestorePresetsPref.setValue(null);
    }

    private static File getPresetBackupDirectory(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            return null;
        }
        return new File(base, "backups");
    }

    private static File buildBackupFileFromName(Context context, String name) {
        File backupDir = getPresetBackupDirectory(context);
        if (backupDir == null) {
            return null;
        }

        final StringBuilder fileName = new StringBuilder();
        fileName.append(BACKUP_PREFIX);
        fileName.append(name);
        fileName.append(".xml");

        return new File(backupDir, fileName.toString());
    }
}
