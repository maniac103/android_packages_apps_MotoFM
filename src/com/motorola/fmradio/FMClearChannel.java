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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FMClearChannel extends ListActivity implements View.OnClickListener {
    public static final String TAG = "FMClearChannel";

    public static final String AUTHORITY = "com.motorola.provider.fmradio";
    public static final Uri CONTENT_URI = Uri.parse("content://com.motorola.provider.fmradio/FM_Radio");

    public static final int CLEAR_ID = 1;
    public static final int SELECT_ALL_ID = 2;
    public static final int UNSELECT_ALL_ID = 3;

    private SparseBooleanArray checked;
    private int checked_num = 0;
    private ListView listView;
    private Button mBtnDone;

    private void noticeFMRadioMainUpdateVol(String cmd, int action, int keyCode) {
        Intent intent = new Intent(cmd);
        intent.putExtra("event_action", action);
        intent.putExtra("event_keycode", keyCode);
        sendBroadcast(intent);
    }

    @Override
    public void onClick(View button) {
        if (button.getId() == R.id.btn_done) {
            checked = listView.getCheckedItemPositions();
            for (int i = 1; i < 21; i++) {
                if (checked.get(i)) {
                    checked_num++;
                    Log.d("FMRadio clear", "checked_num is " + checked_num);
                    ContentValues cv = new ContentValues();
                    cv.put("ID", i - 1);
                    cv.put("CH_Freq", "");
                    cv.put("CH_Name", "");
                    getContentResolver().update(getIntent().getData(), cv, "ID=" + (i - 1), null);
                }
            }
            Intent clear_result = new Intent();
            clear_result.putExtra("isClearAll", checked_num == 20);
            setResult(-1, clear_result);
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.clear_ch);
        setTitle(R.string.clear_presets);
        listView = (ListView) findViewById(android.R.id.list);
        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(CONTENT_URI);
        }
        Cursor cur = getContentResolver().query(getIntent().getData(), FMUtil.PROJECTION, null, null, null);
        ArrayList<Map<String, Object> > coll = new ArrayList<Map<String, Object> >();
        Map<String, Object> item = new HashMap<String, Object>();
        item.put("c1", getString(R.string.all_presets));
        coll.add(item);
        if (cur != null) {
            cur.moveToFirst();
            int i = 1;
            while (!cur.isAfterLast()) {
                item = new HashMap<String, Object>();
                String chName = cur.getString(3);
                String chFreq = cur.getString(2);
                if (FMUtil.isEmptyStr(chName)) {
                    if (FMUtil.isEmptyStr(chFreq)) {
                        item.put("c1", "    " + getString(R.string.preset) + " " + i + " (" + getString(R.string.empty) + ")");
                    } else {
                        item.put("c1", "    " + getString(R.string.preset) + " " + i + " (" + chFreq + "MHz" + ")");
                    }
                } else {
                    String chNameTmp = chName.replaceAll("\n", " ");
                    item.put("c1", "    " + getString(R.string.preset) + " " + i + " (" + chNameTmp + ")");
                }
                coll.add(item);
                cur.moveToNext();
                i++;
            }
            cur.close();
        }
        Log.d(TAG, "get data to coll, ready to setListAdapter. ");
        ViewBinder viewBinder = new ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object obj, String str) {
                CheckedTextView v = (CheckedTextView) view;
                v.setEllipsize(TruncateAt.END);
                v.setText(obj.toString());
                return true;
            }
        };
        SimpleAdapter sa = new SimpleAdapter(this, coll, R.layout.simple_choice, new String[] { "c1" }, new int[] { R.id.text1 });
        setListAdapter(sa);
        Log.d(TAG, "after setListAdapter!");
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    boolean result = listView.isItemChecked(position);
                    for (int j = 1; j < 21; j++) {
                        listView.setItemChecked(j, result);
                    }
                }
            }
        });
        listView.setFocusableInTouchMode(true);
        listView.requestFocus();
        listView.setFocusable(true);
        listView.setItemsCanFocus(true);
        listView.setChoiceMode(2);

        mBtnDone = (Button) findViewById(R.id.btn_done);
        mBtnDone.setOnClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 1, 0, R.string.clear).setIcon(R.drawable.ic_menu_clear_channel);
        menu.add(0, 2, 1, R.string.select_all).setIcon(R.drawable.ic_menu_select_all);
        menu.add(0, 3, 2, R.string.unselect_all).setIcon(R.drawable.ic_menu_unselect_all);
        return true;
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
                checked = listView.getCheckedItemPositions();
                for (int i = 1; i < 21; i++) {
                    if (checked.get(i)) {
                        checked_num++;
                        Log.d("FMRadio clear", "checked_num is " + checked_num);
                        ContentValues cv = new ContentValues();
                        cv.put("ID", i - 1);
                        cv.put("CH_Freq", "");
                        cv.put("CH_Name", "");
                        getContentResolver().update(getIntent().getData(), cv, "ID=" + (i - 1), null);
                    }
                }
                Intent clear_result = new Intent();
                clear_result.putExtra("isClearAll", checked_num == 20);
                setResult(-1, clear_result);
                finish();
                break;
            case 2:
                for (int j = 0; j < 21; j++) {
                    listView.setItemChecked(j, true);
                }
                break;
            case 3:
                for (int j = 0; j < 21; j++) {
                    listView.setItemChecked(j, false);
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
