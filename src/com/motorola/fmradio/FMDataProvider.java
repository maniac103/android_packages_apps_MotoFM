package com.motorola.fmradio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

public class FMDataProvider extends ContentProvider {
    private static final String TAG = "FMDataProvider";

    private static final String DATABASE_NAME = "FM_RadioDB.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "FM_Radio";
    private static final String SAVED_TABLE_NAME = "FM_Radio_saved_state";

    private static final int CHANNELS = 1;
    private static final int CHANNELS_ID = 2;
    private static final int SAVED_STATE = 3;
    private static final int SAVED_STATE_ID = 4;

    private static final UriMatcher sUriMatcher = new UriMatcher(-1);
    static {
        sUriMatcher.addURI("com.motorola.provider.fmradio", "FM_Radio", CHANNELS);
        sUriMatcher.addURI("com.motorola.provider.fmradio", "FM_Radio/#", CHANNELS_ID);
        sUriMatcher.addURI("com.motorola.provider.fmradio", "FM_Radio_saved_state", SAVED_STATE);
        sUriMatcher.addURI("com.motorola.provider.fmradio", "FM_Radio_saved_state/#", SAVED_STATE_ID);
    }

    private DatabaseHelper mOpenHelper;

    private class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, TABLE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            String sql_saved = "insert into FM_Radio_saved_state (ID, Last_ChNum, Last_Freq, isFirstScaned, Last_Volume) values(\'0\', \'0\', \'87.5\', \'true\', \'5\');";
            try {
                db.execSQL("CREATE TABLE FM_Radio_saved_state (ID INTEGER,Last_ChNum INTEGER,Last_Freq FLOAT,isFirstScaned BOOLEAN,Last_Volume INTEGER);");
                db.execSQL("CREATE TABLE FM_Radio (ID INTEGER,CH_Num TEXT,CH_Freq FLOAT,CH_Name TEXT,CH_RdsName TEXT);");
                db.execSQL(sql_saved);
                for (int i = 0; i < 20; i++) {
                    db.execSQL("insert into FM_Radio (ID, CH_Num, CH_Freq, CH_Name, CH_RdsName) values(\'" + i + "\', \'Preset" + (i + 1) + "\', \'\', \'\', \'\');");
                }
            } catch (SQLException e) {
                Log.e(TAG, e.toString());
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        switch (sUriMatcher.match(uri)) {
            case CHANNELS:
            case CHANNELS_ID:
                Log.d(TAG, "set channel table: FM_Radio");
                qb.setTables(TABLE_NAME);
                break;
            case SAVED_STATE:
            case SAVED_STATE_ID:
                Log.d(TAG, "set save table: FM_Radio_saved_state");
                qb.setTables(SAVED_TABLE_NAME);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, null);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        } else {
            Log.d(TAG, "It was not possible to set notification URI to cursor");
        }
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        if (initialValues.get("ID") == null
                || initialValues.get("CH_Num") == null
                || initialValues.get("CH_Freq") == null
                || initialValues.get("CH_Name") == null
                || initialValues.get("CH_RdsName") == null) {
            throw new IllegalArgumentException("Null values when adding to " + uri);
        }
        String field_1 = initialValues.get("ID").toString();
        String field_2 = initialValues.get("CH_Num").toString();
        String field_3 = initialValues.get("CH_Freq").toString();
        String field_4 = initialValues.get("CH_Name").toString();
        String field_5 = initialValues.get("CH_RdsName").toString();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        try {
            db.execSQL("insert into FM_Radio (ID, CH_Num, CH_Freq, CH_Name, CH_RdsName) values(\'" +
                    field_1 + "\', \'" + field_2 + "\', \'" + field_3 +
                    "\', \'" + field_4 + "\', \'" + field_5);
        } catch (SQLiteException e) {
            Log.e(TAG, e.toString());
        }
        return uri;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = 0;

        switch (sUriMatcher.match(uri)) {
            case CHANNELS:
            case CHANNELS_ID:
                count = db.update(TABLE_NAME, values, where, whereArgs);
                break;
            case SAVED_STATE:
            case SAVED_STATE_ID:
                count = db.update(SAVED_TABLE_NAME, values, where, whereArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.delete(where, whereArgs[0] + "=\'" + whereArgs[1] + "\'", null);
        return 0;
    }
}
