package com.rutarescue.service;

import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.OccupancyLevel;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The numbers behind the "View report" charts, computed only from the city's real records:
 * its audit trail, its Ligtas applications and its evacuation centers (nothing is invented).
 *
 * Plain Java with no Android classes, so every number is checked by OperationsReportTest.
 * The object is immutable: build() does all the counting once.
 */
public final class OperationsReport {

    /** Days shown in the "Activity" column chart (today included). */
    public static final int ACTIVITY_DAYS = 14;
    /** Days counted in "Changes by kind" (the same window as the advisory lists). */
    public static final int WINDOW_DAYS = 30;
    /** Audit entries listed as text under the charts. */
    public static final int LATEST_COUNT = 10;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** The kinds of record a change can be about, read from AuditEvent.getEntityType(). */
    public enum Category {
        ADVISORIES("Advisories"),
        CENTERS("Evacuation centers"),
        ROADS("Road closures & bridges"),
        LIGTAS("Ligtas decisions"),
        DATA("Data imports"),
        STAFF("Staff accounts");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** Null for entity types that are not staff work (they still count in the daily activity). */
        static Category of(String entityType) {
            if (entityType == null) return null;
            switch (entityType) {
                case "advisory":
                case "cityAlert": return ADVISORIES;
                case "center": return CENTERS;
                case "closure":
                case "structure": return ROADS;
                case "ligtasApplication": return LIGTAS;
                case "datasetVersion": return DATA;
                case "staffRequest":
                case "staff": return STAFF;
                default: return null;
            }
        }
    }

    /** One column of the activity chart: a calendar day and how many changes were made on it. */
    public static final class DayCount {
        private final LocalDate date;
        private final int count;

        DayCount(LocalDate date, int count) {
            this.date = date;
            this.count = count;
        }

        public LocalDate getDate() { return date; }
        public int getCount() { return count; }
    }

    /** One bar of the occupancy chart. */
    public static final class CenterLoad {
        private final String name;
        private final int occupancy;
        private final int capacity;
        private final int percent;
        private final OccupancyLevel level;

        CenterLoad(String name, CenterCapacity capacity) {
            this.name = name;
            this.occupancy = capacity.getCurrentOccupancy();
            this.capacity = capacity.getTotalPersons();
            this.percent = capacity.getPercent();
            this.level = capacity.getLevel();
        }

        public String getName() { return name; }
        public int getOccupancy() { return occupancy; }
        public int getCapacity() { return capacity; }
        public int getPercent() { return percent; }
        public OccupancyLevel getLevel() { return level; }
    }

    private final List<DayCount> dailyActivity;
    private final Map<Category, Integer> changesByCategory;
    private final int changesInWindow;
    private final Map<ApplicationStatus, Integer> ligtasByStatus;
    private final int ligtasTotal;
    private final List<CenterLoad> centerLoads;
    private final int centersWithoutCapacity;
    private final List<AuditEvent> latestEvents;

    private OperationsReport(List<DayCount> dailyActivity, Map<Category, Integer> changesByCategory, int changesInWindow,
                             Map<ApplicationStatus, Integer> ligtasByStatus, int ligtasTotal,
                             List<CenterLoad> centerLoads, int centersWithoutCapacity, List<AuditEvent> latestEvents) {
        this.dailyActivity = Collections.unmodifiableList(dailyActivity);
        this.changesByCategory = Collections.unmodifiableMap(changesByCategory);
        this.changesInWindow = changesInWindow;
        this.ligtasByStatus = Collections.unmodifiableMap(ligtasByStatus);
        this.ligtasTotal = ligtasTotal;
        this.centerLoads = Collections.unmodifiableList(centerLoads);
        this.centersWithoutCapacity = centersWithoutCapacity;
        this.latestEvents = Collections.unmodifiableList(latestEvents);
    }

    /**
     * Counts everything for the report.
     *
     * @param zone the phone's time zone, so "today" starts at local midnight
     */
    public static OperationsReport build(List<AuditEvent> events, List<LigtasApplication> applications,
                                         List<EvacuationCenter> centers, long now, ZoneId zone) {
        // Activity per calendar day, oldest day first.
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        LocalDate firstDay = today.minusDays(ACTIVITY_DAYS - 1);
        int[] perDay = new int[ACTIVITY_DAYS];

        Map<Category, Integer> byCategory = new EnumMap<>(Category.class);
        for (Category category : Category.values()) byCategory.put(category, 0);
        int inWindow = 0;

        for (AuditEvent event : events) {
            LocalDate day = Instant.ofEpochMilli(event.getTimestamp()).atZone(zone).toLocalDate();
            if (!day.isBefore(firstDay) && !day.isAfter(today)) {
                perDay[(int) (day.toEpochDay() - firstDay.toEpochDay())]++;
            }
            if (now - event.getTimestamp() <= WINDOW_DAYS * DAY_MS) {
                inWindow++;
                Category category = Category.of(event.getEntityType());
                if (category != null) byCategory.put(category, byCategory.get(category) + 1);
            }
        }
        List<DayCount> daily = new ArrayList<>();
        for (int i = 0; i < ACTIVITY_DAYS; i++) daily.add(new DayCount(firstDay.plusDays(i), perDay[i]));

        // Ligtas applications by their current status.
        Map<ApplicationStatus, Integer> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) byStatus.put(status, 0);
        for (LigtasApplication application : applications) {
            byStatus.put(application.getStatus(), byStatus.get(application.getStatus()) + 1);
        }

        // Centers that have a capacity, fullest first; archived centers are left out.
        List<CenterLoad> loads = new ArrayList<>();
        int withoutCapacity = 0;
        for (EvacuationCenter center : centers) {
            if (center.isArchived()) continue;
            if (center.getCapacity().isSet()) {
                loads.add(new CenterLoad(center.getName(), center.getCapacity()));
            } else {
                withoutCapacity++;
            }
        }
        loads.sort((a, b) -> Integer.compare(b.getPercent(), a.getPercent()));

        // The newest audit entries for the text list.
        List<AuditEvent> sorted = new ArrayList<>(events);
        sorted.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        List<AuditEvent> latest = new ArrayList<>(sorted.subList(0, Math.min(LATEST_COUNT, sorted.size())));

        return new OperationsReport(daily, byCategory, inWindow, byStatus, applications.size(), loads,
                withoutCapacity, latest);
    }

    /**
     * One audit entry in plain words for the "Latest changes" list, e.g. "VERIFY_AND_PUBLISH" +
     * "center" becomes "Verified and published center". Unknown codes are spelled out as words.
     */
    public static String describe(AuditEvent event) {
        String action;
        switch (event.getAction()) {
            case "PUBLISH": action = "Published"; break;
            case "UPDATE": action = "Updated"; break;
            case "SAVE": action = "Saved"; break;
            case "ARCHIVE": action = "Archived"; break;
            case "VERIFY_AND_PUBLISH": action = "Verified and published"; break;
            case "UPDATE_CAPACITY": action = "Updated headcount of"; break;
            case "REPORT_CLOSURE": action = "Reported"; break;
            case "LIFT_CLOSURE": action = "Lifted"; break;
            case "SET_STRUCTURE_STATUS": action = "Set status of"; break;
            case "APPROVE": action = "Approved"; break;
            case "REJECT": action = "Rejected"; break;
            case "IMPORT_PUBLISH": action = "Imported"; break;
            case "PUBLISH_DATASET": action = "Published"; break;
            case "IMPORT": action = "Imported"; break;
            case "VERIFY": action = "Verified"; break;
            case "VERIFY_AND_SEND": action = "Verified and sent to the LGU:"; break;
            case "SEND_TO_LGU": action = "Sent to the LGU:"; break;
            case "SEND_BACK": action = "Sent back"; break;
            case "ASK_DOCUMENTS": action = "Asked for documents on"; break;
            // 2026-10-22: advisory approval, alert level, center assignment, priority IDs
            case "SUBMIT": action = "Sent for approval:"; break;
            case "EDIT_PENDING": action = "Fixed pending"; break;
            case "WITHDRAW": action = "Withdrew"; break;
            case "APPROVE_ADVISORY": action = "Approved and published"; break;
            case "DECLINE_ADVISORY": action = "Declined"; break;
            case "SET_ALERT_LEVEL": action = "Set the"; break;
            case "ASSIGN_CENTER": action = "Assigned a center to a"; break;
            case "CHECK_PRIORITY_ID": action = "Checked a"; break;
            default:
                String words = event.getAction().replace('_', ' ').toLowerCase(Locale.ROOT).strip();
                action = words.isEmpty() ? "Changed" : Character.toUpperCase(words.charAt(0)) + words.substring(1);
        }
        String thing;
        switch (event.getEntityType()) {
            case "advisory": thing = "advisory"; break;
            case "center": thing = "center"; break;
            case "closure": thing = "road closure"; break;
            case "structure": thing = "bridge or tunnel"; break;
            case "ligtasApplication": thing = "Ligtas application"; break;
            case "datasetVersion": thing = "dataset"; break;
            case "staffRequest": thing = "staff account"; break;
            case "cityAlert": thing = "city alert level"; break;
            case "staff": thing = "moderator"; break;
            case "priorityId": thing = "priority ID"; break;
            default: thing = event.getEntityType().isEmpty() ? "record" : event.getEntityType();
        }
        return action + " " + thing;
    }

    public List<DayCount> getDailyActivity() { return dailyActivity; }

    /** The highest daily count in the activity chart (0 when nothing happened). */
    public int getBusiestDayCount() {
        int max = 0;
        for (DayCount day : dailyActivity) max = Math.max(max, day.getCount());
        return max;
    }

    public Map<Category, Integer> getChangesByCategory() { return changesByCategory; }
    /** Every change in the last 30 days, including kinds without their own bar. */
    public int getChangesInWindow() { return changesInWindow; }
    public Map<ApplicationStatus, Integer> getLigtasByStatus() { return ligtasByStatus; }
    public int getLigtasTotal() { return ligtasTotal; }
    public List<CenterLoad> getCenterLoads() { return centerLoads; }
    public int getCentersWithoutCapacity() { return centersWithoutCapacity; }
    public List<AuditEvent> getLatestEvents() { return latestEvents; }
}
