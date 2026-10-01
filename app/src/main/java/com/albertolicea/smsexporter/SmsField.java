package com.albertolicea.smsexporter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A message attribute the user can include in the export, and its default label. */
public final class SmsField {

    public enum Kind { NUMBER, TEXT }

    public final String key;
    public final Kind kind;
    public final int descRes;
    public final boolean defaultOn;

    private SmsField(String key, Kind kind, int descRes, boolean defaultOn) {
        this.key = key;
        this.kind = kind;
        this.descRes = descRes;
        this.defaultOn = defaultOn;
    }

    public static final List<SmsField> ALL = Collections.unmodifiableList(Arrays.asList(
            new SmsField("id", Kind.NUMBER, R.string.f_id, false),
            new SmsField("thread_id", Kind.NUMBER, R.string.f_thread_id, false),
            new SmsField("address", Kind.TEXT, R.string.f_address, true),
            new SmsField("contact_name", Kind.TEXT, R.string.f_contact_name, true),
            new SmsField("body", Kind.TEXT, R.string.f_body, true),
            new SmsField("date", Kind.TEXT, R.string.f_date, true),
            new SmsField("date_ms", Kind.NUMBER, R.string.f_date_ms, false),
            new SmsField("date_sent", Kind.TEXT, R.string.f_date_sent, false),
            new SmsField("direction", Kind.TEXT, R.string.f_direction, true),
            new SmsField("type", Kind.NUMBER, R.string.f_type, false),
            new SmsField("read", Kind.NUMBER, R.string.f_read, false),
            new SmsField("seen", Kind.NUMBER, R.string.f_seen, false),
            new SmsField("locked", Kind.NUMBER, R.string.f_locked, false),
            new SmsField("status", Kind.NUMBER, R.string.f_status, false),
            new SmsField("protocol", Kind.NUMBER, R.string.f_protocol, false),
            new SmsField("subject", Kind.TEXT, R.string.f_subject, false),
            new SmsField("service_center", Kind.TEXT, R.string.f_service_center, false),
            new SmsField("sub_id", Kind.NUMBER, R.string.f_sub_id, false)
    ));
}
