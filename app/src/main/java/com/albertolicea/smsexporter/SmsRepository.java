package com.albertolicea.smsexporter;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the system SMS provider (content://sms). SMS only; MMS is not included. */
final class SmsRepository {

    interface RowSink {
        void row(Object[] values) throws IOException;
    }

    private static final Uri SMS = Uri.parse("content://sms");
    private static final String[] WANTED = {
            "_id", "thread_id", "address", "date", "date_sent", "type", "read", "seen",
            "locked", "status", "protocol", "subject", "service_center", "sub_id", "body"};

    private final ContentResolver cr;
    private final ContactResolver contacts;
    private final Set<String> available = new HashSet<>();

    SmsRepository(Context ctx) {
        cr = ctx.getContentResolver();
        contacts = new ContactResolver(ctx);
        // OEM providers differ; only ask for columns that really exist.
        try (Cursor c = cr.query(SMS, null, "0=1", null, null)) {
            if (c != null) {
                for (String name : c.getColumnNames()) available.add(name);
            }
        }
    }

    List<ChatInfo> loadChats() {
        Map<Long, ChatInfo> map = new LinkedHashMap<>();
        try (Cursor c = cr.query(SMS, new String[]{"thread_id", "address", "date", "body"},
                null, null, "date DESC")) {
            if (c == null) return new ArrayList<>();
            while (c.moveToNext()) {
                long tid = c.getLong(0);
                ChatInfo ci = map.get(tid);
                if (ci == null) {
                    ci = new ChatInfo(tid);
                    ci.address = c.getString(1);
                    ci.lastDate = c.getLong(2);
                    ci.snippet = c.getString(3);
                    map.put(tid, ci);
                }
                ci.count++;
            }
        }
        for (ChatInfo ci : map.values()) ci.name = contacts.name(ci.address);
        return new ArrayList<>(map.values());
    }

    void stream(long threadId, List<ExportSettings.Column> cols, String datePattern, RowSink sink)
            throws IOException {
        SimpleDateFormat fmt = new SimpleDateFormat(datePattern, java.util.Locale.getDefault());
        List<String> proj = new ArrayList<>();
        for (String w : WANTED) if (available.contains(w)) proj.add(w);
        try (Cursor c = cr.query(SMS, proj.toArray(new String[0]), "thread_id = ?",
                new String[]{String.valueOf(threadId)}, "date ASC, _id ASC")) {
            if (c == null) return;
            while (c.moveToNext()) {
                Object[] row = new Object[cols.size()];
                for (int i = 0; i < row.length; i++) row[i] = value(c, cols.get(i).field, fmt);
                sink.row(row);
            }
        }
    }

    private Object value(Cursor c, SmsField f, SimpleDateFormat fmt) {
        switch (f.key) {
            case "contact_name":
                return contacts.name(text(c, "address"));
            case "direction":
                return direction(num(c, "type"));
            case "date":
                return formatDate(fmt, num(c, "date"));
            case "date_sent":
                return formatDate(fmt, num(c, "date_sent"));
            case "date_ms":
                return num(c, "date");
            case "id":
                return num(c, "_id");
            default:
                return f.kind == SmsField.Kind.NUMBER ? num(c, f.key) : text(c, f.key);
        }
    }

    private static String formatDate(SimpleDateFormat fmt, Long ms) {
        // date_sent is 0 for outgoing messages; an epoch-1970 string would be misleading.
        if (ms == null || ms == 0) return null;
        return fmt.format(new Date(ms));
    }

    private static String direction(Long type) {
        if (type == null) return null;
        switch (type.intValue()) {
            case 1: return "received";
            case 2: return "sent";
            case 3: return "draft";
            case 4: return "outbox";
            case 5: return "failed";
            case 6: return "queued";
            default: return "unknown";
        }
    }

    private static Long num(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? null : c.getLong(i);
    }

    private static String text(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? null : c.getString(i);
    }
}
