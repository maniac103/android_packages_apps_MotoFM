package com.motorola.fmradio;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Toast;

import java.text.DecimalFormat;

public class FMUtil {
    public static final String EMPTY = "";

    public static final int MIN_FREQUENCY = 87500;
    public static final int MAX_FREQUENCY = 108000;
    public static final int STEP = 100;

    public static final String[] PROJECTION = new String[] {
        FMDataProvider.Channels.ID, FMDataProvider.Channels.FREQUENCY,
        FMDataProvider.Channels.NAME, FMDataProvider.Channels.RDS_NAME
    };
    public static  final int CHANNEL_COLUMN_ID = 0;
    public static  final int CHANNEL_COLUMN_FREQ = 1;
    public static  final int CHANNEL_COLUMN_NAME = 2;
    public static  final int CHANNEL_COLUMN_RDSNAME = 3;

    public static final int FREQ_RATE = 1000;

    public static String getPresetListString(Context context, Cursor cursor) {
        String name = cursor.getString(FMUtil.CHANNEL_COLUMN_NAME);
        if (!isEmptyStr(name)) {
            return name;
        }

        int frequency = cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ);
        if (frequency == 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            sb.append(context.getString(R.string.empty));
            sb.append(")");
            return sb.toString();
        }

        String rdsName = cursor.getString(FMUtil.CHANNEL_COLUMN_RDSNAME);
        if (!isEmptyStr(rdsName)) {
            return rdsName;
        }

        return context.getString(R.string.untitled);
    }

    public static String getPresetUiString(Context context, Cursor cursor, int index) {
        int frequency = cursor.getInt(CHANNEL_COLUMN_FREQ);
        String chName = cursor.getString(CHANNEL_COLUMN_NAME);
        Resources res = context.getResources();
        StringBuilder sb = new StringBuilder();

        sb.append(res.getString(R.string.preset));
        sb.append(" ");
        sb.append(index);
        sb.append(" (");
        if (!FMUtil.isEmptyStr(chName)) {
            sb.append(chName.replaceAll("\n", " "));
        } else if (frequency != 0) {
            sb.append(formatFrequency(context, frequency));
        } else {
            sb.append(res.getString(R.string.empty));
        }
        sb.append(")");
        return sb.toString();
    }

    public static boolean isEmptyStr(String str) {
        if (str == null) {
            return true;
        }
        return TextUtils.isEmpty(str.trim());
    }

    public static void showNoticeDialog(Context context, int noticeId) {
        Toast ts = Toast.makeText(context, context.getString(noticeId), Toast.LENGTH_LONG);
        ts.setGravity(Gravity.CENTER, 0, 0);
        ts.show();
    }

    public static String formatFrequency(Context context, int frequency) {
        float freq = (float) frequency / 1000.0F;
        DecimalFormat formatter = new DecimalFormat(".0");
        return formatter.format(freq) + context.getString(R.string.mhz);
    }
}
