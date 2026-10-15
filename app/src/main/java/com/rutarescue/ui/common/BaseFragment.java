package com.rutarescue.ui.common;

import android.view.View;

import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.rutarescue.AppContainer;
import com.rutarescue.RutaRescueApp;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Common behaviour of every tab Fragment: container access, the signed-in profile,
 * lifecycle-safe callbacks, and automatic removal of database listeners when the view is
 * destroyed (so no listener keeps updating a screen that no longer exists).
 */
public abstract class BaseFragment extends Fragment {

    private final List<ListenerHandle> listeners = new ArrayList<>();

    protected AppContainer container() {
        return RutaRescueApp.container(requireContext());
    }

    protected UserProfile profile() {
        return container().session() == null ? null : container().session().getProfile();
    }

    protected void track(ListenerHandle handle) {
        if (handle != null) listeners.add(handle);
    }

    @Override
    public void onDestroyView() {
        for (ListenerHandle handle : listeners) handle.remove();
        listeners.clear();
        super.onDestroyView();
    }

    protected void showMessage(String message) {
        View view = getView();
        if (view != null) Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }

    protected void showError(Exception error) {
        showMessage(UiMessages.describe(error));
    }

    protected <T> Callback<T> ui(Consumer<T> onSuccess) {
        return UiCallback.of(this, onSuccess, this::showError);
    }

    /** Called by the shell when this tab becomes visible again. */
    public void onTabShown() {
    }
}
