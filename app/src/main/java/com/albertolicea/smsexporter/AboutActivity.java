package com.albertolicea.smsexporter;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AboutActivity extends AppCompatActivity {

    private static final String GITHUB_USER = "albertolicea00";
    private static final String REPO_URL = "https://github.com/" + GITHUB_USER + "/SMSExporter";

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private Button checkUpdatesBtn;
    private ProgressBar checkUpdatesProgress;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle(R.string.about);
        setContentView(R.layout.activity_about);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ((TextView) findViewById(R.id.version)).setText(
                    getString(R.string.version_fmt, pi.versionName, pi.getLongVersionCode()));
        } catch (PackageManager.NameNotFoundException ignored) {
            // cannot happen for our own package
        }

        TextView creator = findViewById(R.id.creator);
        creator.setText(getString(R.string.created_by, "@" + GITHUB_USER));
        creator.setOnClickListener(v -> open("https://github.com/" + GITHUB_USER));
        checkUpdatesBtn = findViewById(R.id.check_updates);
        checkUpdatesProgress = findViewById(R.id.check_updates_progress);
        checkUpdatesBtn.setOnClickListener(v -> checkForUpdate());
        findViewById(R.id.view_source).setOnClickListener(v -> open(REPO_URL));
    }

    private void checkForUpdate() {
        checkUpdatesBtn.setEnabled(false);
        checkUpdatesBtn.setText(R.string.checking_updates);
        checkUpdatesProgress.setVisibility(android.view.View.VISIBLE);
        io.execute(() -> {
            UpdateChecker.UpdateInfo info = UpdateChecker.fetchLatestIfNewer(BuildConfig.VERSION_NAME);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                checkUpdatesBtn.setEnabled(true);
                checkUpdatesBtn.setText(R.string.check_updates);
                checkUpdatesProgress.setVisibility(android.view.View.GONE);
                if (info != null) showUpdateDialog(info);
                else Toast.makeText(this, R.string.up_to_date, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showUpdateDialog(UpdateChecker.UpdateInfo info) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_available_title)
                .setMessage(getString(R.string.update_available_msg, info.version, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.download, (d, w) -> open(info.url))
                .setNegativeButton(R.string.later, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show();
        }
    }
}
