package com.rutarescue.ui.resident.record;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.rutarescue.R;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.persistence.ImportReport;
import com.rutarescue.service.FamilyService;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lab 5-7 compatibility screen (DOCX section 9, acceptance journey 8):
 *  - shows family_records.txt and system.dat as stored on the phone (re-read from disk),
 *  - imports a family_records.txt with per-line warnings for malformed/duplicate rows,
 *  - exports the resident's record in the same pipe-delimited UTF-8 format.
 * The Android file picker (Storage Access Framework) supplies the input/output streams.
 */
public class LegacyDataActivity extends BaseActivity {

    private UserProfile profile;
    private FamilyRecord current;

    private final ActivityResultLauncher<String[]> openDocument = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importFrom);
    private final ActivityResultLauncher<String> createDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), this::exportTo);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legacy_data);
        applySystemBarInsets(findViewById(R.id.legacy_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.legacy_title);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        profile = container().session().getProfile();

        findViewById(R.id.legacy_reload).setOnClickListener(v -> reloadFiles());
        findViewById(R.id.legacy_import).setOnClickListener(v -> openDocument.launch(new String[]{"text/plain", "*/*"}));
        findViewById(R.id.legacy_export).setOnClickListener(v -> {
            if (current == null) {
                showMessage("Create your family record first.");
            } else {
                createDocument.launch("family_records.txt");
            }
        });
        // Read the files after the record loads, because loading updates family_records.txt.
        container().family().load(profile.getUid(), UiCallback.of(this, data -> {
            current = data.getData();
            reloadFiles();
        }, error -> {
            showError(error);
            reloadFiles();
        }));
    }

    private void reloadFiles() {
        container().family().readLocalFiles(ui(this::showStatus));
    }

    private void showStatus(FamilyService.LocalFilesStatus status) {
        StringBuilder text = new StringBuilder();
        text.append("family_records.txt (text, UTF-8, pipe-delimited)\n")
                .append(status.familyFilePath).append('\n')
                .append(status.textReport.summary()).append('\n');
        for (FamilyRecord record : status.textReport.getRecords()) {
            text.append("  ").append(record.toFileString()).append('\n');
        }
        for (String warning : status.textReport.getWarnings()) text.append("  ! ").append(warning).append('\n');
        text.append("\nsystem.dat (binary, DataOutputStream)\n")
                .append(status.systemFilePath).append('\n')
                .append(status.systemState).append('\n');
        if (!status.systemMessage.isEmpty()) text.append("  ! ").append(status.systemMessage).append('\n');
        if (!status.lastMirrorError.isEmpty()) text.append("\nLast save problem: ").append(status.lastMirrorError);
        ((TextView) findViewById(R.id.legacy_status)).setText(text);
    }

    private void importFrom(Uri uri) {
        if (uri == null) return;
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            if (stream == null) throw new FileNotFoundException("The chosen file could not be opened.");
            container().family().importLegacy(stream, ui(this::showReport));
        } catch (FileNotFoundException e) {
            showMessage(e.getMessage());
        }
    }

    private void showReport(ImportReport report) {
        findViewById(R.id.legacy_report_title).setVisibility(View.VISIBLE);
        TextView reportView = findViewById(R.id.legacy_report);
        reportView.setVisibility(View.VISIBLE);
        StringBuilder text = new StringBuilder(report.summary());
        for (String warning : report.getWarnings()) text.append("\n• ").append(warning);
        reportView.setText(text);

        LinearLayout list = findViewById(R.id.legacy_records);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (FamilyRecord record : report.getRecords()) {
            View row = inflater.inflate(R.layout.item_simple_row, list, false);
            ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(R.drawable.ic_family);
            ((TextView) row.findViewById(R.id.row_title)).setText(record.getRecordId() + " • " + record.getHeadName());
            ((TextView) row.findViewById(R.id.row_subtitle)).setText(record.getAddress() + " • size "
                    + record.getFamilySize() + " • " + record.getPreferredCenter());
            ImageButton use = row.findViewById(R.id.row_action);
            use.setImageResource(R.drawable.ic_check_circle);
            use.setContentDescription(getString(R.string.use_this_record));
            use.setVisibility(View.VISIBLE);
            use.setOnClickListener(v -> Dialogs.confirm(this, getString(R.string.use_this_record),
                    "Replace your current record with " + record.getRecordId() + "? Member details must then be added "
                            + "(the legacy file only stores the family size).", getString(R.string.use_this_record),
                    () -> adopt(record)));
            list.addView(row);
        }
    }

    private void adopt(FamilyRecord imported) {
        FamilyRecord adopted = container().family().adoptImported(imported, profile);
        container().family().save(adopted, ui(ignored -> {
            current = adopted;
            showMessage("Imported record saved. Open My Record > Edit to add member details.");
            reloadFiles();
        }));
    }

    private void exportTo(Uri uri) {
        if (uri == null || current == null) return;
        try {
            OutputStream stream = getContentResolver().openOutputStream(uri);
            if (stream == null) throw new FileNotFoundException("The chosen file could not be created.");
            container().family().exportLegacy(stream, current, ui(ignored -> showMessage("Exported " + current.getRecordId())));
        } catch (FileNotFoundException e) {
            showMessage(e.getMessage());
        }
    }
}
