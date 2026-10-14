package com.nickwoluff.wearcalculator;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 用轻量 JSON 保存最近十次成功计算，保持跨页面和重启可用。 */
public final class HistoryStore {
    private static final String PREFS = "calculator_history";
    private static final String KEY_ENTRIES = "entries";

    public static final class Entry {
        public final String equation;
        public final String result;

        Entry(String equation, String result) {
            this.equation = equation;
            this.result = result;
        }
    }

    private HistoryStore() {
    }

    public static synchronized void add(Context context, String equation, String result) {
        List<Entry> entries = read(context);
        entries.add(new Entry(equation, result));
        while (entries.size() > 10) entries.remove(0);
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) {
                JSONObject object = new JSONObject();
                object.put("equation", entry.equation);
                object.put("result", entry.result);
                array.put(object);
            }
        } catch (JSONException exception) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ENTRIES, array.toString()).apply();
    }

    public static synchronized List<Entry> read(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRIES, "[]");
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = Math.max(0, array.length() - 10); i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null && object.opt("equation") instanceof String
                        && object.opt("result") instanceof String) {
                    entries.add(new Entry(object.getString("equation"), object.getString("result")));
                }
            }
        } catch (JSONException ignored) {
            entries.clear();
        }
        return entries;
    }
}
