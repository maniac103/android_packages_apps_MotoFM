package com.motorola.fmradio;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Toast;

public class FMUtil {
    public static final String AUTHORITY = "com.motorola.provider.fmradio";
    public static final Uri CONTENT_URI = Uri.parse("content://com.motorola.provider.fmradio/FM_Radio");
    public static final String EMPTY = "";

    public static final int MIN_FREQUENCY = 87500;
    public static final int MAX_FREQUENCY = 108000;
    public static final int STEP = 100;

    public static final String[] PROJECTION = new String[] {
        "ID", "CH_Freq", "CH_Name", "CH_RdsName"
    };
    public static  final int FM_RADIO_INDEX_ID = 0;
    public static  final int FM_RADIO_INDEX_CHFREQ = 1;
    public static  final int FM_RADIO_INDEX_CHNAME = 2;
    public static  final int FM_RADIO_INDEX_CHRDSNAME = 3;

    public static final int FREQ_RATE = 1000;

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

    public static String getPresetUiString(Context context, Cursor cursor, int index) {
        String chFreq = cursor.getString(FM_RADIO_INDEX_CHFREQ);
        String chName = cursor.getString(FM_RADIO_INDEX_CHNAME);
        Resources res = context.getResources();
        StringBuilder sb = new StringBuilder();

        sb.append(res.getString(R.string.preset));
        sb.append(" ");
        sb.append(index);
        sb.append(" (");
        if (!FMUtil.isEmptyStr(chName)) {
            sb.append(chName.replaceAll("\n", " "));
        } else if (!FMUtil.isEmptyStr(chFreq)) {
            sb.append(chFreq);
            sb.append(res.getString(R.string.mhz));
        } else {
            sb.append(res.getString(R.string.empty));
        }
        sb.append(")");
        return sb.toString();
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
}
