package com.rutarescue.exception;

/**
 * The login worked, but the account has no RutaRescue profile: no resident profile, no staff
 * record and no staff sign-up request. This happens when a sign-up was interrupted after the
 * login was created (for example the connection dropped). Staff sign-up with the same e-mail and
 * password finishes it (AuthService.registerStaff).
 */
public class ProfileNotFoundException extends Exception {

    public ProfileNotFoundException() {
        super("This account has no RutaRescue profile yet. If your staff sign-up was interrupted, open Staff sign-up "
                + "and send it again with the same e-mail and password.");
    }
}
