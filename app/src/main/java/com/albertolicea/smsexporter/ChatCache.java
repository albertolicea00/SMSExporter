package com.albertolicea.smsexporter;

import java.util.List;

/** Hands the loaded chat list from MainActivity to ExportActivity without re-scanning the SMS DB. */
final class ChatCache {
    static volatile List<ChatInfo> chats;

    private ChatCache() {}
}
