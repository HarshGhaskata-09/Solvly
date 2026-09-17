package com.example.solvly.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.solvly.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class SecuritySettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_settings);

        prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());

        setupDataEncryption();
    }

    private void setupDataEncryption() {
        View row = findViewById(R.id.itemDataEncryption);
        if (row == null) return;

        TextView tvTitle = row.findViewById(R.id.settingTitle);
        TextView tvSubtitle = row.findViewById(R.id.settingSubtitle);
        ImageView ivIcon = row.findViewById(R.id.settingIcon);

        tvTitle.setText("Data Encryption");
        ivIcon.setImageResource(R.drawable.ic_datacontrol);
        tvSubtitle.setVisibility(View.VISIBLE);
        tvSubtitle.setText("Data stored locally on device");

        row.setOnClickListener(v -> {
            BottomSheetDialog sheet = new BottomSheetDialog(this);
            View sheetView = LayoutInflater.from(this).inflate(R.layout.layout_security_info_sheet, null);
            sheet.setContentView(sheetView);
            makeSheetWrapContent(sheet);

            sheetView.findViewById(R.id.buttonOkay).setOnClickListener(v1 -> sheet.dismiss());

            sheet.show();
        });
    }

    private void makeSheetWrapContent(BottomSheetDialog dialog) {
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
    }
}
