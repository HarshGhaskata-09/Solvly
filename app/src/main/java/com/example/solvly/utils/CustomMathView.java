package com.example.solvly.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class CustomMathView extends WebView {

    private String mathText = "";
    private String textColor = "black"; // Default to dark for steps

    public CustomMathView(Context context) {
        super(context);
        init();
    }

    public CustomMathView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        getSettings().setJavaScriptEnabled(false);
        getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        setBackgroundColor(0); // Transparent
    }

    public void setTextColor(String color) {
        this.textColor = color;
    }

    public void setText(String text) {
        if (text != null) {
            // The user HATES the $$ signs. We will forcefully strip them out.
            // Remove all LaTeX delimiters completely.
            this.mathText = text.replace("$$", "")
                                .replace("$", "")
                                .replace("\\[", "")
                                .replace("\\]", "")
                                .replace("\\(", "")
                                .replace("\\)", "")
                                .trim();
        } else {
            this.mathText = "";
        }
        loadData();
    }

    private void loadData() {
        // Render as escaped plain text so user/AI content cannot inject HTML into the WebView.
        String safeText = TextUtils.htmlEncode(mathText).replace("\n", "<br>");
        String html = "<html><head>" +
                "<style>" +
                "body { font-family: sans-serif; font-size: 1.15em; color: " + textColor + "; background-color: transparent; padding: 0; margin: 0; line-height: 1.4; }" +
                "</style>" +
                "</head><body>" +
                safeText +
                "</body></html>";
        
        loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null);
    }

    @Override
    protected void onDetachedFromWindow() {
        // Defensive cleanup to prevent WebView / Activity memory leaks.
        try {
            stopLoading();
            onPause();
            clearHistory();
            removeAllViews();
            destroyDrawingCache();
        } catch (Exception ignored) {
        }
        super.onDetachedFromWindow();
        try {
            // WebView.destroy() must be called AFTER super.onDetachedFromWindow()
            destroy();
        } catch (Exception ignored) {
        }
    }
}
