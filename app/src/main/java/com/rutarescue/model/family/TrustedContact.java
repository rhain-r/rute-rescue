package com.rutarescue.model.family;

import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.validation.PhoneNumber;
import com.rutarescue.validation.RecordValidator;

/**
 * Someone to contact in an emergency. The phone number is optional only for contacts
 * imported from Lab 5 files whose emergency-contact column had no valid mobile number.
 */
public final class TrustedContact {

    private final String name;
    private final String phone;

    public TrustedContact(String name, String phone) throws InvalidFamilyRecordException {
        this.name = RecordValidator.optionalText("contactName", name).isEmpty()
                ? "Emergency contact"
                : RecordValidator.optionalText("contactName", name);
        if (phone == null || phone.isBlank()) {
            this.phone = "";
        } else {
            this.phone = RecordValidator.contactNumber(phone);
        }
    }

    public String getName() {
        return name;
    }

    /** Normalized 11-digit mobile number, or "" when unknown. */
    public String getPhone() {
        return phone;
    }

    public String describe() {
        return phone.isEmpty() ? name : name + " • " + PhoneNumber.format(phone);
    }
}
