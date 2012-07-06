package com.motorola.fmradio;

import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FMClearChannel extends ListActivity implements View.OnClickListener {
    private static final String TAG = "FMClearChannel";

    public static final String EXTRA_CLEARED_ALL = "cleared_all";

    private static final int CLEAR_ID = 1;
    private static final int SELECT_ALL_ID = 2;
    private static final int UNSELECT_ALL_ID = 3;

    private ListView mListView;
    private Button mDoneButton;
    private int mCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.clear_ch);
        setTitle(R.string.clear_presets);

        mDoneButton = (Button) findViewById(R.id.btn_done);
        mDoneButton.setOnClickListener(this);

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setFocusableInTouchMode(true);
        mListView.requestFocus();
        mListView.setFocusable(true);
        mListView.setItemsCanFocus(true);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    boolean result = mListView.isItemChecked(position);
                    for (int i = 0; i < mCount; i++) {
                        mListView.setItemChecked(i + 1, result);
                    }
                }
            }
        });

        Cursor cursor = getContentResolver().query(FMUtil.CONTENT_URI, FMUtil.PROJECTION, null, null, null);
        ArrayList<String> items = new ArrayList<String>();
        items.add(getString(R.string.all_presets));

        mCount = 0;
        if (cursor != null) {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                mCount++;
                items.add(FMUtil.getPresetUiString(this, cursor, mCount));
                cursor.moveToNext();
            }
            cursor.close();
        }

        ArrayAdapter adapter = new ArrayAdapter<String>(this, R.layout.simple_choice, R.id.text1, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckedTextView v = (CheckedTextView) super.getView(position, convertView, parent);
                v.setEllipsize(TruncateAt.END);
                return v;
            }
        };
        setListAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, CLEAR_ID, Menu.FIRST, R.string.clear).setIcon(R.drawable.ic_menu_clear_channel);
        menu.add(Menu.NONE, SELECT_ALL_ID, Menu.FIRST + 1, R.string.select_all).setIcon(R.drawable.ic_menu_select_all);
        menu.add(Menu.NONE, UNSELECT_ALL_ID, Menu.FIRST + 2, R.string.unselect_all).setIcon(R.drawable.ic_menu_unselect_all);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case CLEAR_ID:
                doClear();
                break;
            case SELECT_ALL_ID:
            case UNSELECT_ALL_ID:
                for (int i = 0; i < (mCount + 1); i++) {
                    mListView.setItemChecked(i, id == SELECT_ALL_ID);
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View button) {
        if (button.getId() == R.id.btn_done) {
            doClear();
        }
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

    private void noticeFMRadioMainUpdateVol(String cmd, int action, int keyCode) {
        Intent intent = new Intent(cmd);
        intent.putExtra("event_action", action);
        intent.putExtra("event_keycode", keyCode);
        sendBroadcast(intent);
    }

    private void doClear() {
        SparseBooleanArray checked = mListView.getCheckedItemPositions();
        int count = 0;

        for (int i = 0; i < mCount; i++) {
            if (checked.get(i + 1)) {
                count++;
                ContentValues cv = new ContentValues();
                cv.put("ID", i);
                cv.put("CH_Freq", "");
                cv.put("CH_Name", "");
                getContentResolver().update(FMUtil.CONTENT_URI, cv, "ID=" + i, null);
            }
        }

        Intent result = new Intent();
        result.putExtra(EXTRA_CLEARED_ALL, count == mCount);
        setResult(RESULT_OK, result);
        finish();
    }
}
