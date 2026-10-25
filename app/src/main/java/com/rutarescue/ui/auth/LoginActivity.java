package com.rutarescue.ui.auth;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.BuildConfig;
import com.rutarescue.R;
import com.rutarescue.exception.ProfileNotFoundException;
import com.rutarescue.exception.StaffPendingException;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.service.LigtasNotifier;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.resident.ligtas.LigtasStatusActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.shell.MainShellActivity;

/**
 * Launcher screen (controller). Restores an existing login, otherwise lets residents and city
 * staff sign in. The role is read from the database, never chosen on this screen. Staff who
 * signed up but are not approved yet are told who decides, and signed out again.
 */
public class LoginActivity extends BaseActivity {

    private static final String LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";
    private static final int REQUEST_LOCAL_NETWORK = 41;

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText email;
    private TextInputEditText password;
    private MaterialButton loginButton;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        applySystemBarInsets(findViewById(R.id.login_root));

        emailLayout = findViewById(R.id.login_email_layout);
        passwordLayout = findViewById(R.id.login_password_layout);
        email = findViewById(R.id.login_email);
        password = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        progress = findViewById(R.id.login_progress);
        MaterialButton register = findViewById(R.id.login_register);

        if (!container().isFirebaseReady()) {
            findViewById(R.id.login_firebase_warning).setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
            register.setEnabled(false);
            return;
        }

        loginButton.setOnClickListener(v -> signIn());
        password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            signIn(); // "Done" on the keyboard signs in, like the button
            return true;
        });
        findViewById(R.id.login_forgot).setOnClickListener(v -> resetPassword());
        // A test build talks to the test server on this computer: say so, so it is never mistaken for the real app.
        findViewById(R.id.login_test_server).setVisibility(BuildConfig.USE_FIREBASE_EMULATOR ? View.VISIBLE : View.GONE);
        register.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.login_staff).setOnClickListener(v -> startActivity(new Intent(this, StaffRegisterActivity.class)));
        askForLocalNetworkIfUsingEmulator();

        if (container().auth().isSignedIn()) {
            setBusy(true);
            container().session().restore(UiCallback.of(this, profile -> {
                if (profile != null) {
                    openShell();
                } else {
                    setBusy(false);
                }
            }, error -> {
                setBusy(false);
                if (error instanceof StaffPendingException) {
                    showStaffPending((StaffPendingException) error);
                } else {
                    if (error instanceof ProfileNotFoundException) container().signOut(); // do not stay half signed in
                    showError(error);
                }
            }));
        }
    }

    /** A staff sign-up that the city's LGU has not approved (or refused): explain, then sign out. */
    private void showStaffPending(StaffPendingException pending) {
        container().signOut();
        new MaterialAlertDialogBuilder(this)
                .setTitle(pending.getRequest().getStatus().getLabel())
                .setMessage(pending.getMessage())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * Android 17 blocks local-network addresses (like the emulator host 10.0.2.2) unless the
     * user allows it. Only needed when testing against the Firebase Local Emulator Suite;
     * the permission is declared in the debug manifest only.
     */
    private void askForLocalNetworkIfUsingEmulator() {
        if (!BuildConfig.USE_FIREBASE_EMULATOR || Build.VERSION.SDK_INT < 37) {
            return;
        }
        if (checkSelfPermission(LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{LOCAL_NETWORK_PERMISSION}, REQUEST_LOCAL_NETWORK);
        }
    }

    private void signIn() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        setBusy(true);
        container().auth().signIn(Dialogs.text(email), Dialogs.text(password),
                UiCallback.of(this, this::onSignedIn, error -> {
                    setBusy(false);
                    if (error instanceof ValidationException) {
                        String field = ((ValidationException) error).getField();
                        ("password".equals(field) ? passwordLayout : emailLayout).setError(error.getMessage());
                    } else if (error instanceof StaffPendingException) {
                        showStaffPending((StaffPendingException) error);
                    } else if (error instanceof ProfileNotFoundException) {
                        container().signOut(); // do not stay half signed in
                        showError(error);
                    } else {
                        showError(error);
                    }
                }));
    }

    /** "Forgot password?": Firebase e-mails a reset link to the address typed above. */
    private void resetPassword() {
        emailLayout.setError(null);
        String typed = Dialogs.text(email).strip();
        if (typed.isEmpty()) {
            emailLayout.setError("Type your e-mail first, then tap Forgot password.");
            return;
        }
        container().auth().sendPasswordReset(typed, UiCallback.of(this, ignored -> showMessage(
                "If " + typed + " has an account, a reset link is on its way. Check that inbox (and spam)."), error -> {
            if (error instanceof ValidationException) emailLayout.setError(error.getMessage());
            else showError(error);
        }));
    }

    private void onSignedIn(UserProfile profile) {
        container().session().setProfile(profile);
        openShell();
    }

    private void openShell() {
        Intent intent = new Intent(this, MainShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        // Opened from a Ligtas notification: show the resident's applications on top of the shell.
        if (getIntent().getBooleanExtra(LigtasNotifier.EXTRA_OPEN_LIGTAS, false) && !container().session().isStaff()) {
            startActivity(new Intent(this, LigtasStatusActivity.class));
        }
        finish();
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!busy);
    }
}
