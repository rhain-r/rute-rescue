package com.rutarescue.ui.common;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.exception.ValidationException;

/**
 * Turns exceptions into short, user-friendly messages (exception handling in the UI layer).
 * Specific types are checked first, so each problem gets its own explanation.
 */
public final class UiMessages {

    private UiMessages() {
    }

    public static String describe(Throwable error) {
        if (error == null) return "Something went wrong.";
        if (error instanceof ValidationException) return error.getMessage();
        if (error instanceof OfflineException) return error.getMessage();
        if (error instanceof ProviderException) return error.getMessage();
        if (error instanceof FirebaseNetworkException) {
            return "No connection to the server. Your changes are kept on this phone and will sync later.";
        }
        if (error instanceof FirebaseAuthInvalidCredentialsException || error instanceof FirebaseAuthInvalidUserException) {
            return "Incorrect e-mail or password.";
        }
        if (error instanceof FirebaseAuthUserCollisionException) {
            return "An account with this e-mail already exists. Sign in instead.";
        }
        if (error instanceof FirebaseAuthException) {
            return "Sign-in problem: " + error.getMessage();
        }
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) error).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Your account is not allowed to do this (checked by the database security rules).";
            }
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "The server is unavailable. Showing what is saved on this phone.";
            }
            return "Database error: " + code.name().toLowerCase().replace('_', ' ') + ".";
        }
        if (error instanceof SecurityException) return error.getMessage();
        if (error instanceof IllegalStateException || error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        return "Something went wrong. Please try again.";
    }
}
