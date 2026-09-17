package com.example.solvly.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.solvly.R;
import com.example.solvly.utils.HistoryDbHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryActivity extends AppCompatActivity {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private RecyclerView recyclerView;
    private LinearLayout layoutEmpty;
    private HistoryDbHelper dbHelper;
    private HistoryAdapter adapter;
    private List<HistoryDbHelper.HistoryItem> fullHistoryList = new ArrayList<>();
    private TextView textViewEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        android.content.SharedPreferences prefs = getSharedPreferences("solvly_prefs", MODE_PRIVATE);
        int autoDeleteDays = prefs.getInt("auto_delete_days", 30);
        boolean autoSave = prefs.getBoolean("auto_save_history", true);
        
        dbHelper = HistoryDbHelper.getInstance(this);
        if (autoSave && autoDeleteDays > 0) {
            dbHelper.deleteOldHistory(autoDeleteDays);
        }
        recyclerView = findViewById(R.id.recyclerViewHistory);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        textViewEmpty = findViewById(R.id.textViewEmptyHistory);
        ImageButton buttonBack = findViewById(R.id.buttonBack);
        ImageButton buttonClearAll = findViewById(R.id.buttonClearAll);
        EditText editTextSearch = findViewById(R.id.editTextSearch);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadHistory();

        buttonBack.setOnClickListener(v -> finish());
        buttonClearAll.setOnClickListener(v -> {
            if (fullHistoryList.isEmpty()) return;
                                                                        showClearHistorySheet();
        });

        // Search Functionality
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Swipe to Delete — only on ITEM rows, not HEADER rows
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final android.graphics.Paint paint = new android.graphics.Paint();

            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof HistoryAdapter.HeaderViewHolder) return 0; // Disable swipe on headers
                return super.getSwipeDirs(rv, viewHolder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder v, @NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, rv, viewHolder, dX, dY, actionState, isCurrentlyActive);
                View itemView = viewHolder.itemView;
                if (dX < 0) {
                    float cornerRadius = 16 * rv.getContext().getResources().getDisplayMetrics().density;
                    android.graphics.RectF rect = new android.graphics.RectF(
                            itemView.getRight() + dX, itemView.getTop(),
                            itemView.getRight(), itemView.getBottom()
                    );
                    paint.setColor(androidx.core.content.ContextCompat.getColor(rv.getContext(), R.color.swipe_background));
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

                    paint.setColor(androidx.core.content.ContextCompat.getColor(rv.getContext(), R.color.swipe_text));
                    paint.setTextSize(40);
                    paint.setAntiAlias(true);
                    paint.setTextAlign(android.graphics.Paint.Align.RIGHT);
                    float textY = itemView.getTop() + (itemView.getHeight() / 2f) + (paint.getTextSize() / 3f);
                    c.drawText(rv.getContext().getString(R.string.swipe_delete_label), itemView.getRight() - 40, textY, paint);
                }
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                // Get the actual HistoryItem from the display list
                Object obj = adapter.displayList.get(position);
                if (!(obj instanceof HistoryDbHelper.HistoryItem)) {
                    adapter.notifyItemChanged(position);
                    return;
                }
                HistoryDbHelper.HistoryItem item = (HistoryDbHelper.HistoryItem) obj;

                BottomSheetDialog sheet = new BottomSheetDialog(HistoryActivity.this);
                View sheetView = getLayoutInflater().inflate(R.layout.layout_delete_item_sheet, null);
                sheet.setContentView(sheetView);
                makeSheetWrapContent(sheet);

                sheetView.findViewById(R.id.buttonConfirmDelete).setOnClickListener(v -> {
                    dbHelper.deleteHistory(item.id);
                    fullHistoryList.remove(item);
                    rebuildAdapter(fullHistoryList);
                    updateListVisibility(fullHistoryList.isEmpty(), false);
                    Toast.makeText(HistoryActivity.this, R.string.toast_item_deleted, Toast.LENGTH_SHORT).show();
                    sheet.dismiss();
                });

                sheetView.findViewById(R.id.buttonCancelDelete).setOnClickListener(v -> {
                    adapter.notifyItemChanged(position);
                    sheet.dismiss();
                });

                sheet.setOnCancelListener(d -> adapter.notifyItemChanged(position));
                sheet.show();
            }
        }).attachToRecyclerView(recyclerView);
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

    private void showClearHistorySheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_clear_history_sheet, null);
        sheet.setContentView(sheetView);
        makeSheetWrapContent(sheet);

        sheetView.findViewById(R.id.buttonConfirmClear).setOnClickListener(v -> {
            dbHelper.clearAll();
            loadHistory();
            Toast.makeText(this, R.string.toast_history_cleared, Toast.LENGTH_SHORT).show();
            sheet.dismiss();
        });

        sheetView.findViewById(R.id.buttonCancelClear).setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    private void loadHistory() {
        fullHistoryList = dbHelper.getAllHistory();
        rebuildAdapter(fullHistoryList);
        updateListVisibility(fullHistoryList.isEmpty(), false);
    }

    private void rebuildAdapter(List<HistoryDbHelper.HistoryItem> items) {
        List<Object> displayList = buildDisplayList(items);
        if (adapter == null) {
            adapter = new HistoryAdapter(displayList);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateList(displayList);
        }
    }

    private void filter(String text) {
        List<HistoryDbHelper.HistoryItem> filtered = new ArrayList<>();
        for (HistoryDbHelper.HistoryItem item : fullHistoryList) {
            String q = text.toLowerCase();
            if ((item.title   != null && item.title.toLowerCase().contains(q))   ||
                (item.problem != null && item.problem.toLowerCase().contains(q)) ||
                (item.answer  != null && item.answer.toLowerCase().contains(q))) {
                filtered.add(item);
            }
        }
        rebuildAdapter(filtered);
        updateListVisibility(filtered.isEmpty(), !text.trim().isEmpty());
    }

    private void updateListVisibility(boolean isEmpty, boolean isSearch) {
        if (isEmpty) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            if (textViewEmpty != null) {
                textViewEmpty.setText(isSearch ? R.string.history_no_search_results : R.string.history_no_history);
            }
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    // Build a flat list with String date headers interspersed between HistoryItems
    private List<Object> buildDisplayList(List<HistoryDbHelper.HistoryItem> items) {
        List<Object> list = new ArrayList<>();
        String lastDate = "";
        for (HistoryDbHelper.HistoryItem item : items) {
            String dateStr = getLocalDateString(item.timestamp);
            if (!dateStr.equals(lastDate)) {
                list.add(getRelativeDateLabel(dateStr)); // Add header string
                lastDate = dateStr;
            }
            list.add(item); // Add item
        }
        return list;
    }

    private String getLocalDateString(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) return "unknown";
        try {
            SimpleDateFormat utcFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            utcFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = utcFmt.parse(timestamp);
            SimpleDateFormat localFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            localFmt.setTimeZone(TimeZone.getDefault());
            return localFmt.format(date);
        } catch (Exception e) {
            return timestamp.substring(0, 10);
        }
    }

    private String getRelativeDateLabel(String dateStr) {
        if (dateStr.equals("unknown")) return "Unknown Date";
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = fmt.parse(dateStr);

            Calendar calDate = Calendar.getInstance();
            calDate.setTime(date);

            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            if (calDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                calDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                return "Today";
            } else if (calDate.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                       calDate.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)) {
                return "Yesterday";
            } else {
                return new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date);
            }
        } catch (Exception e) {
            return dateStr;
        }
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    private class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Object> displayList;

        HistoryAdapter(List<Object> displayList) {
            this.displayList = displayList;
        }

        void updateList(List<Object> newList) {
            this.displayList = newList;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return displayList.get(position) instanceof String ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_HEADER) {
                View v = inflater.inflate(R.layout.item_history_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_history, parent, false);
                return new ItemViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvHeader.setText((String) displayList.get(position));
            } else if (holder instanceof ItemViewHolder) {
                HistoryDbHelper.HistoryItem item = (HistoryDbHelper.HistoryItem) displayList.get(position);
                ItemViewHolder h = (ItemViewHolder) holder;

                // ① Title
                h.tvTitle.setText(item.title != null ? item.title : "Problem");

                // ② Formatted date-time (local timezone: dd MMM yyyy, hh:mm a)
                h.tvTimestamp.setText(formatTimestamp(item.timestamp));

                // ③ Problem statement
                h.tvProblem.setText(stripLatex(item.problem));

                // ④ Answer
                h.tvAnswer.setText(stripLatex(item.answer));

                h.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(HistoryActivity.this, ResultActivity.class);
                    intent.putExtra("raw_text", item.problem);
                    intent.putExtra("saved_answer", item.answer);
                    intent.putStringArrayListExtra("saved_steps", new ArrayList<>(item.steps));
                    startActivity(intent);
                });

                h.btnCopy.setOnClickListener(v -> {
                    String fullText = buildHistoryExportText(item);
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Solvly Solution", fullText));
                    Toast.makeText(HistoryActivity.this, R.string.toast_solution_copied, Toast.LENGTH_SHORT).show();
                });

                h.btnShare.setOnClickListener(v -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, buildHistoryExportText(item));
                    startActivity(Intent.createChooser(shareIntent, "Share with..."));
                });
            }
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        private String stripLatex(String text) {
            if (text == null) return "";
            return text.replace("$$", "").replace("$", "").trim();
        }

        private String formatTimestamp(String timestamp) {
            if (timestamp == null || timestamp.length() < 10) return timestamp != null ? timestamp : "";
            try {
                SimpleDateFormat utcFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                utcFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = utcFmt.parse(timestamp);
                SimpleDateFormat localFmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                localFmt.setTimeZone(TimeZone.getDefault());
                return localFmt.format(date);
            } catch (Exception e) {
                return timestamp;
            }
        }

        private String buildHistoryExportText(HistoryDbHelper.HistoryItem item) {
            StringBuilder exportText = new StringBuilder();
            exportText.append(item.title).append("\n")
                    .append(formatTimestamp(item.timestamp)).append("\n\n")
                    .append("Problem: ").append(item.problem).append("\n\n")
                    .append("Answer: ").append(item.answer);
            if (item.steps != null && !item.steps.isEmpty()) {
                exportText.append("\n\nSolution Steps:\n");
                for (int i = 0; i < item.steps.size(); i++) {
                    exportText.append(i + 1).append(". ").append(item.steps.get(i)).append("\n");
                }
            }
            return exportText.toString().trim();
        }

        // ─── ViewHolders ────────────────────────────────────────────────────

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvHeader;
            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvHeader = itemView.findViewById(R.id.textViewDateHeader);
            }
        }

        class ItemViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTimestamp, tvProblem, tvAnswer;
            ImageButton btnCopy, btnShare;
            ItemViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle     = itemView.findViewById(R.id.textViewTitle);
                tvTimestamp = itemView.findViewById(R.id.textViewTimestamp);
                tvProblem   = itemView.findViewById(R.id.textViewProblem);
                tvAnswer    = itemView.findViewById(R.id.textViewAnswer);
                btnCopy     = itemView.findViewById(R.id.buttonCopy);
                btnShare    = itemView.findViewById(R.id.buttonShare);
            }
        }
    }
}
