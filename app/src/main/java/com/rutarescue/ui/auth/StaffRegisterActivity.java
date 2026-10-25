package com.rutarescue.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.Role;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;

import java.util.HashMap;
import java.util.Map;

/**
 * Staff sign-up (2026-10-20): the person chooses Moderator or LGU and the city they work for.
 * It creates a login and a locked request; the account works only after the LGU of that city
 * approves it (the first LGU of a city is approved by the administrator in the Firebase console).
 */
public class StaffRegisterActivity extends BaseActivity {

    private static final Role[] ROLES = {Role.MODERATOR, Role.LGU};

    private final Map<String, TextInputLayout> fieldLayouts = new HashMap<>();
    private TextInputEditText name, position, email, password, confirm;
    private Role selectedRole;
    private MetroManilaCity selectedCity;
    private MaterialButton sendButton;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_register);
        applySystemBarInsets(findViewById(R.id.staff_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.staff_sign_up_title);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());

        name = findViewById(R.id.staff_name);
        position = findViewById(R.id.staff_position);
        email = findViewById(R.id.staff_email);
        password = findViewById(R.id.staff_password);
        confirm = findViewById(R.id.staff_confirm);
        sendButton = findViewById(R.id.staff_button);
        progress = findViewById(R.id.staff_progress);
        fieldLayouts.put("role", findViewById(R.id.staff_role_layout));
        fieldLayouts.put("city", findViewById(R.id.staff_city_layout));
        fieldLayouts.put("fullName", findViewById(R.id.staff_name_layout));
        fieldLayouts.put("position", findViewById(R.id.staff_position_layout));
        fieldLayouts.put("email", findViewById(R.id.staff_email_layout));
        fieldLayouts.put("password", findViewById(R.id.staff_password_layout));
        fieldLayouts.put("confirmPassword", findViewById(R.id.staff_confirm_layout));

        MaterialAutoCompleteTextView role = findViewById(R.id.staff_role);
        String[] roleLabels = new String[ROLES.length];
        for (int i = 0; i < ROLES.length; i++) roleLabels[i] = ROLES[i].getLabel();
        role.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, roleLabels));
        role.setOnItemClickListener((parent, view, index, id) -> selectedRole = ROLES[index]);

        MaterialAutoCompleteTextView city = findViewById(R.id.staff_city);
        city.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, MetroManilaCity.values()));
        city.setOnItemClickListener((parent, view, index, id) ->
                selectedCity = (MetroManilaCity) parent.getItemAtPosition(index));

        sendButton.setOnClickListener(v -> send());
    }

    private void send() {
        for (TextInputLayout layout : fieldLayouts.values()) layout.setError(null);
        setBusy(true);
        container().auth().registerStaff(Dialogs.text(name), Dialogs.text(email), Dialogs.text(password),
                Dialogs.text(confirm), selectedRole, selectedCity, Dialogs.text(position),
                UiCallback.of(this, request -> {
                    setBusy(false);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Request sent")
                            .setMessage(request.waitingMessage())
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                            .show();
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

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!busy);
    }
}
