package com.motorola.fmradio;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

public class SettingsActivity extends PreferenceActivity implements OnPreferenceChangeListener {
    public static final String ACTION_RSSI_UPDATED = "com.motorola.fmradio.action.RSSI_SETTING_UPDATED";
    public static final String EXTRA_RSSI = "rssi";

    private CheckBoxPreference mIgnoreAirplanePref;
    private CheckBoxPreference mIgnoreNoHeadsetPref;
    private ListPreference mSeekSensitivityPref;
    private CheckBoxPreference mActionBarPref;

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
        mActionBarPref = (CheckBoxPreference) prefs.findPreference("hide_actionbar");
        mActionBarPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mIgnoreAirplanePref) {
            final Boolean value = (Boolean) newValue;

            if (value) {
                AlertDialog dialog = new AlertDialog.Builder(this)
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

                dialog.show();
                return false;
            }
        } else if (preference == mIgnoreNoHeadsetPref) {
            final Boolean value = (Boolean) newValue;

            if (value) {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.notice)
                        .setMessage(R.string.no_headset_ignore_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();

                dialog.show();
            }
        } else if (preference == mSeekSensitivityPref) {
            final int value = Integer.parseInt((String) newValue);
            Intent i = new Intent(ACTION_RSSI_UPDATED);
            i.putExtra(EXTRA_RSSI, value);
            sendBroadcast(i);
        } else if (preference == mActionBarPref) {
            // TODO call configuration change
        }

        return true;
    }
}
