package org.duckassist.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class ChatDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "ChatDatabaseHelper";
    private static final String DATABASE_NAME = "duck_chats.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_CHAT_CACHE = "chat_cache";
    private static final String COLUMN_KEY = "cache_key";
    private static final String COLUMN_JSON = "json_data";

    private static final String KEY_CHATS_JSON = "cached_chats_json";

    private static ChatDatabaseHelper instance;

    public static synchronized ChatDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ChatDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    public ChatDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS " + TABLE_CHAT_CACHE + " (" +
                COLUMN_KEY + " TEXT PRIMARY KEY, " +
                COLUMN_JSON + " TEXT)";
        db.execSQL(createTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future database migrations if schema changes
    }

    public synchronized void saveCachedChatsJson(String json) {
        if (json == null || json.isEmpty()) return;
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_KEY, KEY_CHATS_JSON);
            values.put(COLUMN_JSON, json);
            db.insertWithOnConflict(TABLE_CHAT_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Throwable t) {
            Log.e(TAG, "Error saving chats JSON to SQLite database", t);
        }
    }

    public synchronized String getCachedChatsJson() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = getReadableDatabase();
            cursor = db.query(
                    TABLE_CHAT_CACHE,
                    new String[]{COLUMN_JSON},
                    COLUMN_KEY + " = ?",
                    new String[]{KEY_CHATS_JSON},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int colIdx = cursor.getColumnIndex(COLUMN_JSON);
                if (colIdx != -1) {
                    String json = cursor.getString(colIdx);
                    if (json != null && !json.isEmpty()) {
                        return json;
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error loading chats JSON from SQLite database", t);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ignored) {}
            }
        }
        return "{}";
    }
}
