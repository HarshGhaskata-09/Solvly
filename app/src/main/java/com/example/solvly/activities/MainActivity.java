package com.example.solvly.activities;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.example.solvly.R;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Uri cameraImageUri;
    private final List<Uri> selectedImages = new ArrayList<>();
    private static final int MAX_IMAGES = 1;
    private static final String STATE_SELECTED_IMAGES = "state_selected_images";
    private static final String STATE_CAMERA_IMAGE_URI = "state_camera_image_uri";

    private View scrollImagePreview;
    private LinearLayout containerImagePreview;
    private EditText editTextMessage;
    private ImageButton buttonAttachment, buttonGallery, buttonCamera;
    private com.google.android.material.button.MaterialButton buttonSend;
    private boolean isActionVisible = true;

    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String> galleryPermissionLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<CropImageContractOptions> cropImageLauncher;
    private final ExecutorService previewExecutor = Executors.newFixedThreadPool(2);

    private long backPressedTime;
    private Toast backToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Safety guard: if policy was never accepted, go back to IntroActivity
        SharedPreferences prefs = getSharedPreferences(
                IntroActivity.PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(IntroActivity.KEY_POLICY_ACCEPTED, false)) {
            Intent intent = new Intent(this, IntroActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        initViews();
        setupLaunchers();
        setupClickListeners();
        setupDoubleBackExit();
        setupTextWatcher();
        restoreInputState(savedInstanceState);
    }

    private void restoreInputState(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        String cameraUri = savedInstanceState.getString(STATE_CAMERA_IMAGE_URI);
        if (cameraUri != null) {
            cameraImageUri = Uri.parse(cameraUri);
        }
        ArrayList<String> imageUris = savedInstanceState.getStringArrayList(STATE_SELECTED_IMAGES);
        if (imageUris != null && !imageUris.isEmpty()) {
            selectedImages.clear();
            for (String uriString : imageUris) {
                selectedImages.add(Uri.parse(uriString));
            }
            renderPreviews();
        }
    }

    private void initViews() {
        scrollImagePreview = findViewById(R.id.scrollImagePreview);
        containerImagePreview = findViewById(R.id.containerImagePreview);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonAttachment = findViewById(R.id.buttonAttachment);
        buttonGallery = findViewById(R.id.buttonGallery);
        buttonCamera = findViewById(R.id.buttonCamera);
        buttonSend = findViewById(R.id.buttonSend);
        updateSendButtonState();
    }

    private void setupLaunchers() {
        cropImageLauncher = registerForActivityResult(new CropImageContract(), result -> {
            if (result.isSuccessful()) {
                Uri croppedUri = result.getUriContent();
                if (croppedUri != null) addImage(croppedUri);
            } else {
                Exception error = result.getError();
                if (error != null) Toast.makeText(this, getString(R.string.toast_crop_failed, error.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) handleSelectedImage(uri);
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    try {
                        if (success && cameraImageUri != null) {
                            handleSelectedImage(cameraImageUri);
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Camera Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) openCamera();
                    else Toast.makeText(this, R.string.toast_camera_permission_denied, Toast.LENGTH_SHORT).show();
                }
        );

        galleryPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) pickImageLauncher.launch("image/*");
                    else Toast.makeText(this, R.string.toast_gallery_permission_denied, Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void startCrop(Uri uri) {

        CropImageOptions cropImageOptions = new CropImageOptions();
        cropImageOptions.guidelines = CropImageView.Guidelines.ON;
        cropImageOptions.activityTitle = "Crop Problem";
        cropImageOptions.cropShape = CropImageView.CropShape.RECTANGLE;
        cropImageOptions.fixAspectRatio = false;
        cropImageOptions.initialCropWindowPaddingRatio = 0.15f;
        
        // Theme-aware colors (auto-selects values/values-night)
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (isDark) {
            cropImageOptions.toolbarColor = ContextCompat.getColor(this, R.color.surface);
            cropImageOptions.activityBackgroundColor = ContextCompat.getColor(this, R.color.background);
            cropImageOptions.toolbarTitleColor = ContextCompat.getColor(this, R.color.on_surface);
            cropImageOptions.activityMenuIconColor = ContextCompat.getColor(this, R.color.on_surface);
        } else {
            cropImageOptions.toolbarColor = ContextCompat.getColor(this, R.color.primary);
            cropImageOptions.activityBackgroundColor = ContextCompat.getColor(this, R.color.surface);
            cropImageOptions.toolbarTitleColor = ContextCompat.getColor(this, R.color.white);
            cropImageOptions.activityMenuIconColor = ContextCompat.getColor(this, R.color.white);
        }
        
        CropImageContractOptions options = new CropImageContractOptions(uri, cropImageOptions);
        cropImageLauncher.launch(options);
    }

    private void handleSelectedImage(Uri sourceUri) {
        startCrop(sourceUri);
    }

    private Uri copyToCache(Uri sourceUri) {
        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "images");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) return null;
            java.io.File destFile = java.io.File.createTempFile("GALLERY_", ".jpg", cacheDir);

            try (java.io.InputStream in = getContentResolver().openInputStream(sourceUri);
                 java.io.OutputStream out = new java.io.FileOutputStream(destFile)) {
                if (in == null) return null;
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }

            if (destFile.length() <= 0) return null;

            return androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", destFile);
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            return null;
        }
    }

    private void setupClickListeners() {
        buttonAttachment.setOnClickListener(v -> toggleActionButtons());
        buttonCamera.setOnClickListener(v -> checkCameraPermission());
        buttonGallery.setOnClickListener(v -> checkGalleryPermission());
        findViewById(R.id.buttonSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        buttonSend.setOnClickListener(v -> {
            String message = editTextMessage.getText().toString().trim();
            if (!message.isEmpty() || !selectedImages.isEmpty()) {
                navigateToResultCombined(message, new ArrayList<>(selectedImages));
                clearInput();
            } else {
                Toast.makeText(this, R.string.toast_enter_problem, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupTextWatcher() {
        editTextMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean isTyping = !s.toString().trim().isEmpty();
                updateActionVisibility(!isTyping);
                updateSendButtonState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void updateActionVisibility(boolean visible) {
        if (isActionVisible == visible) return;
        isActionVisible = visible;

        float alpha = visible ? 1f : 0f;
        float scale = visible ? 1f : 0f;

        buttonGallery.animate().alpha(alpha).scaleX(scale).scaleY(scale).setDuration(200).withEndAction(() -> {
            if (!visible) buttonGallery.setVisibility(View.GONE);
        }).start();

        buttonCamera.animate().alpha(alpha).scaleX(scale).scaleY(scale).setDuration(200).withEndAction(() -> {
            if (!visible) buttonCamera.setVisibility(View.GONE);
        }).start();

        if (visible) {
            buttonGallery.setVisibility(View.VISIBLE);
            buttonCamera.setVisibility(View.VISIBLE);
            buttonAttachment.setVisibility(View.GONE);
        } else {
            buttonAttachment.setVisibility(View.VISIBLE);
            buttonAttachment.setAlpha(0f);
            buttonAttachment.setScaleX(0f);
            buttonAttachment.setScaleY(0f);
            buttonAttachment.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
        }
    }

    private void toggleActionButtons() {
        // Single source of truth: delegate to the same state-machine method that TextWatcher uses.
        // Avoids race where Gallery/Camera/Attachment button state mismatch when animation in-progress.
        updateActionVisibility(!isActionVisible);
    }

    private void addImage(Uri uri) {
        if (selectedImages.size() >= MAX_IMAGES) {
            Toast.makeText(this, R.string.toast_max_1_image, Toast.LENGTH_SHORT).show();
            return;
        }
        selectedImages.add(uri);
        renderPreviews();
    }

    private void renderPreviews() {
        containerImagePreview.removeAllViews();
        if (selectedImages.isEmpty()) {
            scrollImagePreview.setVisibility(View.GONE);
            updateSendButtonState();
            return;
        }

        scrollImagePreview.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < selectedImages.size(); i++) {
            Uri uri = selectedImages.get(i);
            View view = inflater.inflate(R.layout.item_image_preview, containerImagePreview, false);
            ShapeableImageView img = view.findViewById(R.id.imageViewAttachment);
            ImageButton btnRemove = view.findViewById(R.id.buttonRemoveImage);

            loadPreviewInto(img, uri);
            final int index = i;
            btnRemove.setOnClickListener(v -> {
                selectedImages.remove(index);
                renderPreviews();
            });

            containerImagePreview.addView(view);
        }
        updateSendButtonState();
    }

    private void loadPreviewInto(android.widget.ImageView imageView, Uri uri) {
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

    private void updateSendButtonState() {
        if (buttonSend == null) return;
        boolean hasInput = !editTextMessage.getText().toString().trim().isEmpty() || !selectedImages.isEmpty();
        if (hasInput) {
            buttonSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary)));
            buttonSend.setIconTint(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)));
        } else {
            buttonSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.send_button_disabled)));
            buttonSend.setIconTint(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary)));
        }
    }

    private void clearInput() {
        editTextMessage.setText("");
        selectedImages.clear();
        renderPreviews();
    }

    private void navigateToResultCombined(String text, ArrayList<Uri> uris) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!text.isEmpty()) intent.putExtra("raw_text", text);
        if (!uris.isEmpty()) {
            intent.setData(uris.get(0));
            intent.setClipData(ClipData.newUri(getContentResolver(), "solvly_problem_image", uris.get(0)));
            ArrayList<String> uriStrings = new ArrayList<>();
            for (Uri u : uris) uriStrings.add(u.toString());
            intent.putStringArrayListExtra("image_uris", uriStrings);
        }
        startActivity(intent);
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void checkGalleryPermission() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImageLauncher.launch("image/*");
        } else {
            galleryPermissionLauncher.launch(permission);
        }
    }

    private void openCamera() {
        try {
            CropImageOptions cropImageOptions = new CropImageOptions();
            cropImageOptions.imageSourceIncludeGallery = false;
            cropImageOptions.imageSourceIncludeCamera = true;
            cropImageOptions.guidelines = CropImageView.Guidelines.ON;
            cropImageOptions.activityTitle = "Crop Problem";
            cropImageOptions.cropShape = CropImageView.CropShape.RECTANGLE;
            cropImageOptions.fixAspectRatio = false;
            cropImageOptions.initialCropWindowPaddingRatio = 0.15f;

            CropImageContractOptions options = new CropImageContractOptions(null, cropImageOptions);
            cropImageLauncher.launch(options);
        } catch (Exception e) {
            Toast.makeText(this, "Launch Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupDoubleBackExit() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    if (backToast != null) backToast.cancel();
                    finish();
                } else {
                    backToast = Toast.makeText(MainActivity.this, R.string.toast_press_back_exit, Toast.LENGTH_SHORT);
                    backToast.show();
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backToast != null) {
            backToast.cancel();
            backToast = null;
        }
        previewExecutor.shutdownNow();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> imageUris = new ArrayList<>();
        for (Uri uri : selectedImages) {
            imageUris.add(uri.toString());
        }
        outState.putStringArrayList(STATE_SELECTED_IMAGES, imageUris);
        if (cameraImageUri != null) {
            outState.putString(STATE_CAMERA_IMAGE_URI, cameraImageUri.toString());
        }
    }
}
