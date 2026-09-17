package com.example.solvly.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.solvly.R;

public class IntroActivity extends AppCompatActivity {

    public static final String PREFS_NAME     = "solvly_prefs";
    public static final String KEY_POLICY_ACCEPTED = "policy_accepted";

    private CheckBox  checkBoxPolicy;
    private Button    buttonContinue;
    private TextView  textPolicyLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ─── Already accepted? Skip straight to MainActivity ───────────────
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_POLICY_ACCEPTED, false)) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_intro);

        checkBoxPolicy  = findViewById(R.id.checkBoxPolicy);
        buttonContinue  = findViewById(R.id.buttonContinue);
        textPolicyLink  = findViewById(R.id.textPolicyLink);

        // ─── Entrance animations ────────────────────────────────────────────
        animateEntrance();

        // ─── Make "Privacy Policy" clickable inside combined TextView ───────
        setupPolicyText();

        // ─── Checkbox → enable / disable button ────────────────────────────
        checkBoxPolicy.setOnCheckedChangeListener((buttonView, isChecked) ->
                setButtonEnabled(isChecked)
        );

        // ─── Continue button ────────────────────────────────────────────────
        buttonContinue.setOnClickListener(v -> {
            if (!checkBoxPolicy.isChecked()) return;

            // Save acceptance permanently
            prefs.edit().putBoolean(KEY_POLICY_ACCEPTED, true).apply();

            // Animate out then go to MainActivity
            View root = findViewById(R.id.introRootLayout);
            root.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(this::goToMain)
                    .start();
        });
    }

    // ───────────────────────────────────────────────────────────────────────
    private void setupPolicyText() {
        String full   = "I have read and agree to the Privacy Policy";
        String target = "Privacy Policy";
        int start = full.indexOf(target);
        int end   = start + target.length();

        SpannableString ss = new SpannableString(full);

        int primaryColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary);
        ss.setSpan(new ForegroundColorSpan(primaryColor),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Bold
        ss.setSpan(new StyleSpan(Typeface.BOLD),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Clickable → open PolicyActivity
        ss.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                startActivity(new Intent(IntroActivity.this, PolicyActivity.class));
            }
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
                ds.setColor(primaryColor);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        textPolicyLink.setText(ss);
        textPolicyLink.setMovementMethod(LinkMovementMethod.getInstance());
        textPolicyLink.setHighlightColor(Color.TRANSPARENT);
    }

    // ───────────────────────────────────────────────────────────────────────
    private void setButtonEnabled(boolean enabled) {
        buttonContinue.setEnabled(enabled);
        buttonContinue.setBackground(
                enabled
                        ? getDrawable(R.drawable.intro_button_enabled_bg)
                        : getDrawable(R.drawable.intro_button_disabled_bg)
        );
        buttonContinue.animate()
                .scaleX(enabled ? 1.02f : 1f)
                .scaleY(enabled ? 1.02f : 1f)
                .setDuration(150)
                .start();
    }

    // ───────────────────────────────────────────────────────────────────────
    private void animateEntrance() {
        // Icon + content slides up from below
        LinearLayout content = findViewById(R.id.contentCenter);
        content.setTranslationY(60f);
        content.setAlpha(0f);
        content.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

        // Bottom card slides up
        LinearLayout bottomCard = findViewById(R.id.bottomCard);
        bottomCard.setTranslationY(120f);
        bottomCard.setAlpha(0f);
        bottomCard.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(200)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    // ───────────────────────────────────────────────────────────────────────
    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
