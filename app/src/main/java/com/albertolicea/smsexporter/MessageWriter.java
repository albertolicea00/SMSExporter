package com.albertolicea.smsexporter;

import android.util.JsonWriter;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Format-specific output. Call order: begin, (startChat, message*, endChat)*, end, close. */
abstract class MessageWriter implements Closeable {

    protected final Writer out;
    protected final List<ExportSettings.Column> cols;
    protected final ExportSettings settings;

    private MessageWriter(OutputStream os, List<ExportSettings.Column> cols, ExportSettings settings) {
        this.out = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), 1 << 16);
        this.cols = cols;
        this.settings = settings;
    }

    abstract void begin() throws IOException;

    abstract void startChat(ChatInfo chat) throws IOException;

    abstract void message(Object[] values) throws IOException;

    abstract void endChat() throws IOException;

    abstract void end() throws IOException;

    @Override
    public void close() throws IOException {
        out.close();
    }

    static MessageWriter create(OutputStream os, List<ExportSettings.Column> cols, ExportSettings s)
            throws IOException {
        switch (s.format) {
            case "csv": return new Csv(os, cols, s);
            case "txt": return new Txt(os, cols, s);
            case "xml": return new XmlOut(os, cols, s);
            default: return new Json(os, cols, s);
        }
    }

    // ---------------------------------------------------------------- JSON

    private static final class Json extends MessageWriter {
        private final JsonWriter jw;
        private final boolean flat;

        Json(OutputStream os, List<ExportSettings.Column> cols, ExportSettings s) {
            super(os, cols, s);
            jw = new JsonWriter(out);
            jw.setIndent("  ");
            flat = s.jsonFlat;
        }

        @Override void begin() throws IOException {
            if (flat) {
                jw.beginArray();
            } else {
                jw.beginObject();
                jw.name(settings.structure.get("chats"));
                jw.beginArray();
            }
        }

        @Override void startChat(ChatInfo c) throws IOException {
            if (flat) return;
            jw.beginObject();
            jw.name(settings.structure.get("chat_thread_id")).value(c.threadId);
            jw.name(settings.structure.get("chat_address")).value(c.address);
            jw.name(settings.structure.get("chat_name")).value(c.name);
            jw.name(settings.structure.get("messages"));
            jw.beginArray();
        }

        @Override void message(Object[] v) throws IOException {
            jw.beginObject();
            for (int i = 0; i < v.length; i++) {
                jw.name(cols.get(i).label);
                if (v[i] == null) jw.nullValue();
                else if (v[i] instanceof Number) jw.value((Number) v[i]);
                else jw.value(v[i].toString());
            }
            jw.endObject();
        }

        @Override void endChat() throws IOException {
            if (flat) return;
            jw.endArray();
            jw.endObject();
        }

        @Override void end() throws IOException {
            jw.endArray();
            if (!flat) jw.endObject();
            jw.flush();
        }
    }

    // ----------------------------------------------------------------- CSV

    private static final class Csv extends MessageWriter {
        Csv(OutputStream os, List<ExportSettings.Column> cols, ExportSettings s) {
            super(os, cols, s);
        }

        @Override void begin() throws IOException {
            out.write('﻿'); // BOM so Excel detects UTF-8
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) out.write(',');
                out.write(quote(cols.get(i).label));
            }
            out.write("\r\n");
        }

        @Override void startChat(ChatInfo c) {}

        @Override void message(Object[] v) throws IOException {
            for (int i = 0; i < v.length; i++) {
                if (i > 0) out.write(',');
                if (v[i] != null) out.write(quote(v[i].toString()));
            }
            out.write("\r\n");
        }

        @Override void endChat() {}

        @Override void end() throws IOException {
            out.flush();
        }

        private static String quote(String s) {
            boolean need = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0
                    || s.indexOf('\r') >= 0 || s.startsWith(" ") || s.endsWith(" ");
            return need ? '"' + s.replace("\"", "\"\"") + '"' : s;
        }
    }

    // ----------------------------------------------------------------- TXT

    private static final class Txt extends MessageWriter {
        Txt(OutputStream os, List<ExportSettings.Column> cols, ExportSettings s) {
            super(os, cols, s);
        }

        @Override void begin() {}

        @Override void startChat(ChatInfo c) throws IOException {
            out.write("=== " + c.title());
            if (c.address != null && !c.address.equals(c.title())) out.write(" (" + c.address + ")");
            out.write(" [thread " + c.threadId + "] ===\n\n");
        }

        @Override void message(Object[] v) throws IOException {
            for (int i = 0; i < v.length; i++) {
                out.write(cols.get(i).label);
                out.write(": ");
                if (v[i] != null) out.write(v[i].toString());
                out.write('\n');
            }
            out.write('\n');
        }

        @Override void endChat() throws IOException {
            out.write('\n');
        }

        @Override void end() throws IOException {
            out.flush();
        }
    }

    // ----------------------------------------------------------------- XML

    private static final class XmlOut extends MessageWriter {
        private final XmlSerializer xs = Xml.newSerializer();

        XmlOut(OutputStream os, List<ExportSettings.Column> cols, ExportSettings s) throws IOException {
            super(os, cols, s);
            xs.setOutput(out);
            xs.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        }

        @Override void begin() throws IOException {
            xs.startDocument("UTF-8", true);
            xs.startTag("", name(settings.structure.get("chats")));
        }

        @Override void startChat(ChatInfo c) throws IOException {
            xs.startTag("", "chat");
            xs.attribute("", name(settings.structure.get("chat_thread_id")), String.valueOf(c.threadId));
            if (c.address != null) xs.attribute("", name(settings.structure.get("chat_address")), clean(c.address));
            if (c.name != null) xs.attribute("", name(settings.structure.get("chat_name")), clean(c.name));
        }

        @Override void message(Object[] v) throws IOException {
            xs.startTag("", "message");
            for (int i = 0; i < v.length; i++) {
                xs.startTag("", name(cols.get(i).label));
                if (v[i] != null) xs.text(clean(v[i].toString()));
                xs.endTag("", name(cols.get(i).label));
            }
            xs.endTag("", "message");
        }

        @Override void endChat() throws IOException {
            xs.endTag("", "chat");
        }

        @Override void end() throws IOException {
            xs.endTag("", name(settings.structure.get("chats")));
            xs.endDocument();
            xs.flush();
        }

        /** Turn a free-text label into a legal XML element/attribute name. */
        private static String name(String label) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < label.length(); i++) {
                char ch = label.charAt(i);
                boolean ok = Character.isLetter(ch) || ch == '_'
                        || (i > 0 && (Character.isDigit(ch) || ch == '-' || ch == '.'));
                sb.append(ok ? ch : '_');
            }
            if (sb.length() == 0 || !(Character.isLetter(sb.charAt(0)) || sb.charAt(0) == '_')) sb.insert(0, '_');
            return sb.toString();
        }

        /** Drop code points XML 1.0 cannot represent (e.g. control chars found in odd SMS). */
        private static String clean(String s) {
            StringBuilder sb = null;
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                int len = Character.charCount(cp);
                boolean ok = cp == 0x9 || cp == 0xA || cp == 0xD
                        || (cp >= 0x20 && cp <= 0xD7FF)
                        || (cp >= 0xE000 && cp <= 0xFFFD)
                        || (cp >= 0x10000 && cp <= 0x10FFFF);
                if (!ok && sb == null) sb = new StringBuilder(s.substring(0, i));
                if (ok && sb != null) sb.append(s, i, i + len);
                i += len;
            }
            return sb == null ? s : sb.toString();
        }
    }
}
