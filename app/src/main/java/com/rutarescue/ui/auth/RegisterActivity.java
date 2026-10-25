package com.rutarescue.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.priority.PriorityGroup;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.resident.priority.PriorityIdActivity;
import com.rutarescue.ui.shell.MainShellActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * Resident self-registration (always creates a RESIDENT account). Since 2026-10-22 a resident may
 * choose a priority group (senior citizen, person with disability, pregnant): the app then issues
 * a priority ID with a QR code and opens it right after sign-up, ready to save to the phone.
 */
public class RegisterActivity extends BaseActivity {

    private final Map<String, TextInputLayout> fieldLayouts = new HashMap<>();
    private TextInputEditText name;
    private TextInputEditText email;
    private TextInputEditText password;
    private TextInputEditText confirm;
    private MaterialAutoCompleteTextView city;
    private MetroManilaCity selectedCity;
    /** Null for "None". */
    private PriorityGroup selectedGroup;
    private MaterialButton registerButton;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        applySystemBarInsets(findViewById(R.id.register_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.create_resident_account);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());

        name = findViewById(R.id.register_name);
        email = findViewById(R.id.register_email);
        password = findViewById(R.id.register_password);
        confirm = findViewById(R.id.register_confirm);
        city = findViewById(R.id.register_city);
        registerButton = findViewById(R.id.register_button);
        progress = findViewById(R.id.register_progress);
        fieldLayouts.put("fullName", findViewById(R.id.register_name_layout));
        fieldLayouts.put("email", findViewById(R.id.register_email_layout));
        fieldLayouts.put("password", findViewById(R.id.register_password_layout));
        fieldLayouts.put("confirmPassword", findViewById(R.id.register_confirm_layout));
        fieldLayouts.put("city", findViewById(R.id.register_city_layout));

        city.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, MetroManilaCity.values()));
        city.setOnItemClickListener((parent, view, position, id) ->
                selectedCity = (MetroManilaCity) parent.getItemAtPosition(position));

        // "None" first, then the groups that get a priority ID.
        PriorityGroup[] groups = PriorityGroup.values();
        String[] groupLabels = new String[groups.length + 1];
        groupLabels[0] = getString(R.string.priority_group_none);
        for (int i = 0; i < groups.length; i++) groupLabels[i + 1] = groups[i].getLabel();
        MaterialAutoCompleteTextView priority = findViewById(R.id.register_priority);
        priority.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, groupLabels));
        priority.setText(groupLabels[0], false);
        priority.setOnItemClickListener((parent, view, position, id) ->
                selectedGroup = position == 0 ? null : groups[position - 1]);

        registerButton.setOnClickListener(v -> register());
    }

    private void register() {
        for (TextInputLayout layout : fieldLayouts.values()) layout.setError(null);
        setBusy(true);
        container().auth().registerResident(Dialogs.text(name), Dialogs.text(email), Dialogs.text(password),
                Dialogs.text(confirm), selectedCity, UiCallback.of(this, profile -> {
                    container().session().setProfile(profile);
                    if (selectedGroup != null && profile instanceof ResidentProfile) {
                        issuePriorityId((ResidentProfile) profile);
                    } else {
                        openApp(false);
                    }
                }, error -> {
                    setBusy(false);
                    if (error instanceof ValidationException
                            && fieldLayouts.containsKey(((ValidationException) error).getField())) {
                        fieldLayouts.get(((ValidationException) error).getField()).setError(error.getMessage());
                    } else {
                        showError(error);
                    }
                }));
    }

    /**
     * The account exists; now the priority ID. If it cannot be saved (e.g. no signal), the resident
     * still gets in and can get the ID later from Settings or My Record.
     */
    private void issuePriorityId(ResidentProfile resident) {
        container().priorityIds().issue(resident, selectedGroup, UiCallback.of(this, card -> openApp(true), error -> {
            showMessage("Your account is ready. Get your priority ID later in Settings.");
            openApp(false);
        }));
    }

    /** Opens the app; with withPriorityId the new ID is shown on top so it can be saved at once. */
    private void openApp(boolean withPriorityId) {
        Intent shell = new Intent(this, MainShellActivity.class);
        shell.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (withPriorityId) {
            startActivities(new Intent[]{shell, new Intent(this, PriorityIdActivity.class)});
        } else {
            startActivity(shell);
        }
        finish();
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!busy);
    }
}
