package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.advisory.CityAlert;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.CityAlertRepository;
import com.rutarescue.repository.ListenerHandle;

/**
 * The city alert level (2026-10-22): read by every account of the city, set only by its LGU.
 * Each change is saved with an audit event; the Firestore rules check the LGU's role and city.
 */
public final class CityAlertService {

    private final CityAlertRepository repository;

    public CityAlertService(CityAlertRepository repository) {
        this.repository = repository;
    }

    /** Follows a city's level (CityAlert.none(city) until the LGU sets one, or when city is null). */
    public ListenerHandle listen(MetroManilaCity city, Callback<CityAlert> callback) {
        if (city == null) {
            callback.onSuccess(null);
            return ListenerHandle.NONE;
        }
        return repository.listen(city, callback);
    }

    /** LGU: sets its city's alert level with an optional note for residents. */
    public void set(StaffProfile lgu, CityAlert current, AlertLevel level, String note, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        CityAlert alert = current == null ? CityAlert.none(lgu.getJurisdiction()) : current;
        AlertLevel before = alert.getLevel();
        try {
            alert.set(lgu, level, note, now);
        } catch (ValidationException | IllegalStateException e) {
            callback.onError(e);
            return;
        }
        AuditEvent audit = AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "SET_ALERT_LEVEL", alert,
                before.getLabel() + " → " + level.getLabel() + (alert.getNote().isEmpty() ? "" : ": " + alert.getNote()), now);
        repository.save(alert, audit, callback);
    }
}
