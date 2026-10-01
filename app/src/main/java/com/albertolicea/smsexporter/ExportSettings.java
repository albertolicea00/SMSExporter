package com.albertolicea.smsexporter;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** User choices for an export: format, which fields, and what each label is called. Persisted. */
final class ExportSettings {

    static final class Column {
        final SmsField field;
        final String label;

        Column(SmsField field, String label) {
            this.field = field;
            this.label = label;
        }
    }

    /** Keys naming the container levels (JSON keys / XML attributes). Label-only, no on/off. */
    static final String[] STRUCT_KEYS = {
            "chats", "messages", "chat_thread_id", "chat_address", "chat_name"};
    static final int[] STRUCT_DESC = {
            R.string.s_chats, R.string.s_messages, R.string.s_chat_thread_id,
            R.string.s_chat_address, R.string.s_chat_name};

    static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    String format = "json";
    String datePattern = DEFAULT_DATE_PATTERN;
    boolean perChat = false;
    boolean jsonFlat = false;
    final Map<String, Boolean> enabled = new LinkedHashMap<>();
    final Map<String, String> labels = new LinkedHashMap<>();
    final Map<String, String> structure = new LinkedHashMap<>();

    static ExportSettings defaults() {
        ExportSettings s = new ExportSettings();
        for (SmsField f : SmsField.ALL) {
            s.enabled.put(f.key, f.defaultOn);
            s.labels.put(f.key, f.key);
        }
        for (String k : STRUCT_KEYS) s.structure.put(k, k);
        return s;
    }

    static ExportSettings load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("export", Context.MODE_PRIVATE);
        ExportSettings s = defaults();
        s.format = p.getString("format", s.format);
        s.datePattern = p.getString("date_pattern", s.datePattern);
        s.perChat = p.getBoolean("per_chat", s.perChat);
        s.jsonFlat = p.getBoolean("json_flat", s.jsonFlat);
        for (SmsField f : SmsField.ALL) {
            s.enabled.put(f.key, p.getBoolean("on_" + f.key, f.defaultOn));
            s.labels.put(f.key, p.getString("label_" + f.key, f.key));
        }
        for (String k : STRUCT_KEYS) s.structure.put(k, p.getString("struct_" + k, k));
        return s;
    }

    void save(Context ctx) {
        SharedPreferences.Editor e = ctx.getSharedPreferences("export", Context.MODE_PRIVATE).edit();
        e.putString("format", format);
        e.putString("date_pattern", datePattern);
        e.putBoolean("per_chat", perChat);
        e.putBoolean("json_flat", jsonFlat);
        for (Map.Entry<String, Boolean> en : enabled.entrySet()) e.putBoolean("on_" + en.getKey(), en.getValue());
        for (Map.Entry<String, String> en : labels.entrySet()) e.putString("label_" + en.getKey(), en.getValue());
        for (Map.Entry<String, String> en : structure.entrySet()) e.putString("struct_" + en.getKey(), en.getValue());
        e.apply();
    }

    /** Selected fields in canonical order, with their chosen labels. */
    List<Column> columns() {
        List<Column> out = new ArrayList<>();
        for (SmsField f : SmsField.ALL) {
            if (Boolean.TRUE.equals(enabled.get(f.key))) out.add(new Column(f, labels.get(f.key)));
        }
        return out;
    }

    String ext() {
        return format;
    }
}
