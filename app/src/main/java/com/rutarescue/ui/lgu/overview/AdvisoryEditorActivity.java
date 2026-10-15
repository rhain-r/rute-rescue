package com.rutarescue.ui.lgu.overview;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;

/** Create, edit, publish/unpublish or archive a community advisory (LGU only). */
public class AdvisoryEditorActivity extends BaseActivity {

    public static final String EXTRA_ID = "advisory_id";
    public static final String EXTRA_MESSAGE = "advisory_message";

    private LguProfile lgu;
    private Advisory advisory;
    private AlertLevel level = AlertLevel.ADVISORY;
    private TextInputEditText title;
    private TextInputEditText message;
    private MaterialAutoCompleteTextView levelField;
    private MaterialSwitch published;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advisory_editor);
        applySystemBarInsets(findViewById(R.id.advisory_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.advisory_editor);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        lgu = container().session().requireLgu();
        ((TextView) findViewById(R.id.advisory_city)).setText(lgu.getJurisdiction().getDisplayName() + " LGU");

        title = findViewById(R.id.advisory_title_input);
        message = findViewById(R.id.advisory_message_input);
        levelField = findViewById(R.id.advisory_level_input);
        published = findViewById(R.id.advisory_published);
        levelField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels()));
        levelField.setText(level.getLabel(), false);
        levelField.setOnItemClickListener((parent, view, position, id) -> level = AlertLevel.values()[position]);

        String prefill = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (prefill != null) message.setText(prefill);

        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id != null) {
            container().advisories().get(id, ui(this::fill));
        }
        findViewById(R.id.advisory_save).setOnClickListener(v -> save());
        findViewById(R.id.advisory_archive).setOnClickListener(v -> archive());
    }

    private static String[] labels() {
        AlertLevel[] levels = AlertLevel.values();
        String[] labels = new String[levels.length];
        for (int i = 0; i < levels.length; i++) labels[i] = levels[i].getLabel();
        return labels;
    }

    private void fill(Advisory existing) {
        advisory = existing;
        title.setText(existing.getTitle());
        message.setText(existing.getMessage());
        level = existing.getLevel();
        levelField.setText(level.getLabel(), false);
        published.setChecked(existing.isPublished());
        findViewById(R.id.advisory_archive).setVisibility(View.VISIBLE);
    }

    private void save() {
        TextInputLayout titleLayout = findViewById(R.id.advisory_title_layout);
        TextInputLayout messageLayout = findViewById(R.id.advisory_message_layout);
        titleLayout.setError(null);
        messageLayout.setError(null);
        UiCallback<Void> done = UiCallback.of(this, ignored -> {
            showMessage("Advisory saved");
            finish();
        }, error -> {
            if (error instanceof ValidationException) {
                ("title".equals(((ValidationException) error).getField()) ? titleLayout : messageLayout)
                        .setError(error.getMessage());
            } else {
                showError(error);
            }
        });
        if (advisory == null) {
            if (published.isChecked()) {
                container().advisories().publishNew(lgu, Dialogs.text(title), Dialogs.text(message), level, done);
            } else {
                showMessage("New advisories are published immediately. Switch on \"Published\" to continue.");
            }
        } else {
            container().advisories().update(lgu, advisory, Dialogs.text(title), Dialogs.text(message), level,
                    published.isChecked(), done);
        }
    }

    private void archive() {
        if (advisory == null) return;
        Dialogs.input(this, getString(R.string.archive), "Reason for archiving (kept in the audit trail)", "",
                reason -> container().advisories().archive(lgu, advisory, reason, ui(ignored -> {
                    showMessage("Advisory archived");
                    finish();
                })));
    }
}
