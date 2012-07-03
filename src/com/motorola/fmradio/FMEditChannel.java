package com.motorola.fmradio;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class FMEditChannel extends Activity implements View.OnClickListener {
    public static final String TAG = "FMEditChannel";

    public static final String AUTHORITY = "com.motorola.provider.fmradio";
    public static final Uri CONTENT_URI = Uri.parse("content://com.motorola.provider.fmradio/FM_Radio");
    public static final Uri SAVED_CONTENT_URI = Uri.parse("content://com.motorola.provider.fmradio/FM_Radio_saved_state");
    private static final String[] PROJECTION = new String[] {
        "ID", "CH_Num", "CH_Freq", "CH_Name", "CH_RdsName"
    };

    public static final int SAVE_ID = 1;
    public static final int DISCARD_ID = 2;

    public static final String FM_VOLUME_SETTING = "com.motorola.fmradio.setvolume";
    public static final int High_frequency = 108000;
    public static final int Low_frequency = 87500;

    private TextView EdEdit_freq;
    private EditText EdEdit_name;
    private TextView TvEdit_ch_num;
    private int c_num;
    private String curFreqName = "";
    private String curRdsPS = "";
    private Intent intent;
    private Button mBtnDiscard;
    private Button mBtnSave;
    private int mCurFreq = Low_frequency;
    private Cursor mCursor;

    private void initResource() {
        setTitle(R.string.edit_preset);
        mBtnSave = (Button) findViewById(R.id.btn_save);
        mBtnSave.setOnClickListener(this);
        mBtnDiscard = (Button) findViewById(R.id.btn_discard);
        mBtnDiscard.setOnClickListener(this);
        TvEdit_ch_num = (TextView) findViewById(R.id.edit_ch_num);
        EdEdit_freq = (TextView) findViewById(R.id.save_frequency);
        EdEdit_name = (EditText) findViewById(R.id.edit_ch_name);
        InputFilter[] oldFilter = EdEdit_freq.getFilters();
        int oldlen = oldFilter.length;
        InputFilter[] newFilter = new InputFilter[oldlen + 1];
        System.arraycopy(oldFilter, 0, newFilter, 0, oldlen);
        newFilter[oldlen] = new LengthFilter(5);
        EdEdit_freq.setFilters(newFilter);
        oldFilter = EdEdit_name.getFilters();
        oldlen = oldFilter.length;
        newFilter = new InputFilter[oldlen + 1];
        System.arraycopy(oldFilter, 0, newFilter, 0, oldlen);
        newFilter[oldlen] = new LengthFilter(40);
        EdEdit_name.setFilters(newFilter);
        EdEdit_name.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
            }
        });
        intent = getIntent();
        c_num = intent.getIntExtra("current_num", 1);
        if (intent.getData() == null) {
            intent.setData(CONTENT_URI);
        }
        mCursor = getContentResolver().query(getIntent().getData(), FMUtil.PROJECTION, "ID=" + c_num, null, null);
        if (mCursor != null) {
            mCursor.moveToFirst();
            TvEdit_ch_num.setText(getString(R.string.preset) + " " + (c_num + 1));
            String chFreq = mCursor.getString(2);
            curRdsPS = mCursor.getString(3);
            curFreqName = mCursor.getString(4);
            if (FMUtil.isEmptyStr(chFreq)) {
                mCurFreq = Low_frequency;
            } else {
                mCurFreq = (int) (Float.parseFloat(chFreq) * 1000.0F);
            }
            String curFreq = Float.toString((float) mCurFreq / 1000.0F) + getString(R.string.mhz);
            TvEdit_ch_num.setText(getString(R.string.preset) + " " + (c_num + 1));
            if (curFreqName != null && curFreqName.length() > 0) {
                EdEdit_name.setText(curFreqName);
            } else if (curRdsPS != null && curRdsPS.length() > 0 && !curRdsPS.equals("")) {
                EdEdit_name.setText(curRdsPS);
            } else if (curFreq != null) {
                EdEdit_name.setText(curFreq);
            }
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            DecimalFormat myFormatter = new DecimalFormat(".0", symbols);
            String output = myFormatter.format((double) mCurFreq / 1000.0);
            EdEdit_freq.setText(output);
        }
    }

    private void noticeFMRadioMainUpdateVol(String cmd, int action, int keyCode) {
        Intent intent = new Intent(cmd);
        intent.putExtra("event_action", action);
        intent.putExtra("event_keycode", keyCode);
        sendBroadcast(intent);
    }

    @Override
    public void onClick(View arg0) {
        switch (arg0.getId()) {
            case R.id.btn_save:
                ContentValues cv = new ContentValues();
                String id = mCursor.getString(0);
                String chNum = mCursor.getString(1);
                cv.put("ID", id);
                cv.put("CH_Num", chNum);
                cv.put("CH_Freq", EdEdit_freq.getText().toString());
                cv.put("CH_Name", EdEdit_name.getText().toString());
                cv.put("CH_RdsName", curRdsPS);
                getContentResolver().update(getIntent().getData(), cv, "ID=" + id, null);
                Intent edit_result = new Intent();
                edit_result.putExtra("edit_name", EdEdit_name.getText().toString());
                edit_result.putExtra("edit_freq", EdEdit_freq.getText().toString());
                setResult(-1, edit_result);
                finish();
                break;
            case R.id.btn_discard:
                EdEdit_freq.setText("");
                EdEdit_name.setText("");
                setResult(0, new Intent("Discard"));
                finish();
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged() For save channel activity  called");
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.edit_ch);
        initResource();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_ch);
        initResource();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 1, 0, R.string.btn_save).setIcon(android.R.drawable.ic_menu_save);
        menu.add(0, 2, 1, R.string.btn_discard).setIcon(R.drawable.ic_menu_discard);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyDown(keyCode, event);
        }

        int act = event.getAction();
        noticeFMRadioMainUpdateVol("com.motorola.fmradio.setvolume", act, keyCode);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyDown(keyCode, event);
        }

        int act = event.getAction();
        noticeFMRadioMainUpdateVol("com.motorola.fmradio.setvolume", act, keyCode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                ContentValues cv = new ContentValues();
                String id = mCursor.getString(0);
                String chNum = mCursor.getString(1);
                cv.put("ID", id);
                cv.put("CH_Num", chNum);
                cv.put("CH_Freq", EdEdit_freq.getText().toString());
                cv.put("CH_Name", EdEdit_name.getText().toString());
                cv.put("CH_RdsName", curRdsPS);
                getContentResolver().update(getIntent().getData(), cv, "ID=" + id, null);
                Intent edit_result = new Intent();
                edit_result.putExtra("edit_name", EdEdit_name.getText().toString());
                edit_result.putExtra("edit_freq", EdEdit_freq.getText().toString());
                setResult(-1, edit_result);
                finish();
                break;
            case 2:
                EdEdit_freq.setText("");
                EdEdit_name.setText("");
                setResult(0, new Intent("Discard"));
                finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
