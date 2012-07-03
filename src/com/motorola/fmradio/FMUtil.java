package com.motorola.fmradio;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Toast;

public class FMUtil {
    public static final String AUTHORITY = "com.motorola.provider.fmradio";
    public static final Uri CONTENT_URI = Uri.parse("content://com.motorola.provider.fmradio/FM_Radio");
    public static final String EMPTY = "";

    public static final String[] PROJECTION = new String[] {
        "ID", "CH_Num", "CH_Freq", "CH_Name", "CH_RdsName"
    };
    public static  final int FM_RADIO_INDEX_ID = 0;
    public static  final int FM_RADIO_INDEX_CHNUM = 1;
    public static  final int FM_RADIO_INDEX_CHFREQ = 2;
    public static  final int FM_RADIO_INDEX_CHNAME = 3;
    public static  final int FM_RADIO_INDEX_CHRDSNAME = 4;

    public static final String[] SAVED_PROJECTION = new String[] {
        "ID", "Last_ChNum", "Last_Freq", "isFirstScaned", "Last_Volume"
    };
    public static  final int FM_RADIO_SAVED_INDEX_ID = 0;
    public static  final int FM_RADIO_SAVED_INDEX_LAST_CHNUM = 1;
    public static  final int FM_RADIO_SAVED_INDEX_LAST_FREQ = 2;
    public static  final int FM_RADIO_SAVED_INDEX_FIRST_SCAN = 3;
    public static  final int FM_RADIO_SAVED_INDEX_LAST_VOLUME = 4;

    public static final int FREQ_RATE = 1000;
    public static final String PRESET_POSTFIX = "MHz";

    public static String getMatchedStrByCursor(Cursor cursor, Integer value, boolean isLandScape) {
        String strPreset = null;
        if (value != null) {
            strPreset = getStrPreset(value);
        }
        if (cursor != null) {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                String chFreq = cursor.getString(FM_RADIO_INDEX_CHFREQ);
                String chName = cursor.getString(FM_RADIO_INDEX_CHNAME);
                String chRdsName = cursor.getString(FM_RADIO_INDEX_CHRDSNAME);
                if (isEmptyStr(chName)) {
                    if (!isEmptyStr(chFreq)) {
                        if (isLandScape) {
                            if (chRdsName != null && chRdsName.length() > 0) {
                                return chRdsName;
                            }
                        } else {
                            if (chRdsName != null && chRdsName.length() > 0) {
                                return strPreset + " " + chRdsName;
                            }
                        }
                    }
                } else if (chName != null && chName.trim().length() > 0) {
                    String chNameTmp = chName.replaceAll("\\n", " ");
                    return chNameTmp;
                }
                cursor.moveToNext();
            }
        }
        return strPreset;
    }

    public static String getPresetStr(ContentResolver cr, Integer value, int index, boolean isLandScape) {
        String strPreset = null;
        Cursor cursor = cr.query(CONTENT_URI, PROJECTION, "ID=" + index, null, null);
        strPreset = getMatchedStrByCursor(cursor, value, isLandScape);
        cursor.close();
        return strPreset;
    }

    public static String getPresetStr(ContentResolver cr, Integer value, boolean isLandScape) {
        String strPreset = null;
        float fValue = (float) value / 1000.0F;
        Cursor cursor = cr.query(CONTENT_URI, PROJECTION, "CH_Freq=" + fValue, null, null);
        strPreset = getMatchedStrByCursor(cursor, value, isLandScape);
        cursor.close();
        return strPreset;
    }

    public static String getStrPreset(int counter) {
        String strPreset = null;
        float preset = (float) counter / 1000.0F;
        if (isInteger(preset)) {
            return Integer.toString((int) preset) + "MHz";
        } else {
            return Float.toString(preset) + "MHz";
        }
    }

    public static boolean isEmptyStr(String str) {
        if (str == null) {
            return true;
        }
        if (str.trim().equals("")) {
            return true;
        }
        if (str.length() == 0) {
            return true;
        }
        return false;
    }

    public static boolean isInteger(float preset) {
        return preset == (int) preset;
    }

    public static void showNoticeDialog(Context context, int noticeId) {
        Toast ts = Toast.makeText(context, context.getString(noticeId), Toast.LENGTH_LONG);
        ts.setGravity(Gravity.CENTER, 0, 0);
        ts.show();
    }

    public static void showNotification(NotificationManager nm, Context context, String line2) {
        Notification notification = new Notification(R.drawable.fm_statusbar_icon, "", System.currentTimeMillis());
        Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.setComponent(new ComponentName("com.motorola.fmradio", "com.motorola.fmradio.FMRadioMain"));
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, launchIntent, 0);
        notification.setLatestEventInfo(context, line2, null, contentIntent);
        notification.flags |= Notification.DEFAULT_VIBRATE;
        nm.notify(R.string.fmradio_service_label, notification);
    }
}
