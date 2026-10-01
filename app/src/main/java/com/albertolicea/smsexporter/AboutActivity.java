package com.albertolicea.smsexporter;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    private static final String GITHUB_USER = "albertolicea00";
    private static final String REPO_URL = "https://github.com/" + GITHUB_USER + "/SMSExporter";

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
        findViewById(R.id.check_updates).setOnClickListener(v -> open(REPO_URL + "/releases"));
        findViewById(R.id.view_source).setOnClickListener(v -> open(REPO_URL));
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
