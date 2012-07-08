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
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.motorola.fmradio.FMDataProvider.Channels;

public class FMEditChannel extends Activity implements View.OnClickListener {
    private static final String TAG = "FMEditChannel";

    public static final String EXTRA_PRESET = "preset";

    private static final int SAVE_ID = 1;
    private static final int DISCARD_ID = 2;

    private EditText mNameField;
    private TextView mFrequencyField;
    private TextView mPresetField;
    private Button mDiscardButton;
    private Button mSaveButton;

    private Uri mUri;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.edit_ch);
        setTitle(R.string.edit_preset);

        mSaveButton = (Button) findViewById(R.id.btn_save);
        mSaveButton.setOnClickListener(this);
        mDiscardButton = (Button) findViewById(R.id.btn_discard);
        mDiscardButton.setOnClickListener(this);

        mPresetField = (TextView) findViewById(R.id.edit_ch_num);
        mFrequencyField = (TextView) findViewById(R.id.save_frequency);
        mNameField = (EditText) findViewById(R.id.edit_ch_name);

        initNameFilter();
        initData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, SAVE_ID, Menu.FIRST, R.string.btn_save).setIcon(android.R.drawable.ic_menu_save);
        menu.add(Menu.NONE, DISCARD_ID, Menu.FIRST + 1, R.string.btn_discard).setIcon(R.drawable.ic_menu_discard);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case SAVE_ID:
                doSave();
                break;
            case DISCARD_ID:
                doDiscard();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_save:
                doSave();
                break;
            case R.id.btn_discard:
                doDiscard();
                break;
        }
    }

    private void initNameFilter() {
        InputFilter[] oldFilter = mNameField.getFilters();
        int oldLen = oldFilter.length;
        InputFilter[] newFilter = new InputFilter[oldLen + 1];

        System.arraycopy(oldFilter, 0, newFilter, 0, oldLen);
        newFilter[oldLen] = new LengthFilter(40);
        mNameField.setFilters(newFilter);
    }

    private void initData() {
        int preset = getIntent().getIntExtra(EXTRA_PRESET, 0);

        mUri = Uri.withAppendedPath(Channels.CONTENT_URI, String.valueOf(preset));
        mPresetField.setText(getString(R.string.preset) + " " + (preset + 1));

        Cursor cursor = getContentResolver().query(mUri, FMUtil.PROJECTION, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();

            int frequency = cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ);
            if (frequency == 0) {
                frequency = FMUtil.MIN_FREQUENCY;
            }

            final String freqString = FMUtil.formatFrequency(this, frequency);
            mFrequencyField.setText(freqString);

            String name = cursor.getString(FMUtil.CHANNEL_COLUMN_NAME);
            String rdsInfo = cursor.getString(FMUtil.CHANNEL_COLUMN_RDSNAME);

            if (!TextUtils.isEmpty(name)) {
                mNameField.setText(name);
            } else if (!TextUtils.isEmpty(rdsInfo)) {
                mNameField.setText(rdsInfo);
            } else {
                mNameField.setText(freqString);
            }

            cursor.close();
        }
    }

    private void doSave() {
        ContentValues cv = new ContentValues();

        cv.put(Channels.NAME, mNameField.getText().toString());

        getContentResolver().update(mUri, cv, null, null);

        setResult(RESULT_OK);
        finish();
    }

    private void doDiscard() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
