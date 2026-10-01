package com.albertolicea.smsexporter;

import android.Manifest;
import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.HashMap;
import java.util.Map;

/** Phone number -> contact display name, cached. Returns null when unknown or no permission. */
final class ContactResolver {
    private final ContentResolver resolver;
    private final boolean enabled;
    private final Map<String, String> cache = new HashMap<>();

    ContactResolver(Context ctx) {
        resolver = ctx.getContentResolver();
        enabled = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    String name(String address) {
        if (!enabled || address == null || address.isEmpty()) return null;
        if (cache.containsKey(address)) return cache.get(address);
        String result = null;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address));
        try (Cursor c = resolver.query(uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) result = c.getString(0);
        } catch (RuntimeException ignored) {
            // some providers reject non-numeric senders; treat as unknown
        }
        cache.put(address, result);
        return result;
    }
}
