package com.rutarescue.ui.lgu.data;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.persistence.CenterCsvReader;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.SimpleListAdapter;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.validation.TextRules;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Center dataset import (DOCX section 3: "Import, validate, preview, version, and publish
 * approved hazard/center datasets"). Rows are validated with the same model rules as the
 * editor; only valid rows of the officer's own city are published, and the officer must
 * name how the data was verified.
 */
public class DatasetImportActivity extends BaseActivity {

    private static final String LEGACY_ASSET = "seed/centers_legacy_unverified.csv";

    private LguProfile lgu;
    private SimpleListAdapter<CenterCsvReader.Row> adapter;
    private List<CenterCsvReader.Row> rows = new ArrayList<>();
    private String fileLabel = "";

    private final ActivityResultLauncher<String[]> chooseFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::previewUri);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dataset_import);
        applySystemBarInsets(findViewById(R.id.import_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.dataset_import);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        lgu = container().session().requireLgu();

        RecyclerView list = findViewById(R.id.import_rows);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleListAdapter<>(R.layout.item_simple_row, this::bindRow);
        list.setAdapter(adapter);

        findViewById(R.id.import_legacy).setOnClickListener(v -> previewLegacy());
        findViewById(R.id.import_choose).setOnClickListener(v -> chooseFile.launch(new String[]{"text/*", "*/*"}));
        findViewById(R.id.import_publish).setOnClickListener(v -> publish());
    }

    private void previewLegacy() {
        try {
            InputStream stream = getAssets().open(LEGACY_ASSET);
            fileLabel = "legacy evacuation_centers.js list (" + LEGACY_ASSET + ")";
            container().datasets().previewCenterCsv(stream, ui(this::showPreview));
        } catch (IOException e) {
            showMessage("The bundled legacy list is missing: " + e.getMessage());
        }
    }

    private void previewUri(Uri uri) {
        if (uri == null) return;
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            if (stream == null) throw new IOException("The file could not be opened.");
            fileLabel = uri.getLastPathSegment() == null ? "chosen CSV" : uri.getLastPathSegment();
            container().datasets().previewCenterCsv(stream, ui(this::showPreview));
        } catch (IOException e) {
            showMessage(e.getMessage());
        }
    }

    private void showPreview(List<CenterCsvReader.Row> previewRows) {
        rows = previewRows;
        int valid = 0, mine = 0, invalid = 0;
        for (CenterCsvReader.Row row : rows) {
            if (!row.isValid()) invalid++;
            else {
                valid++;
                if (lgu.canManage(row.center.getCity())) mine++;
            }
        }
        ((TextView) findViewById(R.id.import_summary)).setText(rows.size() + " rows • " + valid + " valid • "
                + invalid + " with errors • " + mine + " in " + lgu.getJurisdiction().getDisplayName() + " can be published");
        adapter.submit(rows);
        findViewById(R.id.import_publish).setEnabled(mine > 0);
    }

    private void bindRow(View row, CenterCsvReader.Row item, int position) {
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(item.isValid() ? R.drawable.ic_center : R.drawable.ic_warning);
        TextView title = row.findViewById(R.id.row_title);
        TextView subtitle = row.findViewById(R.id.row_subtitle);
        TextView badge = row.findViewById(R.id.row_badge);
        badge.setVisibility(View.VISIBLE);
        if (!item.isValid()) {
            title.setText("Line " + item.lineNumber);
            subtitle.setText(item.error);
            Badges.style(badge, "Error", Badges.Tone.RED);
        } else {
            title.setText(item.center.getName());
            subtitle.setText(item.center.getCity().getDisplayName() + " • " + item.center.getLocation());
            if (lgu.canManage(item.center.getCity())) Badges.style(badge, "Will publish", Badges.Tone.GREEN);
            else Badges.style(badge, "Other city", Badges.Tone.GREY);
        }
    }

    private void publish() {
        TextInputLayout sourceLayout = findViewById(R.id.import_source_layout);
        sourceLayout.setError(null);
        String source = Dialogs.text((TextInputEditText) findViewById(R.id.import_source));
        try {
            // Same rule as DatasetService.publishCenters(), checked first so the error shows before the dialog.
            TextRules.requiredText("verificationSource", source);
        } catch (ValidationException invalid) {
            sourceLayout.setError(invalid.getMessage());
            return;
        }
        Dialogs.confirm(this, getString(R.string.publish_valid_rows),
                "Publish the valid " + lgu.getJurisdiction().getDisplayName() + " rows as verified centers? Each one is "
                        + "recorded in the audit trail with your verification note.", getString(R.string.publish),
                () -> {
                    findViewById(R.id.import_publish).setEnabled(false);
                    container().datasets().publishCenters(lgu, rows, source, fileLabel, UiCallback.of(this, count -> {
                        showMessage(count + " centers published");
                        finish();
                    }, error -> {
                        findViewById(R.id.import_publish).setEnabled(true);
                        if (error instanceof ValidationException
                                && "verificationSource".equals(((ValidationException) error).getField())) {
                            sourceLayout.setError(error.getMessage());
                        } else {
                            showError(error);
                        }
                    }));
                });
    }
}
