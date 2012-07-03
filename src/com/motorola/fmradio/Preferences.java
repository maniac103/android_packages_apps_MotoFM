package com.motorola.fmradio;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Preferences {
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_LAST_FREQUENCY = "last_frequency";
    private static final String KEY_LAST_CHANNEL = "last_channel";
    private static final String KEY_SCANNED = "scanned";
    private static final String KEY_ENABLED = "enabled";

    private static final int DEFAULT_VOLUME = 0;
    private static final int DEFAULT_FREQUENCY = FMUtil.MIN_FREQUENCY;

    static public int getVolume(Context context) {
        return getPrefs(context).getInt(KEY_VOLUME, DEFAULT_VOLUME);
    }
    static public void setVolume(Context context, int volume) {
        getPrefs(context).edit().putInt(KEY_VOLUME, volume).commit();
    }

    static public int getLastFrequency(Context context) {
        return getPrefs(context).getInt(KEY_LAST_FREQUENCY, DEFAULT_FREQUENCY);
    }
    static public void setLastFrequency(Context context, int frequency) {
        if (frequency > 0) {
            getPrefs(context).edit().putInt(KEY_LAST_FREQUENCY, frequency).commit();
        }
    }

    static public int getLastChannel(Context context) {
        return getPrefs(context).getInt(KEY_LAST_CHANNEL, -1);
    }
    static public void setLastChannel(Context context, int channel) {
        getPrefs(context).edit().putInt(KEY_LAST_CHANNEL, channel).commit();
    }

    static public boolean isScanned(Context context) {
        return getPrefs(context).getBoolean(KEY_SCANNED, false);
    }
    static public void setScanned(Context context, boolean scanned) {
        getPrefs(context).edit().putBoolean(KEY_SCANNED, scanned).commit();
    }

    static public boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, false);
    }
    static public void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    static private SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
