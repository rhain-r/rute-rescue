package com.rutarescue.ui.lgu.finalize;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rutarescue.R;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CapacityChange;
import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.hazard.PointHazard;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.CenterSnapshot;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.HazardQueryService;
import com.rutarescue.service.OperationsReport;
import com.rutarescue.ui.common.AlertStrip;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.chart.BarListView;
import com.rutarescue.ui.common.chart.ChartEntry;
import com.rutarescue.ui.common.chart.ColumnChartView;
import com.rutarescue.ui.common.chart.DonutChartView;
import com.rutarescue.ui.lgu.data.CenterCapacityActivity;
import com.rutarescue.ui.shell.MainShellActivity;
import com.rutarescue.util.Formats;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LGU: one moderator and their center at a glance (2026-10-22), opened from Finalize > Moderators
 * and their centers. Controller for activity_moderator_dashboard.xml:
 *  - the moderator, with "Change center";
 *  - center status: a ring chart of people inside vs free places, the headcount, the last update
 *    and the facilities;
 *  - flood status: the city alert level and the NOAH hazard at the center's pin (5/25/100-year);
 *  - the center's advisories (pending, approved, declined);
 *  - the moderator's activity per day and their work by kind, from the audit trail.
 * Everything follows the database live, so a new headcount shows at once.
 */
public class ModeratorDashboardActivity extends BaseActivity {

    private static final String EXTRA_UID = "moderator_uid";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private StaffProfile lgu;
    private ModeratorProfile moderator;
    private EvacuationCenter center;
    private final List<EvacuationCenter> cityCenters = new ArrayList<>();
    private List<AuditEvent> cityEvents = new ArrayList<>();
    private List<Advisory> cityAdvisories = new ArrayList<>();
    private final List<ListenerHandle> listeners = new ArrayList<>();
    private ListenerHandle centerListener = ListenerHandle.NONE;
    private HazardQueryService hazardQueries;
    /** The center whose flood status is shown (looked up once per center). */
    private String floodCheckedFor = "";

    public static Intent intent(Context context, String moderatorUid) {
        return new Intent(context, ModeratorDashboardActivity.class).putExtra(EXTRA_UID, moderatorUid);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moderator_dashboard);
        applySystemBarInsets(findViewById(R.id.dash_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.dash_title);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        lgu = container().session().requireStaff();
        hazardQueries = new HazardQueryService(container().hazards, container().executors);
        String uid = getIntent().getStringExtra(EXTRA_UID);

        ((DonutChartView) findViewById(R.id.dash_capacity_chart)).setTotalCaption("places");
        ((DonutChartView) findViewById(R.id.dash_advisory_chart)).setTotalCaption("advisories");
        ((BarListView) findViewById(R.id.dash_flood)).setFixedMax(HazardClass.HIGH.getCode());
        findViewById(R.id.dash_change_center).setOnClickListener(v -> pickCenter());
        findViewById(R.id.dash_open_center).setOnClickListener(v -> {
            if (center != null) startActivity(new Intent(this, CenterCapacityActivity.class)
                    .putExtra(CenterCapacityActivity.EXTRA_ID, center.getId()));
        });
        findViewById(R.id.dash_show_map).setOnClickListener(v -> {
            if (center != null) startActivity(MainShellActivity.staffMapIntent(this, center));
        });

        listeners.add(container().staff().listenStaff(lgu, ui(members -> {
            for (StaffProfile member : members) {
                if (member.getUid().equals(uid) && member instanceof ModeratorProfile) {
                    showModerator((ModeratorProfile) member);
                    return;
                }
            }
            showMessage("This moderator is no longer on the city's staff list.");
            finish();
        })));
        listeners.add(container().centers().listenForManager(lgu, ui(data -> {
            cityCenters.clear();
            for (EvacuationCenter each : data.getData()) {
                if (!each.isArchived()) cityCenters.add(each);
            }
            cityCenters.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        })));
        listeners.add(container().audit().listenForCity(lgu.getJurisdiction(), ui(events -> {
            cityEvents = events;
            showActivity();
        })));
        listeners.add(container().advisories().listenForCity(lgu.getJurisdiction(), ui(data -> {
            cityAdvisories = data.getData();
            showAdvisories();
        })));
        listeners.add(container().alerts().listen(lgu.getJurisdiction(), ui(alert ->
                AlertStrip.bind(findViewById(R.id.alert_strip), lgu.getJurisdiction(), alert, null))));
    }

    @Override
    protected void onDestroy() {
        for (ListenerHandle listener : listeners) listener.remove();
        centerListener.remove();
        hazardQueries.cancel();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ the moderator

    private void showModerator(ModeratorProfile member) {
        boolean centerChanged = moderator == null || !moderator.getCenterId().equals(member.getCenterId());
        moderator = member;
        ((TextView) findViewById(R.id.dash_name)).setText(member.getDisplayName());
        ((TextView) findViewById(R.id.dash_role)).setText(member.getPosition() + " • Moderator • "
                + member.getJurisdiction().getDisplayName() + "\n" + member.getEmail());
        ((TextView) findViewById(R.id.dash_center_line)).setText(member.hasAssignedCenter()
                ? "Runs " + member.getCenterName() : getString(R.string.dash_no_center));
        findViewById(R.id.dash_center_section).setVisibility(member.hasAssignedCenter() ? View.VISIBLE : View.GONE);
        if (centerChanged) followCenter();
        showActivity();
        showAdvisories();
    }

    private void followCenter() {
        centerListener.remove();
        center = null;
        if (!moderator.hasAssignedCenter()) return;
        centerListener = container().centers().listen(moderator.getCenterId(), ui(this::showCenter));
    }

    /** LGU: give the moderator another of the city's centers. */
    private void pickCenter() {
        if (moderator == null) return;
        if (cityCenters.isEmpty()) {
            showMessage("Add the evacuation center first (Data > Manage > Add a center).");
            return;
        }
        String[] names = new String[cityCenters.size()];
        for (int i = 0; i < names.length; i++) names[i] = cityCenters.get(i).getName();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Center for " + moderator.getDisplayName())
                .setItems(names, (dialog, which) -> {
                    EvacuationCenter chosen = cityCenters.get(which);
                    container().staff().assignCenter(lgu, moderator, chosen, ui(ignored ->
                            showMessage(moderator.getDisplayName() + " now runs " + chosen.getName())));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ center status

    private void showCenter(CenterSnapshot snapshot) {
        center = snapshot.getCenter();
        TextView status = findViewById(R.id.dash_status);
        Badges.center(status, center.getStatus());
        ((TextView) findViewById(R.id.dash_center_name)).setText(center.getName());
        String barangay = center.getBarangay().isEmpty() ? "" : center.getBarangay() + " · ";
        ((TextView) findViewById(R.id.dash_center_place)).setText(barangay + center.getAddress() + " · "
                + (center.isPublished() ? "shown to residents" : "not published"));

        CenterCapacity capacity = center.getCapacity();
        DonutChartView ring = findViewById(R.id.dash_capacity_chart);
        TextView text = findViewById(R.id.dash_capacity_text);
        List<ChartEntry> slices = new ArrayList<>();
        if (capacity.isSet()) {
            int inside = capacity.getCurrentOccupancy();
            int total = capacity.getTotalPersons();
            int over = Math.max(0, inside - total);
            int insideColor;
            switch (capacity.getLevel()) {
                case RED: insideColor = R.color.hazard_high; break;
                case AMBER: insideColor = R.color.rr_gauge_amber; break;
                default: insideColor = R.color.rr_green;
            }
            slices.add(new ChartEntry("People inside", inside - over, String.valueOf(inside - over), insideColor));
            if (over > 0) slices.add(new ChartEntry("Over capacity", over, String.valueOf(over), R.color.rr_red));
            slices.add(new ChartEntry("Free places", Math.max(0, total - inside), String.valueOf(Math.max(0, total - inside)),
                    R.color.rr_divider));
            text.setText(capacity.summary() + " • limit " + capacity.getLimit() + " (120%)");
        } else {
            text.setText(R.string.capacity_not_set);
        }
        ring.setEmptyText("No capacity set yet: the moderator sets it on the center page");
        ring.setEntries(slices);

        CapacityChange change = center.getLastCapacityChange();
        long now = System.currentTimeMillis();
        ((TextView) findViewById(R.id.dash_last_update)).setText(snapshot.isPendingSync() ? getString(R.string.waiting_to_upload)
                : change == null ? getString(capacity.isSet() ? R.string.no_capacity_change_yet : R.string.no_capacity_change)
                : "Last headcount: " + change.describe() + " • " + Formats.relative(change.getChangedAt(), now)
                + (change.getChangedAt() > 0 && now - change.getChangedAt() > DAY_MS ? " (over a day ago)" : ""));
        List<String> features = new ArrayList<>();
        for (AccessibilityFeature feature : center.getFeatures()) features.add(feature.getLabel());
        ((TextView) findViewById(R.id.dash_facilities)).setText("Facilities: "
                + (features.isEmpty() ? "none ticked yet" : String.join(", ", features)));

        if (!floodCheckedFor.equals(center.getId())) {
            floodCheckedFor = center.getId();
            hazardQueries.inspectAll(center.getLocation(), new Callback<List<PointHazard>>() {
                @Override
                public void onSuccess(List<PointHazard> answers) {
                    showFlood(answers);
                }

                @Override
                public void onError(Exception error) {
                    ((TextView) findViewById(R.id.dash_flood_note)).setText("The prepared flood maps could not be read.");
                }
            });
        }
    }

    /** One bar per scenario: none, low, medium or high hazard mapped at the center's pin. */
    private void showFlood(List<PointHazard> answers) {
        List<ChartEntry> bars = new ArrayList<>();
        HazardClass worst = HazardClass.NONE;
        boolean outside = false;
        for (PointHazard answer : answers) {
            String scenario = answer.getScenario().getLabel();
            if (!answer.isCovered()) {
                outside = true;
                bars.add(new ChartEntry(scenario, 0, "outside the prepared maps", R.color.rr_divider));
                continue;
            }
            HazardClass hazard = answer.getHazardClass();
            worst = HazardClass.max(worst, hazard);
            bars.add(new ChartEntry(scenario, hazard.getCode(), hazard == HazardClass.NONE ? hazard.getLabel()
                    : hazard.getLabel() + " (" + hazard.getDepthRange() + ")", hazardColor(hazard)));
        }
        BarListView chart = findViewById(R.id.dash_flood);
        chart.setEntries(bars);
        String note;
        if (outside && worst == HazardClass.NONE) {
            note = "The pin is outside the prepared NOAH flood maps.";
        } else if (worst == HazardClass.NONE) {
            note = "No flood hazard is mapped at this center in any scenario (this is not a guarantee).";
        } else {
            note = "Worst case: " + worst.getLabel() + " hazard (" + worst.getDepthRange()
                    + "). Prepared scenarios, not live flooding.";
        }
        ((TextView) findViewById(R.id.dash_flood_note)).setText(note);
    }

    private static int hazardColor(HazardClass hazard) {
        switch (hazard) {
            case HIGH: return R.color.hazard_high;
            case MEDIUM: return R.color.hazard_medium;
            case LOW: return R.color.hazard_low;
            default: return R.color.rr_divider;
        }
    }

    // ------------------------------------------------------------------ advisories

    private void showAdvisories() {
        if (moderator == null || !moderator.hasAssignedCenter()) return;
        int pending = 0;
        int approved = 0;
        int declined = 0;
        for (Advisory advisory : cityAdvisories) {
            if (!moderator.getCenterId().equals(advisory.getCenterId())) continue;
            if (advisory.isAwaitingLgu()) pending++;
            else if (advisory.isDeclined()) declined++;
            else if (advisory.isPublished()) approved++;
        }
        List<ChartEntry> slices = new ArrayList<>();
        slices.add(new ChartEntry("Pending", pending, String.valueOf(pending), R.color.rr_orange));
        slices.add(new ChartEntry("Approved", approved, String.valueOf(approved), R.color.rr_green));
        slices.add(new ChartEntry("Declined", declined, String.valueOf(declined), R.color.rr_red));
        DonutChartView chart = findViewById(R.id.dash_advisory_chart);
        chart.setEmptyText("No advisories from this center in the last 30 days");
        chart.setEntries(pending + approved + declined == 0 ? Collections.emptyList() : slices);
    }

    // ------------------------------------------------------------------ the moderator's work

    private void showActivity() {
        if (moderator == null) return;
        List<AuditEvent> mine = new ArrayList<>();
        for (AuditEvent event : cityEvents) {
            if (moderator.getUid().equals(event.getActorUid())) mine.add(event);
        }
        long now = System.currentTimeMillis();
        OperationsReport report = OperationsReport.build(mine, new ArrayList<>(), new ArrayList<>(), now, ZoneId.systemDefault());
        List<ChartEntry> columns = new ArrayList<>();
        int total = 0;
        for (OperationsReport.DayCount day : report.getDailyActivity()) {
            columns.add(new ChartEntry(String.valueOf(day.getDate().getDayOfMonth()), day.getCount(),
                    String.valueOf(day.getCount()), R.color.rr_navy_light));
            total += day.getCount();
        }
        ColumnChartView chart = findViewById(R.id.dash_activity_chart);
        chart.setEmptyText("No changes by this moderator in the last 14 days");
        chart.setEntries(columns);
        ((TextView) findViewById(R.id.dash_activity_note)).setText(total == 0 ? ""
                : total + (total == 1 ? " change" : " changes") + " in 14 days • last "
                + Formats.relative(mine.get(0).getTimestamp(), now));

        // Work by kind over the last 30 days.
        int headcounts = 0, advisories = 0, checks = 0, ligtas = 0;
        for (AuditEvent event : mine) {
            if (now - event.getTimestamp() > OperationsReport.WINDOW_DAYS * DAY_MS) continue;
            String action = event.getAction();
            if ("UPDATE_CAPACITY".equals(action)) headcounts++;
            else if ("SUBMIT".equals(action) || "EDIT_PENDING".equals(action) || "WITHDRAW".equals(action)) advisories++;
            else if ("CHECK_PRIORITY_ID".equals(action)) checks++;
            else if ("ligtasApplication".equals(event.getEntityType())) ligtas++;
        }
        List<ChartEntry> bars = new ArrayList<>();
        bars.add(new ChartEntry("Headcount updates", headcounts, String.valueOf(headcounts), R.color.rr_teal_green));
        bars.add(new ChartEntry("Advisories sent", advisories, String.valueOf(advisories), R.color.rr_navy_light));
        bars.add(new ChartEntry("Priority ID checks", checks, String.valueOf(checks), R.color.rr_orange));
        bars.add(new ChartEntry("Ligtas decisions", ligtas, String.valueOf(ligtas), R.color.rr_green));
        BarListView work = findViewById(R.id.dash_work_chart);
        work.setEmptyText("No work recorded in the last 30 days");
        work.setEntries(headcounts + advisories + checks + ligtas == 0 ? Collections.emptyList() : bars);
        ((TextView) findViewById(R.id.dash_work_caption)).setText("Last " + OperationsReport.WINDOW_DAYS + " days, from the audit trail");
    }
}
