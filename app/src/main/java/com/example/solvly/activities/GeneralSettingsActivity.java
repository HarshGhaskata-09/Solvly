package com.example.solvly.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.solvly.R;
import com.example.solvly.utils.HistoryDbHelper;

public class GeneralSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_settings);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());

        // Solving Preferences
        setupRow(R.id.itemSolutionDepth, "Solution Detail", R.drawable.ic_workspace);
        setupRow(R.id.itemTrigonometry, "Trigonometry (Deg/Rad)", R.drawable.ic_math);
        setupRow(R.id.itemSolverLogic, "Alternative Methods", R.drawable.ic_about);

        // App Behavior
        setupRow(R.id.itemLanguage, "App Language", R.drawable.ic_language);
        setupRow(R.id.itemInputPrefs, "Camera Auto-Focus", R.drawable.ic_camera_outline);
        setupRow(R.id.itemHaptic, "Haptic Feedback", R.drawable.ic_notification);

        // History & Data
        setupHistorySettings();
        setupClearCache();
    }

    private void setupHistorySettings() {
        View rowAutoSave = findViewById(R.id.itemAutoSave);
        View rowAutoDelete = findViewById(R.id.itemAutoDelete);
        
        TextView tvAutoSaveTitle = rowAutoSave.findViewById(R.id.settingTitle);
        ImageView ivAutoSaveIcon = rowAutoSave.findViewById(R.id.settingIcon);
        TextView tvAutoSaveSubtitle = rowAutoSave.findViewById(R.id.settingSubtitle);
        tvAutoSaveTitle.setText("Auto-Save History");
        ivAutoSaveIcon.setImageResource(R.drawable.ic_history);
        tvAutoSaveSubtitle.setVisibility(View.VISIBLE);
        
        TextView tvAutoDeleteTitle = rowAutoDelete.findViewById(R.id.settingTitle);
        ImageView ivAutoDeleteIcon = rowAutoDelete.findViewById(R.id.settingIcon);
        TextView tvAutoDeleteSubtitle = rowAutoDelete.findViewById(R.id.settingSubtitle);
        tvAutoDeleteTitle.setText("Auto-Delete Old Data");
        ivAutoDeleteIcon.setImageResource(R.drawable.ic_delete_custom);
        tvAutoDeleteSubtitle.setVisibility(View.VISIBLE);

        SharedPreferences prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);
        
        Runnable updateUI = () -> {
            boolean autoSave = prefs.getBoolean("auto_save_history", true);
            tvAutoSaveSubtitle.setText(autoSave ? "Yes" : "No");
            
            if (autoSave) {
                rowAutoDelete.setVisibility(View.VISIBLE);
                int deleteDays = prefs.getInt("auto_delete_days", 30);
                if (deleteDays == 1) tvAutoDeleteSubtitle.setText("Instant delete (24 hours)");
                else if (deleteDays == 15) tvAutoDeleteSubtitle.setText("Delete after 15 days");
                else if (deleteDays == 30) tvAutoDeleteSubtitle.setText("Delete after 30 days");
                else if (deleteDays == 60) tvAutoDeleteSubtitle.setText("Delete after 60 days");
                else tvAutoDeleteSubtitle.setText("Never delete");
            } else {
                rowAutoDelete.setVisibility(View.GONE);
            }
        };
        
        updateUI.run();
        
        rowAutoSave.setOnClickListener(v -> {
            String[] options = {"Yes", "No"};
            boolean autoSave = prefs.getBoolean("auto_save_history", true);
            int checkedItem = autoSave ? 0 : 1;
            final int[] pendingChoice = {checkedItem};

            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Auto-Save History")
                .setSingleChoiceItems(options, checkedItem, (d, which) -> {
                    pendingChoice[0] = which;
                })
                .setPositiveButton("OK", (d, which) -> {
                    boolean newValue = (pendingChoice[0] == 0);
                    prefs.edit().putBoolean("auto_save_history", newValue).apply();
                    if (!newValue) {
                        HistoryDbHelper.getInstance(GeneralSettingsActivity.this).clearAll();
                        Toast.makeText(GeneralSettingsActivity.this, R.string.toast_history_cleared_disabled, Toast.LENGTH_SHORT).show();
                    }
                    updateUI.run();
                    d.dismiss();
                })
                .setNegativeButton(R.string.common_cancel, (d, which) -> d.dismiss());

            AlertDialog dialog = builder.create();
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_rounded_dialog);
            }
        });
        
        rowAutoDelete.setOnClickListener(v -> {
            String[] options = {"Instant delete (24 hours)", "Delete after 15 days", "Delete after 30 days", "Delete after 60 days", "Never delete"};
            int[] values = {1, 15, 30, 60, -1};
            
            int currentVal = prefs.getInt("auto_delete_days", 30);
            int checkedItem = 2; // Default 30 days
            for (int i = 0; i < values.length; i++) {
                if (values[i] == currentVal) {
                    checkedItem = i;
                    break;
                }
            }
            
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Auto-Delete Old Data")
                .setSingleChoiceItems(options, checkedItem, (d, which) -> {
                    prefs.edit().putInt("auto_delete_days", values[which]).apply();
                    HistoryDbHelper.getInstance(GeneralSettingsActivity.this).deleteOldHistory(values[which]);
                    updateUI.run();
                    d.dismiss();
                })
                .create();
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_rounded_dialog);
            }
        });
    }

    private void setupClearCache() {
        View row = findViewById(R.id.itemClearCache);
        if (row == null) return;

        TextView tvTitle = row.findViewById(R.id.settingTitle);
        TextView tvSubtitle = row.findViewById(R.id.settingSubtitle);
        ImageView ivIcon = row.findViewById(R.id.settingIcon);

        tvTitle.setText("Clear Cache");
        ivIcon.setImageResource(R.drawable.ic_clear_cache);
        tvSubtitle.setVisibility(View.VISIBLE);
        
        refreshCacheSize(tvSubtitle);

        row.setOnClickListener(v -> {
            clearAppCache();
            Toast.makeText(this, R.string.toast_cache_cleared, Toast.LENGTH_SHORT).show();
            refreshCacheSize(tvSubtitle);
        });
    }

    private void refreshCacheSize(TextView tvSubtitle) {
        long size = getDirSize(getCacheDir());
        tvSubtitle.setText(formatSize(size));
    }

    private long getDirSize(java.io.File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        if (dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    size += getDirSize(file);
                }
            }
        } else {
            size = dir.length();
        }
        return size;
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void clearAppCache() {
        try {
            java.io.File cacheDir = getCacheDir();
            java.io.File[] children = cacheDir != null ? cacheDir.listFiles() : null;
            if (children != null) {
                for (java.io.File child : children) {
                    deleteDir(child);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean deleteDir(java.io.File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new java.io.File(dir, child));
                    if (!success) return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private void setupRow(int id, String title, int iconRes) {
        View row = findViewById(id);
        if (row == null) return;

        TextView tvTitle = row.findViewById(R.id.settingTitle);
        ImageView ivIcon = row.findViewById(R.id.settingIcon);

        if (tvTitle != null) tvTitle.setText(title);
        if (ivIcon != null && iconRes != 0) ivIcon.setImageResource(iconRes);


    }
}
