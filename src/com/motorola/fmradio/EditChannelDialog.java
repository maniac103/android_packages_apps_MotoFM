package com.motorola.fmradio;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.motorola.fmradio.FMDataProvider.Channels;

public class EditChannelDialog extends AlertDialog
        implements DialogInterface.OnClickListener, CheckBox.OnCheckedChangeListener {
    private Uri mUri;

    private TextView mFrequencyField;
    private CheckBox mUseRdsName;
    private EditText mNameField;

    public EditChannelDialog(Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.edit_dialog, null);
        Context context = getContext();

        setIcon(0);
        setView(view);
        setInverseBackgroundForced(true);
        setTitle(" ");

        mFrequencyField = (TextView) view.findViewById(R.id.channel_frequency);
        mUseRdsName = (CheckBox) view.findViewById(R.id.use_rds_name);
        mUseRdsName.setOnCheckedChangeListener(this);
        mNameField = (EditText) view.findViewById(R.id.channel_name);

        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel), this);

        super.onCreate(savedInstanceState);
    }

    public void setPreset(int preset) {
        setTitle(getContext().getString(R.string.edit_preset_title, (preset + 1)));
        initData(preset);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            ContentValues cv = new ContentValues();

            cv.put(Channels.NAME, mUseRdsName.isChecked() ? "" : mNameField.getText().toString());

            getContext().getContentResolver().update(mUri, cv, null, null);
        }
        dismiss();
    }

    @Override
    public void onCheckedChanged(CompoundButton view, boolean isChecked) {
        mNameField.setVisibility(isChecked ? View.GONE : View.VISIBLE);
    }

    private void initData(int preset) {
        mUri = Uri.withAppendedPath(Channels.CONTENT_URI, String.valueOf(preset));

        final Context context = getContext();
        Cursor cursor = context.getContentResolver().query(mUri, FMUtil.PROJECTION, null, null, null);

        if (cursor != null) {
            cursor.moveToFirst();

            int frequency = cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ);
            if (frequency == 0) {
                frequency = FMUtil.MIN_FREQUENCY;
            }

            final String freqString = FMUtil.formatFrequency(context, frequency);
            mFrequencyField.setText(freqString);

            String name = cursor.getString(FMUtil.CHANNEL_COLUMN_NAME);
            if (!TextUtils.isEmpty(name)) {
                mNameField.setText(name);
                mUseRdsName.setChecked(false);
            } else {
                mNameField.setText(cursor.getString(FMUtil.CHANNEL_COLUMN_RDSNAME));
                mUseRdsName.setChecked(true);
            }

            cursor.close();
        }
    }
}
