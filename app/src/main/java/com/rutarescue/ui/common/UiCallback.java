package com.rutarescue.ui.common;

import android.app.Activity;

import androidx.fragment.app.Fragment;

import com.rutarescue.repository.Callback;

import java.util.function.Consumer;

/**
 * A Callback that only touches the screen if the screen still exists ("lifecycle-safe UI
 * updates", DOCX section 10). Background work may finish after the user left the screen;
 * without this check the app could crash updating views that are gone.
 */
public final class UiCallback<T> implements Callback<T> {

    private final Fragment fragment;
    private final Activity activity;
    private final Consumer<T> onSuccess;
    private final Consumer<Exception> onError;

    private UiCallback(Fragment fragment, Activity activity, Consumer<T> onSuccess, Consumer<Exception> onError) {
        this.fragment = fragment;
        this.activity = activity;
        this.onSuccess = onSuccess;
        this.onError = onError;
    }

    public static <T> UiCallback<T> of(Fragment fragment, Consumer<T> onSuccess, Consumer<Exception> onError) {
        return new UiCallback<>(fragment, null, onSuccess, onError);
    }

    public static <T> UiCallback<T> of(Activity activity, Consumer<T> onSuccess, Consumer<Exception> onError) {
        return new UiCallback<>(null, activity, onSuccess, onError);
    }

    private boolean alive() {
        if (fragment != null) return fragment.isAdded() && fragment.getView() != null;
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    @Override
    public void onSuccess(T result) {
        if (alive()) onSuccess.accept(result);
    }

    @Override
    public void onError(Exception error) {
        if (alive()) onError.accept(error);
    }
}
