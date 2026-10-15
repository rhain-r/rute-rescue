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
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.shell.MainShellActivity;

import java.util.HashMap;
import java.util.Map;

/** Resident self-registration (always creates a RESIDENT account). */
public class RegisterActivity extends BaseActivity {

    private final Map<String, TextInputLayout> fieldLayouts = new HashMap<>();
    private TextInputEditText name;
    private TextInputEditText email;
    private TextInputEditText password;
    private TextInputEditText confirm;
    private MaterialAutoCompleteTextView city;
    private MetroManilaCity selectedCity;
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

        registerButton.setOnClickListener(v -> register());
    }

    private void register() {
        for (TextInputLayout layout : fieldLayouts.values()) layout.setError(null);
        setBusy(true);
        container().auth().registerResident(Dialogs.text(name), Dialogs.text(email), Dialogs.text(password),
                Dialogs.text(confirm), selectedCity, UiCallback.of(this, profile -> {
                    container().session().setProfile(profile);
                    Intent intent = new Intent(this, MainShellActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
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
        registerButton.setEnabled(!busy);
    }
}
