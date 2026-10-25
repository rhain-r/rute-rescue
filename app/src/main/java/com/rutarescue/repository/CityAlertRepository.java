package com.rutarescue.repository;

import com.rutarescue.model.advisory.CityAlert;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;

/** The alert level of each city (cityAlerts/{CITY}), set only by that city's LGU. */
public interface CityAlertRepository {

    /** Follows one city's alert level; delivers CityAlert.none(city) while the LGU has not set one. */
    ListenerHandle listen(MetroManilaCity city, Callback<CityAlert> callback);

    /** Saves the new level and its audit event together. */
    void save(CityAlert alert, AuditEvent audit, Callback<Void> callback);
}
