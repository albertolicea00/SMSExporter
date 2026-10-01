package com.albertolicea.smsexporter;

/** One SMS conversation (thread). */
public final class ChatInfo {
    public final long threadId;
    public String address;
    public String name;
    public int count;
    public long lastDate;
    public String snippet;

    public ChatInfo(long threadId) {
        this.threadId = threadId;
    }

    public String title() {
        if (name != null && !name.isEmpty()) return name;
        if (address != null && !address.isEmpty()) return address;
        return "#" + threadId;
    }
}
