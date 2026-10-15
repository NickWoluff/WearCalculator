package com.nickwoluff.wearcalculator;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** 显示最近十次运算，最新项目位于列表底部。 */
public class HistoryActivity extends WearActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        LinearLayout entries = findViewById(R.id.historyEntries);
        boolean round = getResources().getConfiguration().isScreenRound();
        entries.setPadding(0, 0, 0, Math.round(dp(round ? 12 : 6)));
        List<HistoryStore.Entry> history = HistoryStore.read(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.history_empty);
            empty.setTextColor(0xffc3c6cf);
            empty.setTextSize(15);
            empty.setGravity(android.view.Gravity.CENTER);
            entries.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            for (HistoryStore.Entry entry : history) {
                View row = inflater.inflate(R.layout.history_entry, entries, false);
                ((TextView) row.findViewById(R.id.historyEquation)).setText(getString(R.string.history_equation, entry.equation));
                ((TextView) row.findViewById(R.id.historyResult)).setText(entry.result);
                ((TextView) row.findViewById(R.id.historyEquation)).setTextSize(round ? 16 : 14);
                ((TextView) row.findViewById(R.id.historyResult)).setTextSize(round ? 26 : 28);
                for (int id : new int[]{R.id.historyEquationScroll, R.id.historyResultScroll}) {
                    HorizontalScrollView horizontal = row.findViewById(id);
                    horizontal.post(() -> horizontal.fullScroll(View.FOCUS_RIGHT));
                }
                if (entries.getChildCount() > 0) {
                    View divider = new View(this);
                    divider.setBackgroundColor(AppAppearance.outlineVariant(this));
                    LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, Math.round(dp(1)));
                    dividerParams.leftMargin = Math.round(dp(20));
                    dividerParams.rightMargin = Math.round(dp(20));
                    entries.addView(divider, dividerParams);
                }
                entries.addView(row);
            }
        }
        AppAppearance.applyToViews(entries);
        ScrollView scroll = findViewById(R.id.historyScroll);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

}
