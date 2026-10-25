package com.rutarescue.exception;

import com.rutarescue.model.user.StaffRequest;

/**
 * Sign-in found a staff sign-up that the city's LGU has not approved (or has rejected), so the
 * person cannot use the staff screens yet. The message says what is happening and who decides.
 */
public class StaffPendingException extends Exception {

    private final StaffRequest request;

    public StaffPendingException(StaffRequest request) {
        super(request.waitingMessage());
        this.request = request;
    }

    public StaffRequest getRequest() {
        return request;
    }
}
