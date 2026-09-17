package com.example.solvly;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class SolvlyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        applySavedTheme();
    }

    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);
        String theme = prefs.getString("pref_theme", "system");
        
        switch (theme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
