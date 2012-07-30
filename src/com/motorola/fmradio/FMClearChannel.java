package com.motorola.fmradio;

import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils.TruncateAt;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.motorola.fmradio.FMDataProvider.Channels;

import java.util.ArrayList;

public class FMClearChannel extends ListActivity implements View.OnClickListener {
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

        Cursor cursor = getContentResolver().query(Channels.CONTENT_URI, FMUtil.PROJECTION, null, null, null);
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

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                R.layout.simple_choice, R.id.text1, items) {
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
        menu.add(Menu.NONE, CLEAR_ID, Menu.FIRST, R.string.clear)
                .setIcon(R.drawable.ic_menu_clear_channel);
        menu.add(Menu.NONE, SELECT_ALL_ID, Menu.FIRST + 1, R.string.select_all)
                .setIcon(R.drawable.ic_menu_select_all);
        menu.add(Menu.NONE, UNSELECT_ALL_ID, Menu.FIRST + 2, R.string.unselect_all)
                .setIcon(R.drawable.ic_menu_unselect_all);
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

    private void doClear() {
        SparseBooleanArray checked = mListView.getCheckedItemPositions();
        int count = 0;

        ContentValues cv = new ContentValues();
        cv.put(Channels.FREQUENCY, 0);
        cv.put(Channels.NAME, "");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mCount; i++) {
            if (checked.get(i + 1)) {
                if (sb.length() > 0) {
                    sb.append(" OR ");
                }
                sb.append(Channels.ID);
                sb.append("=");
                sb.append(i);
                count++;
            }
        }

        getContentResolver().update(Channels.CONTENT_URI, cv, sb.toString(), null);

        Intent result = new Intent();
        result.putExtra(EXTRA_CLEARED_ALL, count == mCount);
        setResult(RESULT_OK, result);
        finish();
    }
}
