package org.duckassist.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class ChatDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "ChatDatabaseHelper";
    private static final String DATABASE_NAME = "duck_chats.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_CHAT_CACHE = "chat_cache";
    private static final String COLUMN_KEY = "cache_key";
    private static final String COLUMN_JSON = "json_data";

    private static final String KEY_CHATS_JSON = "cached_chats_json";
    private static final String CACHE_FILE_NAME = "duck_chats_cache.json";

    private static ChatDatabaseHelper instance;
    private final Context context;

    public static synchronized ChatDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ChatDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    public ChatDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
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

        // 1. Save to internal app file storage (no CursorWindow 2MB limit, handles large documents & base64 attachments safely)
        try {
            File cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
            File tempFile = new File(context.getFilesDir(), CACHE_FILE_NAME + ".tmp");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                writer.write(json);
                writer.flush();
            }
            if (tempFile.exists()) {
                if (cacheFile.exists()) {
                    cacheFile.delete();
                }
                tempFile.renameTo(cacheFile);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error saving chats JSON to file storage", t);
        }

        // 2. Also save to SQLite if small enough (under 1MB) for backwards compatibility
        if (json.length() < 1000000) {
            try {
                SQLiteDatabase db = getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(COLUMN_KEY, KEY_CHATS_JSON);
                values.put(COLUMN_JSON, json);
                db.insertWithOnConflict(TABLE_CHAT_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (Throwable t) {
                Log.w(TAG, "Optional SQLite backup save skipped or failed", t);
            }
        } else {
            // Clean up potentially oversized SQLite row to avoid CursorWindow crashes
            try {
                SQLiteDatabase db = getWritableDatabase();
                db.delete(TABLE_CHAT_CACHE, COLUMN_KEY + " = ?", new String[]{KEY_CHATS_JSON});
            } catch (Throwable ignored) {}
        }
    }

    public synchronized String getCachedChatsJson() {
        // 1. Try reading from internal file storage
        File cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder((int) Math.min(cacheFile.length(), 65536));
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
                String content = sb.toString();
                if (!content.isEmpty() && !content.equals("{}")) {
                    return content;
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error loading chats JSON from file storage", t);
            }
        }

        // 2. Fallback / migration: Try reading from SQLite database if file not found
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getReadableDatabase();
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
                    if (json != null && !json.isEmpty() && !json.equals("{}")) {
                        // Migrate to file storage
                        saveCachedChatsJson(json);
                        return json;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not load chats JSON from SQLite database (may exceed CursorWindow limit)", t);
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
