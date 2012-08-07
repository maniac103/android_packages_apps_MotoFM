package com.motorola.fmradio;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

import com.motorola.fmradio.FMDataProvider.Channels;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

public class PresetBackupHelper {
    private static final String TAG = "PresetBackupHelper";

    private static final String ROOT_ELEMENT = "fmradio";
    private static final String PRESETS_ELEMENT = "presets";
    private static final String PRESET_ELEMENT = "preset";
    private static final String FREQUENCY_ELEMENT = "frequency";
    private static final String NAME_ELEMENT = "name";
    private static final String INDEX_ATTRIBUTE = "index";

    private PresetBackupHelper() {
        /* this class is not supposed to be instantiated */
    }

    public static boolean backupPresets(Context context, File destination) {
        OutputStream os = null;
        boolean result = false;

        Cursor cursor = context.getContentResolver().query(Channels.CONTENT_URI,
                FMUtil.PROJECTION, null, null, null);
        if (cursor == null) {
            return false;
        }

        try {
            final File dir = destination.getParentFile();
            dir.mkdirs();

            os = new FileOutputStream(destination.getAbsolutePath());

            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(os, "UTF-8");
            serializer.startDocument(null, Boolean.TRUE);

            // Output with indentation
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startTag(null, ROOT_ELEMENT);

            serializer.startTag(null, PRESETS_ELEMENT);
            exportPresets(serializer, cursor);
            serializer.endTag(null, PRESETS_ELEMENT);

            serializer.endTag(null, ROOT_ELEMENT);
            serializer.endDocument();
            serializer.flush();

            result = true;
        } catch (IOException e) {
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                }
            }
        }

        cursor.close();

        return result;
    }

    private static void exportPresets(XmlSerializer serializer, Cursor cursor) throws IOException {
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            int frequency = cursor.getInt(FMUtil.CHANNEL_COLUMN_FREQ);
            if (frequency != 0) {
                int id = cursor.getInt(FMUtil.CHANNEL_COLUMN_ID);
                String name = cursor.getString(FMUtil.CHANNEL_COLUMN_NAME);

                serializer.startTag(null, PRESET_ELEMENT);
                serializer.attribute(null, INDEX_ATTRIBUTE, Integer.toString(id + 1));
                writeElement(serializer, FREQUENCY_ELEMENT, Integer.toString(frequency));
                writeElement(serializer, NAME_ELEMENT, name);
                serializer.endTag(null, PRESET_ELEMENT);
            }
            cursor.moveToNext();
        }
    }

    private static void writeElement(XmlSerializer serializer, String name, String value)
            throws IOException {
        if (!TextUtils.isEmpty(value)) {
            serializer.startTag(null, name);
            serializer.text(value);
            serializer.endTag(null, name);
        }
    }

    private static class PresetDescription {
        public int index;
        public int frequency;
        public String name;

        public boolean isValid() {
            return index > 0 && frequency >= FMUtil.MIN_FREQUENCY && frequency <= FMUtil.MAX_FREQUENCY;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Preset description for index ");
            sb.append(index);
            sb.append(": frequency ");
            sb.append(frequency);
            sb.append(", name ");
            sb.append(TextUtils.isEmpty(name) ? "<empty>" : name);
            return sb.toString();
        }
    }

    public static int restorePresets(Context context, File source) {
        InputStream is = null;
        HashMap<Integer, PresetDescription> importResults = null;

        try {
            is = new FileInputStream(source);
            importResults = parseBackup(is);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Preset backup " + source.getAbsolutePath() + " not found", e);
        } catch (IOException e) {
            Log.w(TAG, "Could not read from backup file", e);
        } catch (XmlPullParserException e) {
            Log.w(TAG, "Invalid XML in backup file", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }

        if (importResults == null) {
            return -1;
        }

        /* flush DB */
        ContentResolver cr = context.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(Channels.FREQUENCY, 0);
        cv.put(Channels.NAME, "");
        cv.put(Channels.RDS_NAME, "");
        cr.update(Channels.CONTENT_URI, cv, null, null);

        /* save import results */
        for (PresetDescription desc : importResults.values()) {
            Log.d(TAG, "Importing preset " + desc);
            cv = new ContentValues();
            cv.put(Channels.FREQUENCY, desc.frequency);
            cv.put(Channels.NAME, desc.name);

            Uri uri = Uri.withAppendedPath(Channels.CONTENT_URI, String.valueOf(desc.index - 1));
            cr.update(uri, cv, null, null);
        }

        return importResults.size();
    }

    private static HashMap<Integer, PresetDescription> parseBackup(InputStream is)
            throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser xpp = factory.newPullParser();

        InputStreamReader reader = new InputStreamReader(is);
        xpp.setInput(reader);

        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (TextUtils.equals(xpp.getName(), ROOT_ELEMENT)) {
                    return parseRootElement(xpp);
                }
            }
            eventType = xpp.next();
        }

        return null;
    }

    private static HashMap<Integer, PresetDescription> parseRootElement(XmlPullParser xpp)
            throws XmlPullParserException, IOException {
        HashMap<Integer, PresetDescription> result = new HashMap<Integer, PresetDescription>();
        int eventType = xpp.next();

        while (!(eventType == XmlPullParser.END_TAG && TextUtils.equals(xpp.getName(), ROOT_ELEMENT))) {
            if (eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (TextUtils.equals(element, PRESETS_ELEMENT)) {
                    parsePresets(xpp, result);
                }
            }
            eventType = xpp.next();
        }

        return result;
    }

    private static void parsePresets(XmlPullParser xpp, HashMap<Integer, PresetDescription> results)
            throws XmlPullParserException, IOException {
        int eventType = xpp.next();

        while (!(eventType == XmlPullParser.END_TAG && TextUtils.equals(xpp.getName(), PRESETS_ELEMENT))) {
            if (eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (TextUtils.equals(element, PRESET_ELEMENT)) {
                    PresetDescription desc = parsePreset(xpp);
                    if (desc != null && desc.isValid()) {
                        results.put(desc.index, desc);
                    }
                }
            }
            eventType = xpp.next();
        }
    }

    private static PresetDescription parsePreset(XmlPullParser xpp)
            throws XmlPullParserException, IOException {
        PresetDescription desc = new PresetDescription();
        String index = xpp.getAttributeValue(null, INDEX_ATTRIBUTE);

        try {
            desc.index = Integer.valueOf(index);
        } catch (NullPointerException e) {
            skipToEndTag(xpp, PRESET_ELEMENT);
            Log.w(TAG, "Preset description is missing the index attribute");
            return null;
        } catch (NumberFormatException e) {
            skipToEndTag(xpp, PRESET_ELEMENT);
            Log.w(TAG, "Preset description index '" + index + "' is not a valid number");
            return null;
        }

        int eventType = xpp.next();

        while (!(eventType == XmlPullParser.END_TAG && TextUtils.equals(xpp.getName(), PRESET_ELEMENT))) {
            if (eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (TextUtils.equals(element, FREQUENCY_ELEMENT)) {
                    String value = getText(xpp);
                    try {
                        desc.frequency = Integer.valueOf(value);
                    } catch (NumberFormatException e) {
                    }
                } else if (TextUtils.equals(element, NAME_ELEMENT)) {
                    desc.name = getText(xpp);
                }
            }
            eventType = xpp.next();
        }

        return desc;
    }

    private static void skipToEndTag(XmlPullParser xpp, String tag)
            throws XmlPullParserException, IOException {
        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG && TextUtils.equals(xpp.getName(), tag))) {
            eventType = xpp.next();
        }
    }

    private static String getText(XmlPullParser xpp)
            throws XmlPullParserException, IOException {
        int eventType = xpp.next();
        if (eventType != XmlPullParser.TEXT) {
            return "";
        }
        return xpp.getText();
    }
}
