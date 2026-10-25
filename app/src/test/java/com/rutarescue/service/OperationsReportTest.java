package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.OccupancyLevel;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.NeedCategory;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The numbers behind the "View report" charts. */
public class OperationsReportTest {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    /** 20 Oct 2026, 9:00 AM in Manila. */
    private static final long NOW = ZonedDateTime.of(2026, 10, 20, 9, 0, 0, 0, MANILA).toInstant().toEpochMilli();
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static int ids;

    private static AuditEvent event(String entityType, long at) {
        return new AuditEvent("e" + (ids++), "uid-1", "MANILA", "UPDATE", entityType, "r" + ids, "test", at);
    }

    private static LigtasApplication application(ApplicationStatus status) {
        return new LigtasApplication("app" + (ids++), "owner", MetroManilaCity.MANILA, "Ana", "m1", "Lola",
                NeedCategory.OTHER, "Needs help walking", "proof", "", NOW - DAY, status, "", "", 0, null);
    }

    private static EvacuationCenter center(String name, int capacity, int occupancy) throws ValidationException {
        EvacuationCenter center = new EvacuationCenter("c" + (ids++), name, "Tondo", MetroManilaCity.MANILA,
                new GeoPoint(14.60, 120.98), CenterStatus.OPEN, EnumSet.noneOf(AccessibilityFeature.class),
                VerificationInfo.verified("Field check", "uid-1", NOW), true, "");
        if (capacity > 0) center.restoreCapacity(new CenterCapacity(capacity, occupancy), null);
        return center;
    }

    @Test
    public void activityIsCountedPerCalendarDayForTwoWeeks() {
        List<AuditEvent> events = new ArrayList<>();
        events.add(event("advisory", NOW));                 // today
        events.add(event("center", NOW - 8L * 60 * 60 * 1000)); // 1:00 AM today
        events.add(event("center", NOW - 10L * 60 * 60 * 1000)); // 11:00 PM yesterday
        events.add(event("closure", NOW - 13 * DAY));      // first day of the chart
        events.add(event("closure", NOW - 14 * DAY));      // too old for the chart
        OperationsReport report = OperationsReport.build(events, new ArrayList<>(), new ArrayList<>(), NOW, MANILA);

        List<OperationsReport.DayCount> daily = report.getDailyActivity();
        assertEquals(OperationsReport.ACTIVITY_DAYS, daily.size());
        assertEquals(LocalDate.of(2026, 10, 7), daily.get(0).getDate());
        assertEquals(LocalDate.of(2026, 10, 20), daily.get(13).getDate());
        assertEquals(2, daily.get(13).getCount());
        assertEquals(1, daily.get(12).getCount());
        assertEquals(1, daily.get(0).getCount());
        assertEquals(2, report.getBusiestDayCount());
    }

    @Test
    public void changesAreGroupedByKindForThirtyDays() {
        List<AuditEvent> events = new ArrayList<>();
        events.add(event("advisory", NOW - DAY));
        events.add(event("advisory", NOW - 2 * DAY));
        events.add(event("center", NOW - 3 * DAY));
        events.add(event("structure", NOW - 3 * DAY));
        events.add(event("closure", NOW - 4 * DAY));
        events.add(event("ligtasApplication", NOW - 5 * DAY));
        events.add(event("advisory", NOW - 31 * DAY)); // outside the window
        OperationsReport report = OperationsReport.build(events, new ArrayList<>(), new ArrayList<>(), NOW, MANILA);

        assertEquals(2, report.getChangesByCategory().get(OperationsReport.Category.ADVISORIES).intValue());
        assertEquals(1, report.getChangesByCategory().get(OperationsReport.Category.CENTERS).intValue());
        assertEquals(2, report.getChangesByCategory().get(OperationsReport.Category.ROADS).intValue());
        assertEquals(1, report.getChangesByCategory().get(OperationsReport.Category.LIGTAS).intValue());
        assertEquals(0, report.getChangesByCategory().get(OperationsReport.Category.DATA).intValue());
        assertEquals(6, report.getChangesInWindow());
    }

    @Test
    public void ligtasApplicationsAreCountedByStatus() {
        List<LigtasApplication> applications = new ArrayList<>();
        applications.add(application(ApplicationStatus.PENDING));
        applications.add(application(ApplicationStatus.PENDING));
        applications.add(application(ApplicationStatus.APPROVED));
        applications.add(application(ApplicationStatus.REJECTED));
        OperationsReport report = OperationsReport.build(new ArrayList<>(), applications, new ArrayList<>(), NOW, MANILA);

        assertEquals(2, report.getLigtasByStatus().get(ApplicationStatus.PENDING).intValue());
        assertEquals(1, report.getLigtasByStatus().get(ApplicationStatus.APPROVED).intValue());
        assertEquals(1, report.getLigtasByStatus().get(ApplicationStatus.REJECTED).intValue());
        assertEquals(4, report.getLigtasTotal());
    }

    @Test
    public void centersWithACapacityAreListedFullestFirst() throws ValidationException {
        List<EvacuationCenter> centers = new ArrayList<>();
        centers.add(center("Half full school", 100, 50));
        centers.add(center("No capacity yet", 0, 0));
        centers.add(center("Nearly full gym", 200, 190));
        EvacuationCenter archived = center("Old court", 100, 100);
        archived.archive("uid-1", NOW);
        centers.add(archived);
        OperationsReport report = OperationsReport.build(new ArrayList<>(), new ArrayList<>(), centers, NOW, MANILA);

        List<OperationsReport.CenterLoad> loads = report.getCenterLoads();
        assertEquals(2, loads.size());
        assertEquals("Nearly full gym", loads.get(0).getName());
        assertEquals(95, loads.get(0).getPercent());
        assertEquals(OccupancyLevel.RED, loads.get(0).getLevel());
        assertEquals(50, loads.get(1).getPercent());
        assertEquals(OccupancyLevel.GREEN, loads.get(1).getLevel());
        assertEquals(1, report.getCentersWithoutCapacity());
    }

    @Test
    public void theLatestTenEventsAreKeptNewestFirst() {
        List<AuditEvent> events = new ArrayList<>();
        for (int i = 0; i < 15; i++) events.add(event("advisory", NOW - i * 1000L));
        events.add(0, event("center", NOW - 99_000L)); // out of order on purpose
        OperationsReport report = OperationsReport.build(events, new ArrayList<>(), new ArrayList<>(), NOW, MANILA);
        assertEquals(10, report.getLatestEvents().size());
        assertEquals(NOW, report.getLatestEvents().get(0).getTimestamp());
        assertTrue(report.getLatestEvents().get(9).getTimestamp() > NOW - 99_000L);
    }
    @Test
    public void auditCodesAreDescribedInPlainWords() {
        assertEquals("Verified and published center", OperationsReport.describe(
                new AuditEvent("x1", "u", "MANILA", "VERIFY_AND_PUBLISH", "center", "r", "", NOW)));
        assertEquals("Approved Ligtas application", OperationsReport.describe(
                new AuditEvent("x2", "u", "MANILA", "APPROVE", "ligtasApplication", "r", "", NOW)));
        assertEquals("Set status of bridge or tunnel", OperationsReport.describe(
                new AuditEvent("x3", "u", "MANILA", "SET_STRUCTURE_STATUS", "structure", "r", "", NOW)));
        assertEquals("Sent to the LGU: advisory", OperationsReport.describe(
                new AuditEvent("x5", "u", "MANILA", "SEND_TO_LGU", "advisory", "r", "", NOW)));
        assertEquals("Asked for documents on Ligtas application", OperationsReport.describe(
                new AuditEvent("x6", "u", "MANILA", "ASK_DOCUMENTS", "ligtasApplication", "r", "", NOW)));
        assertEquals("Something new road closure", OperationsReport.describe(
                new AuditEvent("x4", "u", "MANILA", "SOMETHING_NEW", "closure", "r", "", NOW)));
    }
}
