package com.example.solvly.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.solvly.R;
import com.example.solvly.utils.MathSolver;
import com.example.solvly.utils.CustomMathView;
import com.example.solvly.utils.AISolver;
import com.example.solvly.utils.HistoryDbHelper;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ResultActivity extends AppCompatActivity {

    private static final String STATE_RESULTS_DISPLAYED = "state_results_displayed";
    private static final String STATE_PROBLEMS = "state_problems";
    private static final String STATE_ANSWERS = "state_answers";
    private static final String STATE_STEPS = "state_steps";
    private static final String STATE_IMAGE_URIS = "state_image_uris";
    private static final int AI_IMAGE_MAX_SIDE = 2400;

    private LinearLayout layoutResults;
    private LinearLayout layoutLoading;
    private androidx.core.widget.NestedScrollView scrollContent;
    private TextView textViewLoadingMessage;
    private TextRecognizer recognizer;
    private int tasksProcessed = 0;
    private int totalTasks = 0;
    private String sharedRawText; // Manual text input from user
    private final List<Uri> pendingImageUris = new ArrayList<>();
    private boolean pendingTypedProblem = false;
    private volatile AISolver.Cancellable activeSolve;
    private boolean shouldAutoSave = true;
    private boolean resultsDisplayed;
    private final ArrayList<String> displayedProblems = new ArrayList<>();
    private final ArrayList<String> displayedAnswers = new ArrayList<>();
    private final ArrayList<String> displayedStepsJson = new ArrayList<>();
    private final ArrayList<String> displayedImageUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        layoutLoading = findViewById(R.id.layoutLoading);
        scrollContent = findViewById(R.id.scrollContent);
        textViewLoadingMessage = findViewById(R.id.textViewLoadingMessage);
        layoutResults = findViewById(R.id.layoutResults); 
        android.widget.ImageButton buttonBack = findViewById(R.id.buttonBack);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        ArrayList<String> imageUriStrings = getIntent().getStringArrayListExtra("image_uris");
        Uri dataImageUri = getIntent().getData();
        sharedRawText = getIntent().getStringExtra("raw_text");
        String savedAnswer = getIntent().getStringExtra("saved_answer");
        ArrayList<String> savedSteps = getIntent().getStringArrayListExtra("saved_steps");

        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_RESULTS_DISPLAYED, false)) {
            shouldAutoSave = false;
            restoreDisplayedResults(savedInstanceState);
        } else if (savedAnswer != null && savedSteps != null) {
            shouldAutoSave = false;
            layoutLoading.setVisibility(View.GONE);
            scrollContent.setVisibility(View.VISIBLE);
            
            layoutResults.removeAllViews();
            addResultCard(null, sharedRawText != null ? sharedRawText : "Problem", savedAnswer, savedSteps);
            resultsDisplayed = true;
        } else {
            if (savedInstanceState != null) {
                shouldAutoSave = false;
            }
            layoutLoading.setVisibility(View.VISIBLE);
            scrollContent.setVisibility(View.GONE);

            boolean hasTypedText = sharedRawText != null && !sharedRawText.trim().isEmpty();
            if (imageUriStrings != null && !imageUriStrings.isEmpty()) {
                for (String uriStr : imageUriStrings) {
                    pendingImageUris.add(Uri.parse(uriStr));
                }
            } else if (dataImageUri != null) {
                pendingImageUris.add(dataImageUri);
            }

            // If there's an image, treat image + text as a single task
            if (!pendingImageUris.isEmpty()) {
                totalTasks = 1;
                pendingTypedProblem = false;
            } else {
                totalTasks = hasTypedText ? 1 : 0;
                pendingTypedProblem = hasTypedText;
            }
            
            tasksProcessed = 0;
            layoutResults.removeAllViews();

            if (totalTasks > 0) {
                if (!pendingImageUris.isEmpty()) {
                    textViewLoadingMessage.setText("Reading problem from image...");
                } else {
                    textViewLoadingMessage.setText("Solving problem...");
                }
                processNextTask();
            } else {
                layoutLoading.setVisibility(View.GONE);
                scrollContent.setVisibility(View.VISIBLE);
                showErrorMessage("Nothing to solve. Please type or scan a problem.");
            }
        }

        buttonBack.setOnClickListener(v -> finish());
    }

    private void restoreDisplayedResults(Bundle savedInstanceState) {
        layoutLoading.setVisibility(View.GONE);
        scrollContent.setVisibility(View.VISIBLE);
        layoutResults.removeAllViews();
        resultsDisplayed = true;

        ArrayList<String> problems = savedInstanceState.getStringArrayList(STATE_PROBLEMS);
        ArrayList<String> answers = savedInstanceState.getStringArrayList(STATE_ANSWERS);
        ArrayList<String> stepsJson = savedInstanceState.getStringArrayList(STATE_STEPS);
        ArrayList<String> imageUris = savedInstanceState.getStringArrayList(STATE_IMAGE_URIS);
        if (problems == null || answers == null) return;

        int count = problems.size();
        for (int i = 0; i < count; i++) {
            Uri imageUri = null;
            if (imageUris != null && i < imageUris.size() && !TextUtils.isEmpty(imageUris.get(i))) {
                imageUri = Uri.parse(imageUris.get(i));
            }
            ArrayList<String> steps = new ArrayList<>();
            if (stepsJson != null && i < stepsJson.size()) {
                try {
                    org.json.JSONArray array = new org.json.JSONArray(stepsJson.get(i));
                    for (int s = 0; s < array.length(); s++) {
                        steps.add(array.getString(s));
                    }
                } catch (Exception ignored) {
                }
            }
            String answer = i < answers.size() ? answers.get(i) : "";
            addResultCard(imageUri, problems.get(i), answer, steps);
        }
    }

    private void processNextTask() {
        if (!pendingImageUris.isEmpty()) {
            Uri nextImageUri = pendingImageUris.remove(0);
            processSingleImage(nextImageUri, sharedRawText != null ? sharedRawText.trim() : "");
        } else if (pendingTypedProblem) {
            pendingTypedProblem = false;
            solveAndDisplay(sharedRawText.trim(), null);
        }
    }

    private void processSingleImage(Uri imageUri, String additionalText) {
        try {
            final String finalAdditional = additionalText != null ? additionalText.trim() : "";
            InputImage image = InputImage.fromFilePath(this, imageUri);
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String ocrText = visionText.getText();
                        if (ocrText == null) ocrText = "";
                        String combinedText = finalAdditional;
                        if (!ocrText.isEmpty()) {
                            if (!combinedText.isEmpty()) {
                                combinedText = combinedText + "\n" + ocrText;
                            } else {
                                combinedText = ocrText;
                            }
                        }
                        solveAndDisplay(combinedText, imageUri);
                    })
                    .addOnFailureListener(e -> {
                        solveAndDisplay(finalAdditional, imageUri);
                    });
        } catch (Exception e) {
            solveAndDisplay(additionalText, imageUri);
        }
    }

    private void solveAndDisplay(String ocrText, Uri imageUri) {
        final String finalProblemText = ocrText != null ? ocrText.trim() : "";
        final WeakReference<ResultActivity> weakActivity = new WeakReference<>(this);

        new Thread(() -> {
            final ResultActivity activity = weakActivity.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

            byte[] imageData = null;
            if (imageUri != null) {
                imageData = activity.encodeImageForAi(imageUri);
            }

            final ResultActivity act2 = weakActivity.get();
            if (act2 == null || act2.isFinishing() || act2.isDestroyed()) return;

            act2.activeSolve = AISolver.solveAsync(finalProblemText, imageData, "image/jpeg",
                    imageUri != null, new AISolver.AISolverCallback() {
                @Override
                public void onSuccess(List<MathSolver.SolveResult> results) {
                    ResultActivity act = weakActivity.get();
                    if (act == null || act.isFinishing() || act.isDestroyed()) return;
                    act.runOnUiThread(() -> {
                        if (act.isFinishing() || act.isDestroyed()) return;
                        android.content.SharedPreferences prefs = act.getSharedPreferences("solvly_prefs", MODE_PRIVATE);
                        boolean autoSave = prefs.getBoolean("auto_save_history", true);
                        int autoDeleteDays = prefs.getInt("auto_delete_days", 30);
                        
                        if (autoSave && autoDeleteDays > 0) {
                            HistoryDbHelper.getInstance(act).deleteOldHistory(autoDeleteDays);
                        }

                        if (!results.isEmpty()) {
                            MathSolver.SolveResult result = results.get(0);
                            act.addResultCard(imageUri, result.scannedText, result.answer, result.steps);
                            if (autoSave && act.shouldAutoSave) {
                                String title = (result.title != null && !result.title.isEmpty())
                                        ? result.title
                                        : HistoryDbHelper.generateFallbackTitle(result.scannedText);
                                HistoryDbHelper.getInstance(act)
                                        .addHistory(title, result.scannedText, result.answer, result.steps);
                            }
                        }
                        act.checkAllProcessed();
                    });
                }

                @Override
                public void onError(String error) {
                    ResultActivity act = weakActivity.get();
                    if (act == null || act.isFinishing() || act.isDestroyed()) return;
                    act.runOnUiThread(() -> {
                        if (act.isFinishing() || act.isDestroyed()) return;
                        act.addResultCard(imageUri, finalProblemText, "Error: " + error, new ArrayList<>());
                        act.checkAllProcessed();
                    });
                }
            });
        }).start();
    }

    private void checkAllProcessed() {
        tasksProcessed++;
        if (tasksProcessed >= totalTasks) {
            resultsDisplayed = true;
            layoutLoading.setVisibility(View.GONE);
            scrollContent.setVisibility(View.VISIBLE);
        } else {
            processNextTask();
        }
    }

    private void addResultCard(Uri imageUri, String scannedText, String answer, List<String> steps) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View card = inflater.inflate(R.layout.item_result_card, layoutResults, false);
        TextView tvScanned = card.findViewById(R.id.cardScannedText);
        CustomMathView mvAnswer = card.findViewById(R.id.cardMathAnswer);
        ImageView ivProblem = card.findViewById(R.id.ivProblemImage);
        View cardImageProblem = card.findViewById(R.id.cardImageProblem);
        ImageButton btnCopy = card.findViewById(R.id.buttonCopy);
        ImageButton btnShare = card.findViewById(R.id.buttonShare);

        mvAnswer.setTextColor("white");
        LinearLayout llSteps = card.findViewById(R.id.cardLayoutSteps);

        if (imageUri != null) {
            Bitmap previewBitmap = decodeSampledBitmap(imageUri, 1024);
            if (previewBitmap != null) {
                ivProblem.setImageBitmap(previewBitmap);
            }
            cardImageProblem.setVisibility(View.VISIBLE);
        } else {
            cardImageProblem.setVisibility(View.GONE);
        }

        tvScanned.setText(scannedText);
        mvAnswer.setText(answer);
        
        llSteps.removeAllViews();
        if (steps != null && !steps.isEmpty()) {
            LayoutInflater stepInflater = LayoutInflater.from(this);
            int colorInt = getResources().getColor(R.color.on_surface, getTheme());
            String colorHex = String.format("#%06X", (0xFFFFFF & colorInt));
            for (String step : steps) {
                View stepView = stepInflater.inflate(R.layout.item_step_card, llSteps, false);
                CustomMathView mvStep = stepView.findViewById(R.id.mathViewStep);
                mvStep.setTextColor(colorHex);
                mvStep.setText(step);
                llSteps.addView(stepView);
            }
        } else {
            View stepsTitle = card.findViewById(R.id.cardStepsTitle);
            if (stepsTitle != null) {
                stepsTitle.setVisibility(View.GONE);
            }
        }

        String fullText = buildExportText(scannedText, answer, steps);

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Solvly Solution", fullText));
            Toast.makeText(ResultActivity.this, R.string.toast_solution_copied, Toast.LENGTH_SHORT).show();
        });

        btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, fullText);
            startActivity(Intent.createChooser(shareIntent, "Share with..."));
        });

        layoutResults.addView(card);
        recordDisplayedResult(imageUri, scannedText, answer, steps);
    }

    private void recordDisplayedResult(Uri imageUri, String scannedText, String answer, List<String> steps) {
        displayedProblems.add(scannedText != null ? scannedText : "");
        displayedAnswers.add(answer != null ? answer : "");
        displayedImageUris.add(imageUri != null ? imageUri.toString() : "");
        org.json.JSONArray array = new org.json.JSONArray();
        if (steps != null) {
            for (String step : steps) array.put(step);
        }
        displayedStepsJson.add(array.toString());
    }

    private String buildExportText(String problem, String answer, List<String> steps) {
        StringBuilder exportText = new StringBuilder();
        exportText.append("Problem: ").append(problem).append("\n\n")
                .append("Answer: ").append(answer);
        if (steps != null && !steps.isEmpty()) {
            exportText.append("\n\nSolution Steps:\n");
            for (int i = 0; i < steps.size(); i++) {
                exportText.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
        }
        return exportText.toString().trim();
    }

    private void showErrorMessage(String message) {
        layoutResults.removeAllViews();
        TextView errorView = new TextView(this);
        errorView.setText(message);
        errorView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.error));
        errorView.setTextSize(16f);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        errorView.setPadding(padding, padding, padding, padding);
        layoutResults.addView(errorView);
    }

    private byte[] encodeImageForAi(Uri imageUri) {
        Bitmap bitmap = decodeSampledBitmap(imageUri, AI_IMAGE_MAX_SIDE);
        if (bitmap == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    private Bitmap decodeSampledBitmap(Uri uri, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateInSampleSize(bounds, maxSide, maxSide);
            Bitmap decoded;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                decoded = BitmapFactory.decodeStream(in, null, options);
            }
            if (decoded == null) return null;

            int width = decoded.getWidth();
            int height = decoded.getHeight();
            if (width <= maxSide && height <= maxSide) {
                return decoded;
            }

            float scale = Math.min(maxSide / (float) width, maxSide / (float) height);
            Bitmap scaled = Bitmap.createScaledBitmap(
                    decoded,
                    Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)),
                    true);
            if (scaled != decoded) {
                decoded.recycle();
            }
            return scaled;
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height <= 0 || width <= 0) return 1;
        while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESULTS_DISPLAYED, resultsDisplayed);
        outState.putStringArrayList(STATE_PROBLEMS, new ArrayList<>(displayedProblems));
        outState.putStringArrayList(STATE_ANSWERS, new ArrayList<>(displayedAnswers));
        outState.putStringArrayList(STATE_STEPS, new ArrayList<>(displayedStepsJson));
        outState.putStringArrayList(STATE_IMAGE_URIS, new ArrayList<>(displayedImageUris));
    }

    @Override
    protected void onDestroy() {
        if (activeSolve != null) {
            activeSolve.cancel();
            activeSolve = null;
        }
        super.onDestroy();
        if (recognizer != null) recognizer.close();
    }
}
