package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.repository.AdvisoryRepository;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.validation.TextRules;

import java.util.List;

/** LGU advisories: publish, edit and archive, each with an audit event. */
public final class AdvisoryService {

    private final AdvisoryRepository repository;

    public AdvisoryService(AdvisoryRepository repository) {
        this.repository = repository;
    }

    public ListenerHandle listenPublished(Callback<SourcedData<List<Advisory>>> callback) {
        return repository.listenPublished(callback);
    }

    public ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<Advisory>>> callback) {
        return repository.listenForCity(city, callback);
    }

    public void get(String id, Callback<Advisory> callback) {
        repository.get(id, callback);
    }

    /** Picks the newest advisory for the resident's city, or the newest overall. */
    public static Advisory latestFor(List<Advisory> advisories, MetroManilaCity city) {
        Advisory newest = null;
        for (Advisory advisory : advisories) {
            if (city != null && advisory.getCity() == city) return advisory; // list is newest first
            if (newest == null) newest = advisory;
        }
        return newest;
    }

    public void publishNew(LguProfile lgu, String title, String message, AlertLevel level, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        try {
            Advisory advisory = new Advisory(repository.newId(), title, message, level, lgu.getJurisdiction(),
                    false, 0, lgu.getJurisdiction().getDisplayName() + " LGU");
            advisory.publish(lgu.getUid(), now);
            AuditEvent audit = AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "PUBLISH", advisory,
                    "Published advisory: " + advisory.getTitle(), now);
            repository.save(advisory, audit, callback);
        } catch (ValidationException e) {
            callback.onError(e);
        }
    }

    public void update(LguProfile lgu, Advisory advisory, String title, String message, AlertLevel level,
                       boolean published, Callback<Void> callback) {
        if (!lgu.canManage(advisory.getCity())) {
            callback.onError(new IllegalStateException("You can only edit advisories of your own city."));
            return;
        }
        long now = System.currentTimeMillis();
        try {
            advisory.edit(title, message, level, lgu.getUid(), now);
            if (published && !advisory.isPublished()) advisory.publish(lgu.getUid(), now);
            if (!published && advisory.isPublished()) advisory.unpublish(lgu.getUid(), now);
            AuditEvent audit = AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "UPDATE", advisory,
                    "Edited advisory", now);
            repository.save(advisory, audit, callback);
        } catch (ValidationException e) {
            callback.onError(e);
        }
    }

    public void archive(LguProfile lgu, Advisory advisory, String reason, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        try {
            String checkedReason = TextRules.requiredText("reason", reason);
            advisory.unpublish(lgu.getUid(), now);
            advisory.archive(lgu.getUid(), now);
            repository.save(advisory, AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "ARCHIVE",
                    advisory, checkedReason, now), callback);
        } catch (ValidationException e) {
            callback.onError(e);
        }
    }
}
