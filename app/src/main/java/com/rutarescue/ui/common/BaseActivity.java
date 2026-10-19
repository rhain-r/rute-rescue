package com.rutarescue.ui.common;

import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.rutarescue.AppContainer;
import com.rutarescue.RutaRescueApp;
import com.rutarescue.repository.Callback;

import java.util.function.Consumer;

/**
 * Common behaviour of every Activity (the "controllers" in our MVC design):
 * access to the AppContainer, edge-to-edge insets, and simple messages.
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected AppContainer container() {
        return RutaRescueApp.container(this);
    }

    /** Pads the root view so content is not hidden behind the status/navigation bars. */
    protected void applySystemBarInsets(View root) {
        // Keep the layout's own padding (e.g. the 16 dp screen sides of activity_list) and add the
        // status/navigation bars on top of it; replacing it put list content against the screen edge.
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    protected void showMessage(String message) {
        View root = findViewById(android.R.id.content);
        if (root != null) {
            Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    protected void showError(Exception error) {
        showMessage(UiMessages.describe(error));
    }

    /** Callback that runs on screen only while this Activity is alive; errors become messages. */
    protected <T> Callback<T> ui(Consumer<T> onSuccess) {
        return UiCallback.of(this, onSuccess, this::showError);
    }
}
