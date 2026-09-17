package com.example.solvly.utils;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import android.util.Base64;

import com.example.solvly.BuildConfig;

public class AISolver {

    private static final String[] MODEL_FALLBACKS = {
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-1.5-flash"
    };

    private static final String[] API_VERSIONS = { "v1", "v1beta" };

    private static final String[] API_KEYS;
    static {
        String keysStr = BuildConfig.GEMINI_API_KEYS;
        if (keysStr != null && !keysStr.trim().isEmpty()) {
            String[] rawKeys = keysStr.split(",");
            java.util.List<String> validKeys = new java.util.ArrayList<>();
            for (String key : rawKeys) {
                if (key != null) {
                    String trimmed = key.trim();
                    if (!trimmed.isEmpty()) {
                        validKeys.add(trimmed);
                    }
                }
            }
            API_KEYS = validKeys.isEmpty() ? new String[0] : validKeys.toArray(new String[0]);
        } else {
            API_KEYS = new String[0];
        }
    }
    private static final AtomicInteger currentKeyIndex = new AtomicInteger(0);

    public interface AISolverCallback {
        void onSuccess(java.util.List<MathSolver.SolveResult> results);

        void onError(String error);
    }

    public interface Cancellable {
        void cancel();
        boolean isCancelled();
    }

    public static Cancellable solveAsync(String rawText, byte[] imageData, String mimeType, AISolverCallback callback) {
        return solveAsync(rawText, imageData, mimeType, imageData != null, callback);
    }

    public static Cancellable solveAsync(String rawText, byte[] imageData, String mimeType,
                                         boolean isOcr, AISolverCallback callback) {
        String normalizedText = rawText != null ? rawText.trim() : "";
        if (normalizedText.isEmpty() && imageData == null) {
            callback.onError("No input provided.");
            return new Cancellable() { public void cancel() {} public boolean isCancelled() { return true; } };
        }

        // FAST PATH: local solver only for typed text. Never skip vision when an image is present.
        if (!isOcr && imageData == null && !normalizedText.isEmpty() && normalizedText.length() < 100) {
            MathSolver.SolveResult localResult = MathSolver.solve(normalizedText, false);
            if (localResult.isSuccess) {
                java.util.List<MathSolver.SolveResult> results = new java.util.ArrayList<>();
                results.add(localResult);
                callback.onSuccess(results);
                return new Cancellable() { public void cancel() {} public boolean isCancelled() { return true; } };
            }
        }

        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicReference<HttpURLConnection> activeConn = new java.util.concurrent.atomic.AtomicReference<>(null);
        final Thread worker = new Thread(() -> {
            int keyAttempts = 0;
            boolean success = false;
            String lastError = "";

            if (API_KEYS.length == 0) {
                if (!cancelled.get()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!cancelled.get()) {
                            callback.onError("Configuration error: Gemini API key not found. " +
                                "Please set GEMINI_API_KEYS in your build configuration. " +
                                "Offline mode is available for simple typed problems only.");
                        }
                    });
                }
                return;
            }

            keyLoop:
            while (keyAttempts < API_KEYS.length && !success && !cancelled.get()) {
                int safeIdx = Math.floorMod(currentKeyIndex.get() + keyAttempts, API_KEYS.length);
                String apiKey = API_KEYS[safeIdx];

                modelLoop:
                for (int mIdx = 0; mIdx < MODEL_FALLBACKS.length && !cancelled.get(); mIdx++) {
                    String modelName = MODEL_FALLBACKS[mIdx];

                    apiVerLoop:
                    for (int vIdx = 0; vIdx < API_VERSIONS.length && !cancelled.get(); vIdx++) {
                        if (cancelled.get()) break keyLoop;
                        String apiVersion = API_VERSIONS[vIdx];
                        HttpURLConnection conn = null;
                        try {
                            String endpoint = "https://generativelanguage.googleapis.com/"
                                    + apiVersion + "/models/" + modelName + ":generateContent";

                            URL url = new URL(endpoint);
                            conn = (HttpURLConnection) url.openConnection();
                            activeConn.set(conn);
                            if (cancelled.get()) break keyLoop;

                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setRequestProperty("x-goog-api-key", apiKey);
                            conn.setConnectTimeout(10000);
                            conn.setReadTimeout(30000);
                            conn.setDoOutput(true);

                            String prompt = "You are an expert math tutor with strong vision and word-problem reasoning capabilities. I am providing you with " +
                                    (imageData != null ? "AN IMAGE (PRIMARY SOURCE) and optional low-quality OCR text." : "some mathematical text.") +
                                    "\n\n⚠️ CRITICAL INSTRUCTION IF IMAGE IS PROVIDED:\n" +
                                    "- IGNORE the 'OCR HINT' below completely if it seems wrong. READ THE FULL PROBLEM DIRECTLY FROM THE IMAGE using your vision.\n" +
                                    "- The OCR text is a HINT ONLY and is often inaccurate for math symbols, fractions, integrals, exponents (x^2), subscripts (x_1), Greek letters (π, θ, Σ, ∫), square roots (√), dollar signs ($), percentage signs (%), etc.\n" +
                                    "- Read every symbol from the image carefully including: operators (+,−,×,÷,=,≠,<,>,≤,≥), fractions (a/b), powers (x^n), roots, integrals (∫), sums (Σ), Greek letters, currency ($, ₹, £, €), percentage (%), parenthesis/brackets placement, and MULTIPLE CHOICE OPTIONS (A. B. C. D. E.).\n\n" +
                                    "📝 PROBLEM SOLVING RULES:\n" +
                                    "1. Solve ONLY ONE mathematical problem. Even if there are multiple problems, choose the main/biggest/complete one and solve it.\n" +
                                    "2. If there are MULTIPLE CHOICE OPTIONS (A/B/C/D/E) available, compute the exact answer and MATCH it to the correct option letter. Include both the letter and the value in final_answer.\n" +
                                    "3. For WORD PROBLEMS: Define variables, write the equation(s), show each step CLEARLY, and box/state the final numeric answer. Double-check arithmetic (percentages, discounts, ratios, unit conversions).\n" +
                                    "4. DO NOT introduce extra variables or assumptions not present in the problem.\n\n" +
                                    "CRITICAL OUTPUT FORMAT: Write all math in simple, plain text. Do NOT use LaTeX. Use standard characters (e.g., x^2 for x squared, 3/4 for fraction, sqrt(x) for square root, pi for π, theta for θ, integral for ∫, sum for Σ, $ for dollar, % for percent).\n\n" +
                                    "Return your response ONLY as a valid JSON ARRAY with ONE object (NO markdown, NO extra text, NO code blocks):\n" +
                                    "[{\n" +
                                    "  \"problem_title\": \"Short descriptive heading (e.g. 'Successive Discounts Word Problem')\",\n" +
                                    "  \"cleaned_problem\": \"EXACT problem statement as you read it from image/text (include all options if MCQ)\",\n" +
                                    "  \"final_answer\": \"Final answer — if MCQ include letter, e.g. 'C. $400'\",\n" +
                                    "  \"steps\": [\"Step 1 explanation with reasoning and numbers\", \"Step 2 explanation\", \"...\"]\n" +
                                    "}]\n\n" +
                                    "Just raw JSON ARRAY. Nothing before or after.\n\n" +
                                    (!normalizedText.isEmpty() ? "LOW-QUALITY OCR HINT (do not trust blindly — prefer image): " + normalizedText : "");

                            JSONArray partsArray = new JSONArray();

                            JSONObject textPart = new JSONObject();
                            textPart.put("text", prompt);
                            partsArray.put(textPart);

                            if (imageData != null) {
                                JSONObject imagePart = new JSONObject();
                                JSONObject inlineData = new JSONObject();
                                inlineData.put("mime_type", mimeType != null ? mimeType : "image/jpeg");
                                inlineData.put("data", Base64.encodeToString(imageData, Base64.NO_WRAP));
                                imagePart.put("inline_data", inlineData);
                                partsArray.put(imagePart);
                            }

                            JSONObject content = new JSONObject();
                            content.put("parts", partsArray);

                            JSONArray contents = new JSONArray();
                            contents.put(content);

                            JSONObject requestBody = new JSONObject();
                            requestBody.put("contents", contents);

                            try (OutputStream os = conn.getOutputStream()) {
                                byte[] input = requestBody.toString().getBytes("utf-8");
                                os.write(input, 0, input.length);
                            }
                            if (cancelled.get()) break keyLoop;

                            int responseCode = conn.getResponseCode();
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                StringBuilder responseStr = new StringBuilder();
                                String inputLine;
                                while (!cancelled.get() && (inputLine = in.readLine()) != null) {
                                    responseStr.append(inputLine);
                                }
                                in.close();
                                if (cancelled.get()) break keyLoop;

                                JSONObject jsonResponse = new JSONObject(responseStr.toString());
                                String responseText = extractResponseText(jsonResponse);

                                responseText = responseText.replace("```json", "").replace("```", "").trim();

                                java.util.List<MathSolver.SolveResult> results = new java.util.ArrayList<>();

                                if (responseText.startsWith("[")) {
                                    JSONArray resultsArray = new JSONArray(responseText);
                                    for (int j = 0; j < resultsArray.length(); j++) {
                                        JSONObject finalJson = resultsArray.getJSONObject(j);
                                        results.add(parseResultFromJson(finalJson));
                                    }
                                } else if (responseText.startsWith("{")) {
                                    JSONObject finalJson = new JSONObject(responseText);
                                    results.add(parseResultFromJson(finalJson));
                                }

                                if (results.isEmpty()) {
                                    throw new IllegalStateException("AI returned no structured results.");
                                }

                                if (!cancelled.get()) {
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        if (!cancelled.get()) callback.onSuccess(results);
                                    });
                                    success = true;
                                    break keyLoop;
                                }

                            } else {
                                StringBuilder errorResponse = new StringBuilder();
                                try (BufferedReader errorReader = new BufferedReader(
                                        new InputStreamReader(conn.getErrorStream()))) {
                                    String line;
                                    while (!cancelled.get() && (line = errorReader.readLine()) != null) {
                                        errorResponse.append(line);
                                    }
                                } catch (Exception ignored) {
                                }

                                lastError = "[" + modelName + "/" + apiVersion + "] API Error ("
                                        + responseCode + "): " + errorResponse.toString();

                                if (responseCode == 429 || responseCode == 403 || responseCode == 401) {
                                    break modelLoop;
                                } else {
                                    continue apiVerLoop;
                                }
                            }

                        } catch (Exception e) {
                            if (cancelled.get()) break keyLoop;
                            lastError = "[" + (MODEL_FALLBACKS[mIdx]) + "/" + (API_VERSIONS[vIdx]) + "] Error: " + e.getMessage();
                            continue apiVerLoop;
                        } finally {
                            activeConn.compareAndSet(conn, null);
                            if (conn != null) conn.disconnect();
                        }
                    }
                }

                synchronized (AISolver.class) {
                    currentKeyIndex.set(Math.floorMod(currentKeyIndex.get() + 1, API_KEYS.length));
                }
                keyAttempts++;
            }

            if (cancelled.get()) {
                return;
            } else if (!success) {
                handleOfflineFallback(normalizedText, isOcr, callback, lastError);
            }
        });
        worker.start();

        return new Cancellable() {
            @Override public void cancel() {
                cancelled.set(true);
                HttpURLConnection conn = activeConn.getAndSet(null);
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
                worker.interrupt();
            }
            @Override public boolean isCancelled() { return cancelled.get(); }
        };
    }

    // Helper for backward compatibility or text-only solve
    public static Cancellable solveAsync(String rawText, AISolverCallback callback) {
        return solveAsync(rawText, null, null, callback);
    }

    private static String extractResponseText(JSONObject jsonResponse) throws org.json.JSONException {
        JSONArray candidates = jsonResponse.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new IllegalStateException("AI returned no candidates.");
        }

        JSONObject firstCandidate = candidates.getJSONObject(0);
        JSONObject contentObj = firstCandidate.optJSONObject("content");
        if (contentObj == null) {
            throw new IllegalStateException("AI response content is missing.");
        }

        JSONArray responseParts = contentObj.optJSONArray("parts");
        if (responseParts == null || responseParts.length() == 0) {
            throw new IllegalStateException("AI response parts are missing.");
        }

        String responseText = responseParts.getJSONObject(0).optString("text", "").trim();
        if (responseText.isEmpty()) {
            throw new IllegalStateException("AI response text is empty.");
        }
        return responseText;
    }

    private static MathSolver.SolveResult parseResultFromJson(JSONObject finalJson) throws org.json.JSONException {
        MathSolver.SolveResult solveResult = new MathSolver.SolveResult();
        solveResult.title = finalJson.optString("problem_title", "");
        solveResult.scannedText = finalJson.optString("cleaned_problem", "");
        solveResult.answer = finalJson.optString("final_answer", "");
        solveResult.isSuccess = true;

        JSONArray stepsArray = finalJson.optJSONArray("steps");
        solveResult.steps = new java.util.ArrayList<>();
        if (stepsArray != null) {
            for (int i = 0; i < stepsArray.length(); i++) {
                solveResult.steps.add(stepsArray.getString(i));
            }
        }
        return solveResult;
    }

    private static void handleOfflineFallback(String rawText, boolean isOcr,
                                              AISolverCallback callback, String originalError) {
        try {
            // Fallback to the local, offline MathSolver engine
            MathSolver.SolveResult localResult = MathSolver.solve(rawText, isOcr);

            if (localResult.isSuccess) {
                // It was solved locally without internet!
                localResult.steps.add(0, "[Offline Mode] Solved locally.");
                java.util.List<MathSolver.SolveResult> results = new java.util.ArrayList<>();
                results.add(localResult);
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(results));
            } else {
                // Even the local solver failed
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(
                        "Offline Mode: " + originalError
                                + "\nPlease connect to the internet to solve complex problems."));
            }
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onError(
                    "Offline Mode: Could not solve this problem. Please check your internet connection."));
        }
    }
}
