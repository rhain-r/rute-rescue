package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.AdvisoryRepository;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.validation.TextRules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Community advisories, each change with an audit event. Only the LGU publishes to residents.
 * Since 2026-10-22 a moderator's advisory is about their own center and waits for the LGU:
 * Pending -> Approved (published) or Declined (with a comment; locked, so a new one is written).
 */
public final class AdvisoryService {

    private final AdvisoryRepository repository;

    public AdvisoryService(AdvisoryRepository repository) {
        this.repository = repository;
    }

    /** The 30-day window used by every advisory list (see Advisory.isRecent). */
    public static final int RECENT_DAYS = Advisory.RECENT_DAYS;

    /** Advisories this person saved: Notifications keeps them even after the 30 days (set by AppContainer). */
    private Predicate<String> saved = id -> false;

    public void setSaved(Predicate<String> isSaved) {
        saved = isSaved == null ? id -> false : isSaved;
    }

    /** Recent updates (resident Home): published ones from the last 30 days, newest first. */
    public ListenerHandle listenPublished(Callback<SourcedData<List<Advisory>>> callback) {
        return repository.listenPublished(recent(callback, id -> false));
    }

    /**
     * Notifications (every account): the published updates of the last 30 days plus the ones this person
     * saved, whatever their age. The screen puts the saved ones on top (savedFirst).
     */
    public ListenerHandle listenForNotifications(Callback<SourcedData<List<Advisory>>> callback) {
        return repository.listenPublished(recent(callback, saved));
    }

    /** Staff lists of one city (drafts included), also limited to the last 30 days. */
    public ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<Advisory>>> callback) {
        return repository.listenForCity(city, recent(callback, id -> false));
    }

    /** Keeps the advisories of the last 30 days, in the same order. */
    public static List<Advisory> recentOnly(List<Advisory> advisories, long now) {
        return recentOnly(advisories, now, id -> false);
    }

    /** Keeps the advisories of the last 30 days plus the saved ones (whatever their age), in the same order. */
    public static List<Advisory> recentOnly(List<Advisory> advisories, long now, Predicate<String> keep) {
        List<Advisory> recent = new ArrayList<>();
        for (Advisory advisory : advisories) {
            if (advisory.isRecent(now) || keep.test(advisory.getId())) recent.add(advisory);
        }
        return recent;
    }

    /**
     * Saved updates first - the most recently saved on top - then all the others in their usual
     * newest-first order. savedAt gives the time an advisory was saved, or 0 when it is not saved.
     */
    public static List<Advisory> savedFirst(List<Advisory> newestFirst, ToLongFunction<String> savedAt) {
        List<Advisory> savedOnes = new ArrayList<>();
        List<Advisory> others = new ArrayList<>();
        for (Advisory advisory : newestFirst) {
            (savedAt.applyAsLong(advisory.getId()) > 0 ? savedOnes : others).add(advisory);
        }
        savedOnes.sort((a, b) -> Long.compare(savedAt.applyAsLong(b.getId()), savedAt.applyAsLong(a.getId())));
        savedOnes.addAll(others);
        return savedOnes;
    }

    /** Wraps a callback so it only ever receives recent (or saved) advisories. */
    private static Callback<SourcedData<List<Advisory>>> recent(Callback<SourcedData<List<Advisory>>> callback,
                                                                Predicate<String> keep) {
        return new Callback<SourcedData<List<Advisory>>>() {
            @Override
            public void onSuccess(SourcedData<List<Advisory>> data) {
                callback.onSuccess(new SourcedData<>(recentOnly(data.getData(), System.currentTimeMillis(), keep),
                        data.getFreshness(), data.getRetrievedAt(), data.getSource()));
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        };
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

    /**
     * The one announcement on Home and the LGU Overview (2026-10-25): the newest published one of
     * the last 7 days, the city's own first. With cityOnly another city's never counts (the LGU).
     * Null when there is none this week; Notifications keep all of the last 30 days.
     */
    public static Advisory latestThisWeek(List<Advisory> advisories, MetroManilaCity city, boolean cityOnly, long now) {
        List<Advisory> week = new ArrayList<>();
        for (Advisory advisory : advisories) {
            if (advisory.isPublished() && advisory.isThisWeek(now) && (!cityOnly || advisory.getCity() == city)) {
                week.add(advisory);
            }
        }
        return latestFor(week, city);
    }

    /**
     * LGU: writes and publishes its own city-wide advisory. It carries the city's current alert
     * level (cityLevel), which only the LGU sets (CityAlertService).
     */
    public void publishNew(StaffProfile lgu, String title, String message, AlertLevel cityLevel, Callback<Void> callback) {
        if (!lgu.canPublishToResidents()) {
            callback.onError(new IllegalStateException("Only the LGU publishes advisories. Send yours to the LGU for approval."));
            return;
        }
        long now = System.currentTimeMillis();
        try {
            Advisory advisory = new Advisory(repository.newId(), title, message, cityLevel, lgu.getJurisdiction(),
                    false, 0, lgu.getJurisdiction().getDisplayName() + " LGU");
            advisory.publish(lgu.getUid(), now);
            AuditEvent audit = AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "PUBLISH", advisory,
                    "Published advisory: " + advisory.getTitle(), now);
            repository.save(advisory, audit, callback);
        } catch (ValidationException | IllegalStateException e) {
            callback.onError(e);
        }
    }

    /**
     * Moderator: sends a new advisory about their own center to the LGU (advisory is null), or fixes
     * one that is still pending. A declined advisory can never be changed: write a new one.
     */
    public void submitForApproval(StaffProfile staff, Advisory pending, String title, String message,
                                  Callback<Void> callback) {
        if (!(staff instanceof ModeratorProfile) || !staff.canSubmitAdvisories()) {
            callback.onError(new IllegalStateException(staff instanceof ModeratorProfile
                    ? "Ask your LGU to assign you to an evacuation center first."
                    : "The LGU publishes its own advisories directly."));
            return;
        }
        ModeratorProfile moderator = (ModeratorProfile) staff;
        long now = System.currentTimeMillis();
        try {
            Advisory advisory;
            String action;
            if (pending == null) {
                advisory = Advisory.forApproval(repository.newId(), moderator, title, message, now);
                action = "SUBMIT";
            } else {
                pending.editPending(moderator, title, message, now);
                advisory = pending;
                action = "EDIT_PENDING";
            }
            repository.save(advisory, AuditEvent.create(moderator.getUid(), moderator.getJurisdiction().code(),
                    action, advisory, "Sent to the LGU for approval: " + advisory.getTitle(), now), callback);
        } catch (ValidationException | IllegalStateException e) {
            callback.onError(e);
        }
    }

    /** LGU: accepts a moderator's advisory; it is published at once with the city's alert level. */
    public void approve(StaffProfile lgu, Advisory advisory, AlertLevel cityLevel, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        try {
            advisory.approve(lgu, cityLevel, now);
        } catch (IllegalStateException e) {
            callback.onError(e);
            return;
        }
        repository.save(advisory, AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "APPROVE_ADVISORY",
                advisory, "Approved and published: " + advisory.getTitle(), now), callback);
    }

    /** LGU: declines a moderator's advisory with a comment the moderator reads. */
    public void decline(StaffProfile lgu, Advisory advisory, String comment, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        try {
            advisory.decline(lgu, comment, now);
        } catch (ValidationException | IllegalStateException e) {
            callback.onError(e);
            return;
        }
        repository.save(advisory, AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "DECLINE_ADVISORY",
                advisory, advisory.getLguNote(), now), callback);
    }

    /** LGU: edits one of its advisories and publishes or hides it (the alert level stays as published). */
    public void update(StaffProfile lgu, Advisory advisory, String title, String message, boolean published,
                       Callback<Void> callback) {
        if (!lgu.canPublishToResidents()) {
            callback.onError(new IllegalStateException("Only the LGU changes published advisories."));
            return;
        }
        if (!lgu.canManage(advisory.getCity())) {
            callback.onError(new IllegalStateException("You can only edit advisories of your own city."));
            return;
        }
        long now = System.currentTimeMillis();
        try {
            advisory.edit(title, message, advisory.getLevel(), lgu.getUid(), now);
            if (published && !advisory.isPublished()) advisory.publish(lgu.getUid(), now);
            if (!published && advisory.isPublished()) advisory.unpublish(lgu.getUid(), now);
            repository.save(advisory, AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "UPDATE", advisory,
                    published ? "Edited advisory" : "Hid advisory from residents", now), callback);
        } catch (ValidationException | IllegalStateException e) {
            callback.onError(e);
        }
    }

    /**
     * Archive: the LGU for any advisory of its city; a moderator only to withdraw their own center's
     * advisory while it is still pending.
     */
    public void archive(StaffProfile staff, Advisory advisory, String reason, Callback<Void> callback) {
        boolean withdrawing = staff instanceof ModeratorProfile && advisory.isAwaitingLgu()
                && ((ModeratorProfile) staff).getCenterId().equals(advisory.getCenterId());
        if (!staff.canManage(advisory.getCity()) || (!staff.canPublishToResidents() && !withdrawing)) {
            callback.onError(new IllegalStateException("Only the LGU archives advisories. A moderator can only "
                    + "withdraw their own advisory while it is pending."));
            return;
        }
        long now = System.currentTimeMillis();
        try {
            String checkedReason = TextRules.requiredText("reason", reason);
            if (advisory.isPublished()) advisory.unpublish(staff.getUid(), now);
            advisory.archive(staff.getUid(), now);
            repository.save(advisory, AuditEvent.create(staff.getUid(), staff.getJurisdiction().code(),
                    withdrawing ? "WITHDRAW" : "ARCHIVE", advisory, checkedReason, now), callback);
        } catch (ValidationException e) {
            callback.onError(e);
        }
    }
}
