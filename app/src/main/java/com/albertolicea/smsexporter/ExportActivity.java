package com.albertolicea.smsexporter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExportActivity extends AppCompatActivity {

    static final String EXTRA_THREADS = "threads";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<String, CheckBox> fieldChecks = new LinkedHashMap<>();
    private final Map<String, EditText> fieldLabels = new LinkedHashMap<>();
    private final Map<String, EditText> structLabels = new LinkedHashMap<>();

    private final List<ChatInfo> chats = new ArrayList<>();
    private ExportSettings settings;
    private ExportTask task;

    private RadioGroup format;
    private CheckBox perChat, jsonFlat;
    private EditText datePattern;
    private Button exportBtn;
    private View progressBox;
    private ProgressBar progress;
    private TextView progressText, result;
    private View advanced;
    private Button advancedToggle;
    private boolean errorInAdvanced;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle(R.string.title_export);
        setContentView(R.layout.activity_export);

        List<ChatInfo> cache = ChatCache.chats;
        long[] ids = getIntent().getLongArrayExtra(EXTRA_THREADS);
        if (cache == null || ids == null) {
            Toast.makeText(this, R.string.err_cache, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Set<Long> wanted = new HashSet<>();
        for (long id : ids) wanted.add(id);
        for (ChatInfo c : cache) if (wanted.contains(c.threadId)) chats.add(c);

        settings = ExportSettings.load(this);

        ((TextView) findViewById(R.id.summary)).setText(getResources().getQuantityString(R.plurals.chats_selected, chats.size(), chats.size()));
        format = findViewById(R.id.format);
        perChat = findViewById(R.id.per_chat);
        jsonFlat = findViewById(R.id.json_flat);
        datePattern = findViewById(R.id.date_pattern);
        exportBtn = findViewById(R.id.export);
        progressBox = findViewById(R.id.progress_box);
        progress = findViewById(R.id.progress);
        progressText = findViewById(R.id.progress_text);
        result = findViewById(R.id.result);

        LayoutInflater inf = LayoutInflater.from(this);
        advanced = findViewById(R.id.advanced);
        advancedToggle = findViewById(R.id.advanced_toggle);
        advancedToggle.setOnClickListener(v -> setAdvancedOpen(advanced.getVisibility() != View.VISIBLE));

        android.view.ViewGroup fieldsMain = findViewById(R.id.fields);
        android.view.ViewGroup fieldsAdvanced = findViewById(R.id.fields_advanced);
        for (SmsField f : SmsField.ALL) {
            android.view.ViewGroup fields = f.advanced ? fieldsAdvanced : fieldsMain;
            View row = inf.inflate(R.layout.row_field, fields, false);
            // every row reuses the same view ids, so Android's id-based state restore would
            // copy one row's value into all of them; apply() reloads from settings instead
            row.setSaveFromParentEnabled(false);
            CheckBox cb = row.findViewById(R.id.check);
            cb.setText(f.descRes);
            EditText et = row.findViewById(R.id.label);
            fieldChecks.put(f.key, cb);
            fieldLabels.put(f.key, et);
            fields.addView(row);
        }
        android.view.ViewGroup structure = findViewById(R.id.structure);
        for (int i = 0; i < ExportSettings.STRUCT_KEYS.length; i++) {
            View row = inf.inflate(R.layout.row_field, structure, false);
            row.setSaveFromParentEnabled(false);
            row.findViewById(R.id.check).setVisibility(View.GONE);
            TextView tv = row.findViewById(R.id.text);
            tv.setVisibility(View.VISIBLE);
            tv.setText(ExportSettings.STRUCT_DESC[i]);
            structLabels.put(ExportSettings.STRUCT_KEYS[i], row.findViewById(R.id.label));
            structure.addView(row);
        }

        format.setOnCheckedChangeListener((g, id) -> updateVisibility());
        perChat.setVisibility(chats.size() > 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.reset).setOnClickListener(v -> {
            ExportSettings d = ExportSettings.defaults();
            d.format = formatKey();
            d.datePattern = datePattern.getText().toString();
            d.perChat = perChat.isChecked();
            d.jsonFlat = jsonFlat.isChecked();
            apply(d);
        });
        exportBtn.setOnClickListener(v -> startExport());
        findViewById(R.id.cancel).setOnClickListener(v -> {
            if (task != null) task.cancel();
        });

        apply(settings);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && task != null) task.cancel();
        io.shutdown();
    }

    private void apply(ExportSettings s) {
        switch (s.format) {
            case "csv": format.check(R.id.fmt_csv); break;
            case "txt": format.check(R.id.fmt_txt); break;
            case "xml": format.check(R.id.fmt_xml); break;
            default: format.check(R.id.fmt_json);
        }
        perChat.setChecked(s.perChat);
        jsonFlat.setChecked(s.jsonFlat);
        datePattern.setText(s.datePattern);
        for (SmsField f : SmsField.ALL) {
            fieldChecks.get(f.key).setChecked(Boolean.TRUE.equals(s.enabled.get(f.key)));
            fieldLabels.get(f.key).setText(s.labels.get(f.key));
        }
        for (String k : ExportSettings.STRUCT_KEYS) structLabels.get(k).setText(s.structure.get(k));
        updateVisibility();
    }

    private String formatKey() {
        int id = format.getCheckedRadioButtonId();
        if (id == R.id.fmt_csv) return "csv";
        if (id == R.id.fmt_txt) return "txt";
        if (id == R.id.fmt_xml) return "xml";
        return "json";
    }

    private void setAdvancedOpen(boolean open) {
        advanced.setVisibility(open ? View.VISIBLE : View.GONE);
        advancedToggle.setText(open ? R.string.advanced_open : R.string.advanced_closed);
    }

    private void updateVisibility() {
        jsonFlat.setVisibility("json".equals(formatKey()) ? View.VISIBLE : View.GONE);
    }

    /** Copies the form into {@link #settings}; returns an error string or null. */
    private String readForm() {
        errorInAdvanced = false;
        settings.format = formatKey();
        settings.perChat = chats.size() > 1 && perChat.isChecked();
        settings.jsonFlat = jsonFlat.isChecked();
        settings.datePattern = datePattern.getText().toString().trim();
        try {
            if (settings.datePattern.isEmpty()) throw new IllegalArgumentException();
            new SimpleDateFormat(settings.datePattern);
        } catch (IllegalArgumentException e) {
            errorInAdvanced = true;
            return getString(R.string.err_date_pattern);
        }

        Set<String> seen = new HashSet<>();
        for (SmsField f : SmsField.ALL) {
            boolean on = fieldChecks.get(f.key).isChecked();
            String label = fieldLabels.get(f.key).getText().toString().trim();
            settings.enabled.put(f.key, on);
            settings.labels.put(f.key, label);
            if (on) {
                errorInAdvanced = f.advanced;
                if (label.isEmpty()) return getString(R.string.err_empty_label);
                if (!seen.add(label)) return getString(R.string.err_dup_label, label);
            }
        }
        errorInAdvanced = false;
        if (seen.isEmpty()) return getString(R.string.err_no_fields);

        // keys that share one JSON object (the chat header) must differ
        errorInAdvanced = true;
        Set<String> chatKeys = new HashSet<>();
        for (String k : ExportSettings.STRUCT_KEYS) {
            String label = structLabels.get(k).getText().toString().trim();
            settings.structure.put(k, label);
            if (label.isEmpty()) return getString(R.string.err_empty_label);
            if (!k.equals("chats") && !chatKeys.add(label)) return getString(R.string.err_dup_label, label);
        }

        errorInAdvanced = false;
        if (settings.format.equals("csv") && chats.size() > 1 && !settings.perChat
                && !settings.enabled.get("thread_id") && !settings.enabled.get("address")) {
            return getString(R.string.err_csv_multi);
        }
        return null;
    }

    private void startExport() {
        String err = readForm();
        if (err != null) {
            if (errorInAdvanced) setAdvancedOpen(true);
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            return;
        }
        settings.save(this);

        int total = 0;
        for (ChatInfo c : chats) total += c.count;
        progress.setMax(Math.max(total, 1));
        progress.setProgress(0);
        progressText.setText(R.string.exporting);
        progressBox.setVisibility(View.VISIBLE);
        result.setText("");
        exportBtn.setEnabled(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        task = new ExportTask(this, chats, settings, new ExportTask.Listener() {
            @Override public void onProgress(String chat, int done, int tot) {
                runOnUiThread(() -> {
                    progress.setProgress(done);
                    progressText.setText(chat + "  (" + done + "/" + tot + ")");
                });
            }

            @Override public void onDone(List<String> files) {
                runOnUiThread(() -> {
                    finishUi();
                    result.setText(getResources().getQuantityString(R.plurals.done_n, files.size(), files.size(), ExportTask.FOLDER)
                            + "\n\n" + String.join("\n", files));
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    finishUi();
                    result.setText(getString(R.string.error_prefix, message));
                });
            }

            @Override public void onCancelled() {
                runOnUiThread(() -> {
                    finishUi();
                    result.setText(R.string.cancelled);
                });
            }
        });
        io.execute(task);
    }

    private void finishUi() {
        progressBox.setVisibility(View.GONE);
        exportBtn.setEnabled(true);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
