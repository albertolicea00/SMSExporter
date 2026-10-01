package com.albertolicea.smsexporter;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/** Writes the selected chats to Downloads/SMS Export. Run off the main thread. */
final class ExportTask implements Runnable {

    interface Listener {
        void onProgress(String chat, int doneMessages, int totalMessages);
        void onDone(List<String> fileNames);
        void onError(String message);
        void onCancelled();
    }

    static final String FOLDER = "SMS Export";

    private final Context ctx;
    private final ContentResolver cr;
    private final List<ChatInfo> chats;
    private final ExportSettings settings;
    private final Listener listener;
    private volatile boolean cancelled;
    private int done;
    private int total;

    ExportTask(Context ctx, List<ChatInfo> chats, ExportSettings settings, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.cr = this.ctx.getContentResolver();
        this.chats = chats;
        this.settings = settings;
        this.listener = listener;
    }

    void cancel() {
        cancelled = true;
    }

    @Override
    public void run() {
        List<String> written = new ArrayList<>();
        Uri current = null;
        try {
            SmsRepository repo = new SmsRepository(ctx);
            List<ExportSettings.Column> cols = settings.columns();
            for (ChatInfo c : chats) total += c.count;
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

            if (settings.perChat) {
                for (ChatInfo chat : chats) {
                    String name = "sms_" + safe(chat.title()) + "_" + stamp + "." + settings.ext();
                    current = create(name);
                    export(repo, cols, current, chat);
                    publish(current);
                    written.add(name);
                    current = null;
                }
            } else {
                String name = "sms_" + (chats.size() == 1 ? safe(chats.get(0).title())
                        : chats.size() + "_chats") + "_" + stamp + "." + settings.ext();
                current = create(name);
                export(repo, cols, current, chats.toArray(new ChatInfo[0]));
                publish(current);
                written.add(name);
                current = null;
            }
            listener.onDone(written);
        } catch (CancellationException e) {
            discard(current);
            listener.onCancelled();
        } catch (IOException | RuntimeException e) {
            discard(current);
            listener.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void export(SmsRepository repo, List<ExportSettings.Column> cols, Uri uri, ChatInfo... group)
            throws IOException {
        OutputStream os = cr.openOutputStream(uri);
        if (os == null) throw new IOException("openOutputStream returned null");
        try (MessageWriter w = MessageWriter.create(os, cols, settings)) {
            w.begin();
            for (ChatInfo chat : group) {
                w.startChat(chat);
                repo.stream(chat.threadId, cols, settings.datePattern, row -> {
                    if (cancelled) throw new CancellationException();
                    w.message(row);
                    if (++done % 200 == 0) listener.onProgress(chat.title(), done, total);
                });
                w.endChat();
                listener.onProgress(chat.title(), done, total);
            }
            w.end();
        }
    }

    private Uri create(String displayName) throws IOException {
        String ext = displayName.substring(displayName.lastIndexOf('.') + 1);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        v.put(MediaStore.Downloads.MIME_TYPE, mime != null ? mime : "application/octet-stream");
        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
        v.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("MediaStore insert failed for " + displayName);
        return uri;
    }

    private void publish(Uri uri) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.IS_PENDING, 0);
        cr.update(uri, v, null, null);
    }

    private void discard(Uri uri) {
        if (uri == null) return;
        try {
            cr.delete(uri, null, null);
        } catch (RuntimeException ignored) {
            // best effort: a half-written pending file stays hidden from other apps anyway
        }
    }

    private static String safe(String s) {
        String r = s.replaceAll("[^\\p{L}\\p{N}._+-]", "_");
        return r.length() > 60 ? r.substring(0, 60) : r;
    }
}
