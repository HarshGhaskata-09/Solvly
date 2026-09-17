package com.example.solvly.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class HistoryDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "history.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_HISTORY  = "history";
    public static final String COLUMN_ID        = "id";
    public static final String COLUMN_TITLE     = "title";
    public static final String COLUMN_PROBLEM   = "problem";
    public static final String COLUMN_ANSWER    = "answer";
    public static final String COLUMN_STEPS     = "steps";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_HISTORY + " (" +
                    COLUMN_ID        + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TITLE     + " TEXT, " +
                    COLUMN_PROBLEM   + " TEXT, " +
                    COLUMN_ANSWER    + " TEXT, " +
                    COLUMN_STEPS     + " TEXT, " +
                    COLUMN_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";

    private static volatile HistoryDbHelper instance;

    private HistoryDbHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static HistoryDbHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (HistoryDbHelper.class) {
                if (instance == null) {
                    instance = new HistoryDbHelper(context);
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN " + COLUMN_STEPS + " TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN " + COLUMN_TITLE + " TEXT");
        }
    }

    // ─── Sanitize title: single source of truth for BOTH addHistory() and HistoryItem ───
    private static String sanitizeTitle(String title, String problem) {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        return generateFallbackTitle(problem);
    }

    // ─── Save entry with auto-generated title ────────────────────────────────
    public void addHistory(String title, String problem, String answer, List<String> steps) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE,   sanitizeTitle(title, problem));
        values.put(COLUMN_PROBLEM, problem);
        values.put(COLUMN_ANSWER,  answer);

        org.json.JSONArray array = new org.json.JSONArray();
        if (steps != null) {
            for (String s : steps) array.put(s);
        }
        values.put(COLUMN_STEPS, array.toString());

        db.insert(TABLE_HISTORY, null, values);
    }

    // Fallback: first 6 words of problem text → title
    public static String generateFallbackTitle(String problem) {
        if (problem == null || problem.trim().isEmpty()) return "Untitled Problem";
        String[] words = problem.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(6, words.length); i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        String result = sb.toString();
        if (words.length > 6) result += "…";
        return result;
    }

    public List<HistoryItem> getAllHistory() {
        List<HistoryItem> historyList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT * FROM " + TABLE_HISTORY + " ORDER BY " + COLUMN_TIMESTAMP + " DESC", null);
            if (cursor.moveToFirst()) {
                do {
                    int    id        = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                    String title     = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                    String problem   = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROBLEM));
                    String answer    = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ANSWER));
                    String stepsJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STEPS));
                    String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                    historyList.add(new HistoryItem(id, title, problem, answer, stepsJson, timestamp));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return historyList;
    }

    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
    }

    public void deleteOldHistory(int days) {
        // Values <= 0 include the sentinel -1 ("Never delete"); bail out defensively.
        if (days < 1) return;
        SQLiteDatabase db = this.getWritableDatabase();
        String whereClause = COLUMN_TIMESTAMP + " <= datetime('now', '-" + Math.max(1, days) + " days')";
        db.delete(TABLE_HISTORY, whereClause, null);
    }

    public void deleteHistory(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORY, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }

    // ─── Data Model ──────────────────────────────────────────────────────────
    public static class HistoryItem {
        public int    id;
        public String title;
        public String problem;
        public String answer;
        public List<String> steps;
        public String timestamp;

        public HistoryItem(int id, String title, String problem, String answer,
                           String stepsJson, String timestamp) {
            this.id        = id;
            this.title     = sanitizeTitle(title, problem);
            this.problem   = problem;
            this.answer    = answer;
            this.timestamp = timestamp;
            this.steps     = new ArrayList<>();
            try {
                if (stepsJson != null) {
                    org.json.JSONArray array = new org.json.JSONArray(stepsJson);
                    for (int i = 0; i < array.length(); i++) {
                        this.steps.add(array.getString(i));
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
