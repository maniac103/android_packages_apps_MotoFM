package com.motorola.fmradio;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

public class FMSaveChannel extends Activity implements View.OnClickListener {
    private static final String TAG = "FMSaveChannel";

    public static final String AUTHORITY = "com.motorola.provider.fmradio";
    public static final Uri CONTENT_URI = Uri.parse("content://com.motorola.provider.fmradio/FM_Radio");
    public static final Uri SAVED_CONTENT_URI = Uri.parse("content://com.motorola.provider.fmradio/FM_Radio_saved_state");
    private static final String[] PROJECTION = new String[] {
        "ID", "CH_Num", "CH_Freq", "CH_Name"
    };

    public static final int SAVE_ID = 1;
    public static final int DISCARD_ID = 2;

    private EditText EdSave_name;
    private TextView TvSave_freq;
    private String c_RdsPS = "";
    private int c_freq = 0;
    private int c_num = 0;
    private Cursor cur;
    private Intent intent;
    private Button mBtnDiscard;
    private Button mBtnSave;
    private Spinner mSpin;

    private void initResource() {
        setTitle(R.string.save_as_preset);
        mBtnSave = (Button) findViewById(R.id.btn_save);
        mBtnSave.setOnClickListener(this);
        mBtnDiscard = (Button) findViewById(R.id.btn_discard);
        mBtnDiscard.setOnClickListener(this);
        TvSave_freq = (TextView) findViewById(R.id.save_frequency);
        mSpin = (Spinner) findViewById(R.id.ch_spinner);
        EdSave_name = (EditText) findViewById(R.id.ch_name_edit);
        InputFilter[] oldFilter = EdSave_name.getFilters();
        int oldLen = oldFilter.length;
        InputFilter[] newFilter = new InputFilter[oldLen + 1];
        System.arraycopy(newFilter, 0, oldFilter, 0, oldLen);
        newFilter[oldLen] = new LengthFilter(40);
        EdSave_name.setFilters(newFilter);
        intent = getIntent();
        c_freq = intent.getIntExtra("current_freq", 87500);
        String curFreq = Float.toString((float) c_freq / 1000.0F) + getString(R.string.mhz);
        c_num = intent.getIntExtra("current_num", 0);
        c_RdsPS = intent.getStringExtra("rds_name");
        if (c_RdsPS != null && c_RdsPS.length() > 0) {
            EdSave_name.setText(c_RdsPS);
        } else if (curFreq != null) {
            EdSave_name.setText(curFreq);
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat myFormatter = new DecimalFormat(".0", symbols);
        String output = myFormatter.format((double) c_freq / 1000.0);
        TvSave_freq.setText(output);

        Resources res = getResources();
        if (intent.getData() == null) {
            intent.setData(CONTENT_URI);
        }
        cur = getContentResolver().query(intent.getData(), PROJECTION, null, null, null);
        if (cur != null) {
            cur.moveToFirst();
            ArrayList<String> results = new ArrayList<String>();
            int i = 1;
            while (!cur.isAfterLast()) {
                String chFreq = cur.getString(2);
                String chName = cur.getString(3);
                String result_item;
                if (FMUtil.isEmptyStr(chName)) {
                    if (FMUtil.isEmptyStr(chFreq)) {
                        result_item = res.getString(R.string.preset) + " " + i + " (" + getString(R.string.empty) + ")";
                    } else {
                        result_item = res.getString(R.string.preset) + " " + i + " (" + chFreq + "MHz" + ")";
                    }
                } else {
                    result_item = res.getString(R.string.preset) + " " + i + " (" + chName + ")";
                }
                results.add(result_item);
                i++;
            }
            ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, results);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mSpin.offsetLeftAndRight(3);
            mSpin.setAdapter(adapter);
            mSpin.setSelection(c_num);
        }
    }

    private void noticeFMRadioMainUpdateVol(String cmd, int action, int keyCode) {
        Intent intent = new Intent(cmd);
        intent.putExtra("event_action", action);
        intent.putExtra("event_keycode", keyCode);
        sendBroadcast(intent);
    }

    @Override
    public void onClick(View button) {
        switch (button.getId()) {
            case R.id.btn_save:
                ContentValues cv = new ContentValues();
                String str_id = Integer.toString(mSpin.getSelectedItemPosition());
                int num = mSpin.getSelectedItemPosition() + 1;
                cv.put("CH_Num", "CH" + num);
                cv.put("CH_Freq", TvSave_freq.getText().toString());
                cv.put("CH_Name", EdSave_name.getText().toString());
                Intent intent = getIntent();
                String rds_name = intent.getStringExtra("rds_name");
                if (rds_name != null && rds_name.length() > 0) {
                    cv.put("CH_RdsName", rds_name);
                } else {
                    cv.put("CH_RdsName", "");
                }
                getContentResolver().update(getIntent().getData(), cv, "ID=" + str_id, null);
                Intent save_result = new Intent();
                save_result.putExtra("newSaved_id", mSpin.getSelectedItemPosition());
                setResult(-1, save_result);
                finish();
                break;
            case R.id.btn_discard:
                setResult(0, new Intent("Discard"));
                finish();
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged() For save channel activity  called");
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.save_ch);
        initResource();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.save_ch);
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
            return super.onKeyUp(keyCode, event);
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
                String str_id = Integer.toString(mSpin.getSelectedItemPosition());
                int num = mSpin.getSelectedItemPosition() + 1;
                cv.put("CH_Num", "CH" + num);
                cv.put("CH_Freq", TvSave_freq.getText().toString());
                cv.put("CH_Name", EdSave_name.getText().toString());
                Intent intent = getIntent();
                String rds_name = intent.getStringExtra("rds_name");
                if (rds_name != null && rds_name.length() > 0) {
                    cv.put("CH_RdsName", rds_name);
                } else {
                    cv.put("CH_RdsName", "");
                }
                getContentResolver().update(getIntent().getData(), cv, "ID=" + str_id, null);
                Intent save_result = new Intent();
                save_result.putExtra("newSaved_id", mSpin.getSelectedItemPosition());
                setResult(-1, save_result);
                finish();
                break;
            case 2:
                setResult(0, new Intent("Discard"));
                finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
