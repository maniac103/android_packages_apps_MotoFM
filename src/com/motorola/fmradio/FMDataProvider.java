package com.motorola.fmradio;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

public class FMDataProvider extends ContentProvider {
    private static final String TAG = "FMDataProvider";

    private static final String AUTHORITY = "com.motorola.provider.fmradio";
    private static final String DATABASE_NAME = "fmradio.db";
    private static final int DATABASE_VERSION = 1;

    private static final String CHANNEL_TABLE = "channels";
    private static final int CHANNEL_COUNT = 20;

    public static class Channels {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/channels");
        public static final String ID = "_id";
        public static final String FREQUENCY = "frequency";
        public static final String NAME = "name";
        public static final String RDS_NAME = "rds_name";
    };

    private static final int CHANNELS = 1;
    private static final int CHANNELS_ID = 2;

    private static final UriMatcher sUriMatcher = new UriMatcher(-1);
    static {
        sUriMatcher.addURI(AUTHORITY, "channels", CHANNELS);
        sUriMatcher.addURI(AUTHORITY, "channels/#", CHANNELS_ID);
    }

    private DatabaseHelper mOpenHelper;

    private class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            try {
                db.execSQL("CREATE TABLE channels ("
                        + "_id INTEGER PRIMARY KEY,"
                        + "frequency INT NOT NULL DEFAULT 0,"
                        + "name TEXT,"
                        + "rds_name TEXT"
                        + ");");
                for (int i = 0; i < CHANNEL_COUNT; i++) {
                    db.execSQL("insert into channels (_id, frequency, name, rds_name) " +
                            "values(\'" + i + "\', \'0\', \'\', \'\');");
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
                qb.setTables(CHANNEL_TABLE);
                break;
            case CHANNELS_ID: {
                long id = ContentUris.parseId(uri);
                qb.setTables(CHANNEL_TABLE);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(id));
                qb.appendWhere("_id=?");
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, null);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = 0;

        switch (sUriMatcher.match(uri)) {
            case CHANNELS:
                count = db.update(CHANNEL_TABLE, values, where, whereArgs);
                break;
            case CHANNELS_ID: {
                long id = ContentUris.parseId(uri);
                count = db.update(CHANNEL_TABLE, values, "_id=?", new String[] { String.valueOf(id) });
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }


        if (count > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        return 0;
    }

    private String[] insertSelectionArg(String[] selectionArgs, String arg) {
        if (selectionArgs == null) {
            return new String[] { arg };
        } else {
            int newLength = selectionArgs.length + 1;
            String[] newSelectionArgs = new String[newLength];
            newSelectionArgs[0] = arg;
            System.arraycopy(selectionArgs, 0, newSelectionArgs, 1, selectionArgs.length);
            return newSelectionArgs;
        }
    }
}
