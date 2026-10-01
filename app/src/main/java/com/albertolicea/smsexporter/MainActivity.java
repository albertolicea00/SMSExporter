package com.albertolicea.smsexporter;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<ChatInfo> all = new ArrayList<>();
    private final List<ChatInfo> shown = new ArrayList<>();
    private final Set<Long> selected = new LinkedHashSet<>();
    private boolean loading;
    private boolean loaded;

    private ChatAdapter adapter;
    private TextView status;
    private Button grant;
    private Button next;
    private CheckBox selectAll;
    private EditText search;

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), r -> afterPermissionResult());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        grant = findViewById(R.id.grant);
        next = findViewById(R.id.next);
        selectAll = findViewById(R.id.select_all);
        search = findViewById(R.id.search);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter();
        list.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });
        selectAll.setOnClickListener(v -> {
            for (ChatInfo c : shown) {
                if (selectAll.isChecked()) selected.add(c.threadId);
                else selected.remove(c.threadId);
            }
            adapter.notifyDataSetChanged();
            updateBar();
        });
        next.setOnClickListener(v -> {
            long[] ids = new long[selected.size()];
            int i = 0;
            for (ChatInfo c : all) if (selected.contains(c.threadId)) ids[i++] = c.threadId;
            ChatCache.chats = new ArrayList<>(all);
            startActivity(new Intent(this, ExportActivity.class).putExtra(ExportActivity.EXTRA_THREADS, ids));
        });
        grant.setOnClickListener(v -> {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) || !askedOnce) {
                askedOnce = true;
                requestPerms();
            } else {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            }
        });
        updateBar();
    }

    private boolean askedOnce;

    @Override
    protected void onResume() {
        super.onResume();
        if (hasSms()) {
            if (!loaded && !loading) load();
        } else {
            showPermissionNeeded();
            if (!askedOnce) {
                askedOnce = true;
                requestPerms();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private boolean hasSms() {
        return checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPerms() {
        permLauncher.launch(new String[]{Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS});
    }

    private void afterPermissionResult() {
        if (hasSms()) load();
        else showPermissionNeeded();
    }

    private void showPermissionNeeded() {
        status.setText(R.string.perm_needed);
        status.setVisibility(View.VISIBLE);
        grant.setVisibility(View.VISIBLE);
    }

    private void load() {
        if (loading) return;
        loading = true;
        grant.setVisibility(View.GONE);
        status.setText(R.string.loading);
        status.setVisibility(View.VISIBLE);
        io.execute(() -> {
            List<ChatInfo> chats;
            try {
                chats = new SmsRepository(this).loadChats();
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    loading = false;
                    status.setText(getString(R.string.error_prefix, e.getMessage()));
                });
                return;
            }
            runOnUiThread(() -> {
                loading = false;
                loaded = true;
                all.clear();
                all.addAll(chats);
                status.setText(R.string.no_chats);
                status.setVisibility(chats.isEmpty() ? View.VISIBLE : View.GONE);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (ChatInfo c : all) {
            if (q.isEmpty()
                    || c.title().toLowerCase(Locale.getDefault()).contains(q)
                    || (c.address != null && c.address.toLowerCase(Locale.getDefault()).contains(q))) {
                shown.add(c);
            }
        }
        adapter.notifyDataSetChanged();
        updateBar();
    }

    private void updateBar() {
        next.setText(getString(R.string.next_n, selected.size()));
        next.setEnabled(!selected.isEmpty());
        boolean allShown = !shown.isEmpty();
        for (ChatInfo c : shown) {
            if (!selected.contains(c.threadId)) { allShown = false; break; }
        }
        selectAll.setChecked(allShown);
    }

    private final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

        final class VH extends RecyclerView.ViewHolder {
            final CheckBox check;
            final TextView title, snippet, count, date;

            VH(View v) {
                super(v);
                check = v.findViewById(R.id.check);
                title = v.findViewById(R.id.title);
                snippet = v.findViewById(R.id.snippet);
                count = v.findViewById(R.id.count);
                date = v.findViewById(R.id.date);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_chat, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ChatInfo c = shown.get(position);
            h.title.setText(c.title());
            h.snippet.setText(c.snippet == null ? "" : c.snippet.replace('\n', ' '));
            h.count.setText(getString(R.string.msgs_n, c.count));
            h.date.setText(DateUtils.formatDateTime(MainActivity.this, c.lastDate,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_YEAR));
            h.check.setChecked(selected.contains(c.threadId));
            h.itemView.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                long id = shown.get(pos).threadId;
                if (!selected.remove(id)) selected.add(id);
                notifyItemChanged(pos);
                updateBar();
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
