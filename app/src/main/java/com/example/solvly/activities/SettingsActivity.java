package com.example.solvly.activities;

import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Size;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.solvly.BuildConfig;
import com.example.solvly.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {
    private static final String STATE_BUG_IMAGES = "state_bug_images";
    private static final String STATE_PERSISTED_BUG_IMAGES = "state_persisted_bug_images";
    private static final String STATE_BUG_REPORT_TEXT = "state_bug_report_text";
    private static final String STATE_BUG_SHEET_OPEN = "state_bug_sheet_open";

    private final ArrayList<Uri> bugImages = new ArrayList<>();
    private final Set<String> persistedBugImageUris = new LinkedHashSet<>();
    private final ExecutorService previewExecutor = Executors.newFixedThreadPool(2);

    private ActivityResultLauncher<String[]> imagePickerLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private LinearLayout imagePreviewContainer;
    private TextView tvAttachmentCount;
    private View buttonAddImage;
    private BottomSheetDialog bugReportDialog;
    private EditText editTextBugReport;
    private String pendingBugReportText = "";
    private boolean reopenBugSheetOnResume = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());

        // General
        setupRow(R.id.itemGeneral, "General Settings", R.drawable.ic_settings_custom);
        setupRow(R.id.itemNotifications, "Notifications", R.drawable.ic_notification);
        setupRow(R.id.itemHistory, "Problem History", R.drawable.ic_history);
        setupClearCache();

        // Appearance
        setupRow(R.id.itemAccentColor, "Accent Color", R.drawable.ic_accent);

        // Account & Workspace
        setupRow(R.id.itemSubscription, "Subscription", R.drawable.ic_subscription);
        setupRow(R.id.itemWorkspace, "Workspace", R.drawable.ic_workspace);
        setupRow(R.id.itemDataControl, "Data Control", R.drawable.ic_datacontrol);
        setupRow(R.id.itemSecurity, "Security", R.drawable.ic_security);

        // Support & Info
        setupRow(R.id.itemReportBug, "Report Bug", R.drawable.ic_bug);
        SharedPreferences prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);
        String currentTheme = prefs.getString("pref_theme", "system");
        int themeIcon = R.drawable.ic_theme;
        if ("light".equals(currentTheme)) themeIcon = R.drawable.ic_light;
        else if ("dark".equals(currentTheme)) themeIcon = R.drawable.ic_dark;

        setupRow(R.id.itemTheme, "Theme", themeIcon);
        setupRow(R.id.itemPolicy, "Policy", R.drawable.ic_policy);
        setupRow(R.id.itemLicence, "Licence", R.drawable.ic_licence);
        setupRow(R.id.itemAbout, "About Solvly", R.drawable.ic_about);

        findViewById(R.id.itemHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        });

        TextView tvVersion = findViewById(R.id.textAppVersion);
        if (tvVersion != null) {
            tvVersion.setText("Solvly v" + BuildConfig.VERSION_NAME);
        }

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                imageUri -> {
                    if (imageUri != null && bugImages.size() < 3) {
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                        try {
                            getContentResolver().takePersistableUriPermission(imageUri, takeFlags);
                            persistedBugImageUris.add(imageUri.toString());
                        } catch (SecurityException ignored) {
                            // Some providers do not support persistable permissions; temporary read access may still work.
                        }
                        bugImages.add(imageUri);
                        updateImagePreviews();
                    }
                }
        );

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        enableNotifications();
                    } else {
                        Toast.makeText(this, R.string.toast_notif_permission_denied, Toast.LENGTH_SHORT).show();
                        setNotificationSwitch(false);
                    }
                }
        );

        setupNotificationToggle();
        restoreBugReportState(savedInstanceState);
        if (reopenBugSheetOnResume) {
            findViewById(R.id.itemReportBug).post(() -> showReportBugSheet(true));
        }
    }

    private void setupNotificationToggle() {
        View row = findViewById(R.id.itemNotifications);
        if (row == null) return;

        TextView tvTitle = row.findViewById(R.id.settingTitle);
        ImageView ivIcon = row.findViewById(R.id.settingIcon);
        androidx.appcompat.widget.SwitchCompat switchCompat = row.findViewById(R.id.settingSwitch);
        ImageView ivArrow = row.findViewById(R.id.settingArrow);

        tvTitle.setText("Notifications");
        ivIcon.setImageResource(R.drawable.ic_notification);

        if (switchCompat != null && ivArrow != null) {
            switchCompat.setVisibility(View.VISIBLE);
            ivArrow.setVisibility(View.GONE);

            SharedPreferences prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);
            switchCompat.setChecked(prefs.getBoolean("pref_notifications", false));

            switchCompat.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    checkNotificationPermission();
                } else {
                    disableNotifications();
                }
            });

            row.setOnClickListener(v -> switchCompat.toggle());
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                enableNotifications();
            } else {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            enableNotifications();
        }
    }

    private void enableNotifications() {
        SharedPreferences prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("pref_notifications", true).apply();
        setNotificationSwitch(true);
        com.example.solvly.utils.NotificationHelper.scheduleNextNotification(this);
        Toast.makeText(this, R.string.toast_reminders_enabled, Toast.LENGTH_SHORT).show();
    }

    private void disableNotifications() {
        SharedPreferences prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("pref_notifications", false).apply();
        setNotificationSwitch(false);
        com.example.solvly.utils.NotificationHelper.cancelNotifications(this);
        Toast.makeText(this, R.string.toast_reminders_disabled, Toast.LENGTH_SHORT).show();
    }

    private void setNotificationSwitch(boolean isChecked) {
        View row = findViewById(R.id.itemNotifications);
        if (row != null) {
            androidx.appcompat.widget.SwitchCompat switchCompat = row.findViewById(R.id.settingSwitch);
            if (switchCompat != null) {
                switchCompat.setOnCheckedChangeListener(null);
                switchCompat.setChecked(isChecked);
                switchCompat.setOnCheckedChangeListener((buttonView, checked) -> {
                    if (checked) checkNotificationPermission();
                    else disableNotifications();
                });
            }
        }
    }

    private void setupRow(int id, String title, int iconRes) {
        View row = findViewById(id);
        if (row == null) return;

        TextView tvTitle = row.findViewById(R.id.settingTitle);
        ImageView ivIcon = row.findViewById(R.id.settingIcon);

        if (tvTitle != null) tvTitle.setText(title);
        if (ivIcon != null && iconRes != 0) ivIcon.setImageResource(iconRes);

        row.setOnClickListener(v -> {
            if (id == R.id.itemGeneral) {
                startActivity(new Intent(this, GeneralSettingsActivity.class));
            } else if (id == R.id.itemHistory) {
                startActivity(new Intent(this, HistoryActivity.class));
            } else if (id == R.id.itemPolicy) {
                startActivity(new Intent(this, PolicyActivity.class));
            } else if (id == R.id.itemLicence) {
                startActivity(new Intent(this, LicenceActivity.class));
            } else if (id == R.id.itemAbout) {
                startActivity(new Intent(this, AboutActivity.class));
            } else if (id == R.id.itemReportBug) {
                showReportBugSheet(false);
            } else if (id == R.id.itemTheme) {
                showThemeSelectionSheet();
            } else if (id == R.id.itemSecurity) {
                startActivity(new Intent(this, SecuritySettingsActivity.class));
            }
        });
    }

    private void restoreBugReportState(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        pendingBugReportText = savedInstanceState.getString(STATE_BUG_REPORT_TEXT, "");
        ArrayList<String> savedImages = savedInstanceState.getStringArrayList(STATE_BUG_IMAGES);
        if (savedImages != null) {
            for (String uriString : savedImages) {
                bugImages.add(Uri.parse(uriString));
            }
        }

        ArrayList<String> savedPersistedUris = savedInstanceState.getStringArrayList(STATE_PERSISTED_BUG_IMAGES);
        if (savedPersistedUris != null) {
            persistedBugImageUris.addAll(savedPersistedUris);
        }

        reopenBugSheetOnResume = savedInstanceState.getBoolean(STATE_BUG_SHEET_OPEN, false);
    }

    private void showReportBugSheet(boolean restoreState) {
        if (!restoreState) {
            clearBugReportState();
        }

        if (bugReportDialog != null && bugReportDialog.isShowing()) {
            bugReportDialog.dismiss();
        }

        bugReportDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_report_bug_sheet, null);
        bugReportDialog.setContentView(sheetView);

        editTextBugReport = sheetView.findViewById(R.id.editTextBugReport);
        imagePreviewContainer = sheetView.findViewById(R.id.imagePreviewContainer);
        tvAttachmentCount = sheetView.findViewById(R.id.textViewAttachmentCount);
        buttonAddImage = sheetView.findViewById(R.id.buttonAddImage);

        if (editTextBugReport != null) {
            editTextBugReport.setText(pendingBugReportText);
            editTextBugReport.setSelection(editTextBugReport.getText().length());
            editTextBugReport.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    pendingBugReportText = s.toString();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (buttonAddImage != null) {
            buttonAddImage.setVisibility(View.VISIBLE);
            buttonAddImage.setOnClickListener(v -> {
                if (bugImages.size() < 3) {
                    imagePickerLauncher.launch(new String[]{"image/*"});
                } else {
                    Toast.makeText(this, R.string.toast_max_3_images, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (tvAttachmentCount != null) tvAttachmentCount.setVisibility(View.VISIBLE);
        if (imagePreviewContainer != null) imagePreviewContainer.setVisibility(View.VISIBLE);
        updateImagePreviews();

        sheetView.findViewById(R.id.buttonSendBug).setOnClickListener(v -> {
            String report = editTextBugReport != null ? editTextBugReport.getText().toString().trim() : pendingBugReportText.trim();
            if (report.isEmpty()) {
                Toast.makeText(this, R.string.toast_describe_bug, Toast.LENGTH_SHORT).show();
                return;
            }

            boolean opened = sendEmail(report);
            if (opened) {
                bugReportDialog.dismiss();
                Toast.makeText(this, R.string.toast_bug_report_email_opened, Toast.LENGTH_LONG).show();
            }
        });

        bugReportDialog.setOnDismissListener(dialog -> {
            bugReportDialog = null;
            editTextBugReport = null;
            imagePreviewContainer = null;
            tvAttachmentCount = null;
            buttonAddImage = null;
            if (!isChangingConfigurations()) {
                clearBugReportState();
            }
        });

        bugReportDialog.show();
        reopenBugSheetOnResume = false;
    }

    private void showThemeSelectionSheet() {
        BottomSheetDialog bottomSheetDialog =
                new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_theme_selection_sheet, null);
        bottomSheetDialog.setContentView(sheetView);

        SharedPreferences prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);
        String currentTheme = prefs.getString("pref_theme", "system");

        RadioButton radioSystem = sheetView.findViewById(R.id.radioSystem);
        RadioButton radioLight = sheetView.findViewById(R.id.radioLight);
        RadioButton radioDark = sheetView.findViewById(R.id.radioDark);

        radioSystem.setChecked("system".equals(currentTheme));
        radioLight.setChecked("light".equals(currentTheme));
        radioDark.setChecked("dark".equals(currentTheme));

        com.google.android.material.card.MaterialCardView cardSystem = sheetView.findViewById(R.id.themeSystem);
        com.google.android.material.card.MaterialCardView cardLight = sheetView.findViewById(R.id.themeLight);
        com.google.android.material.card.MaterialCardView cardDark = sheetView.findViewById(R.id.themeDark);

        if ("system".equals(currentTheme)) cardSystem.setStrokeWidth(4);
        else if ("light".equals(currentTheme)) cardLight.setStrokeWidth(4);
        else if ("dark".equals(currentTheme)) cardDark.setStrokeWidth(4);

        cardSystem.setOnClickListener(v -> updateTheme(bottomSheetDialog, "system"));
        cardLight.setOnClickListener(v -> updateTheme(bottomSheetDialog, "light"));
        cardDark.setOnClickListener(v -> updateTheme(bottomSheetDialog, "dark"));

        bottomSheetDialog.show();
    }

    private void updateTheme(BottomSheetDialog dialog, String theme) {
        SharedPreferences prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);
        prefs.edit().putString("pref_theme", theme).apply();

        int themeIcon = R.drawable.ic_theme;
        if ("light".equals(theme)) themeIcon = R.drawable.ic_light;
        else if ("dark".equals(theme)) themeIcon = R.drawable.ic_dark;
        setupRow(R.id.itemTheme, "Theme", themeIcon);

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

        dialog.dismiss();
        Toast.makeText(this, R.string.toast_theme_applied, Toast.LENGTH_SHORT).show();
    }

    private void updateImagePreviews() {
        if (imagePreviewContainer == null) return;
        imagePreviewContainer.removeAllViews();

        for (int i = 0; i < bugImages.size(); i++) {
            Uri uri = bugImages.get(i);
            View previewView = getLayoutInflater().inflate(R.layout.item_bug_image_preview, imagePreviewContainer, false);
            ImageView iv = previewView.findViewById(R.id.imagePreview);
            View ivRemove = previewView.findViewById(R.id.buttonRemoveImage);

            loadPreviewInto(iv, uri);

            final int index = i;
            ivRemove.setOnClickListener(v -> {
                removeBugImage(index);
                updateImagePreviews();
            });

            imagePreviewContainer.addView(previewView);
        }

        if (tvAttachmentCount != null) {
            tvAttachmentCount.setText("Attach Screenshots (" + bugImages.size() + "/3)");
        }

        if (buttonAddImage != null) {
            buttonAddImage.setVisibility(bugImages.size() >= 3 ? View.GONE : View.VISIBLE);
        }
    }

    private void removeBugImage(int index) {
        if (index < 0 || index >= bugImages.size()) return;
        Uri removedUri = bugImages.remove(index);
        releasePersistedUriPermission(removedUri);
    }

    private boolean sendEmail(String content) {
        String recipient = getString(R.string.support_email);
        String subject = getString(R.string.bug_report_subject);
        String messageBody = "App: Solvly\n"
                + "Report Type: Bug Report\n\n"
                + content;

        for (String packageName : getEmailCandidatePackages()) {
            Intent supportedIntent = buildSupportedEmailIntent(recipient, subject, messageBody, packageName);
            if (supportedIntent == null) continue;
            try {
                startActivity(supportedIntent);
                return true;
            } catch (Exception ignored) {
            }
        }

        Toast.makeText(this, R.string.toast_no_email_app, Toast.LENGTH_SHORT).show();
        return false;
    }

    private ArrayList<String> getEmailCandidatePackages() {
        android.content.pm.PackageManager packageManager = getPackageManager();
        Intent emailAppQueryIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        java.util.List<ResolveInfo> emailApps = packageManager.queryIntentActivities(emailAppQueryIntent, 0);

        ArrayList<String> preferredPackages = new ArrayList<>();
        ArrayList<String> otherPackages = new ArrayList<>();
        if (emailApps == null) return preferredPackages;

        Set<String> seenPackages = new LinkedHashSet<>();
        for (ResolveInfo resolveInfo : emailApps) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (!seenPackages.add(packageName)) continue;

            String lowerPackage = packageName.toLowerCase();
            if (lowerPackage.contains("gmail")
                    || lowerPackage.contains("outlook")
                    || lowerPackage.contains("samsung.android.email")) {
                preferredPackages.add(packageName);
            } else {
                otherPackages.add(packageName);
            }
        }
        preferredPackages.addAll(otherPackages);
        return preferredPackages;
    }

    private Intent buildSupportedEmailIntent(String recipient, String subject, String messageBody, String packageName) {
        ArrayList<Intent> candidates = new ArrayList<>();
        if (bugImages.isEmpty()) {
            candidates.add(buildSendIntent(recipient, subject, messageBody, packageName, "text/plain"));
            candidates.add(buildSendIntent(recipient, subject, messageBody, packageName, "message/rfc822"));
            candidates.add(buildSendToIntent(recipient, subject, messageBody, packageName));
        } else {
            candidates.add(buildSendIntent(recipient, subject, messageBody, packageName, "image/*"));
            candidates.add(buildSendIntent(recipient, subject, messageBody, packageName, "*/*"));
            candidates.add(buildSendIntent(recipient, subject, messageBody, packageName, "message/rfc822"));
        }

        for (Intent candidate : candidates) {
            if (isIntentSupported(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Intent buildSendIntent(String recipient, String subject, String messageBody, String packageName, String mimeType) {
        Intent intent = new Intent(bugImages.isEmpty() ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        intent.setType(mimeType);
        intent.setPackage(packageName);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, messageBody);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (!bugImages.isEmpty()) {
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(bugImages));

            ClipData clipData = new ClipData(
                    "Solvly Bug Report Images",
                    new String[]{mimeType},
                    new ClipData.Item(bugImages.get(0))
            );
            for (int i = 1; i < bugImages.size(); i++) {
                clipData.addItem(new ClipData.Item(bugImages.get(i)));
            }
            intent.setClipData(clipData);

            for (Uri uri : bugImages) {
                grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }

        return intent;
    }

    private Intent buildSendToIntent(String recipient, String subject, String messageBody, String packageName) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(recipient)));
        intent.setPackage(packageName);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, messageBody);
        return intent;
    }

    private boolean isIntentSupported(Intent intent) {
        return !getPackageManager().queryIntentActivities(intent, 0).isEmpty();
    }

    private void loadPreviewInto(ImageView imageView, Uri uri) {
        String tag = uri.toString();
        imageView.setTag(tag);
        imageView.setImageDrawable(null);

        previewExecutor.execute(() -> {
            Bitmap bitmap = decodePreviewBitmap(uri, 240);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (tag.equals(imageView.getTag()) && bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    private Bitmap decodePreviewBitmap(Uri uri, int sizePx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return getContentResolver().loadThumbnail(uri, new Size(sizePx, sizePx), null);
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateInSampleSize(bounds, sizePx, sizePx);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    private void clearBugReportState() {
        releaseAllPersistedBugImagePermissions();
        bugImages.clear();
        pendingBugReportText = "";
    }

    private void releaseAllPersistedBugImagePermissions() {
        ArrayList<String> persistedUris = new ArrayList<>(persistedBugImageUris);
        for (String uriString : persistedUris) {
            try {
                getContentResolver().releasePersistableUriPermission(Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }
        persistedBugImageUris.clear();
    }

    private void releasePersistedUriPermission(Uri uri) {
        if (uri == null) return;
        String uriString = uri.toString();
        if (!persistedBugImageUris.remove(uriString)) return;
        try {
            getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> imageUris = new ArrayList<>();
        for (Uri uri : bugImages) {
            imageUris.add(uri.toString());
        }

        outState.putStringArrayList(STATE_BUG_IMAGES, imageUris);
        outState.putStringArrayList(STATE_PERSISTED_BUG_IMAGES, new ArrayList<>(persistedBugImageUris));
        outState.putString(STATE_BUG_REPORT_TEXT,
                editTextBugReport != null ? editTextBugReport.getText().toString() : pendingBugReportText);
        outState.putBoolean(STATE_BUG_SHEET_OPEN, bugReportDialog != null && bugReportDialog.isShowing());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bugReportDialog != null && bugReportDialog.isShowing()) {
            bugReportDialog.dismiss();
        }
        if (isFinishing()) {
            clearBugReportState();
        }
        previewExecutor.shutdownNow();
    }
}

