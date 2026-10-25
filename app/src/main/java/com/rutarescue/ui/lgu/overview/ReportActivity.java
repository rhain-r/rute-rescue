package com.rutarescue.ui.lgu.overview;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rutarescue.R;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.OperationsReport;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.chart.BarListView;
import com.rutarescue.ui.common.chart.ChartEntry;
import com.rutarescue.ui.common.chart.ColumnChartView;
import com.rutarescue.ui.common.chart.DonutChartView;
import com.rutarescue.util.Formats;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "View report" as charts (2026-10-20). It follows the city's audit trail, Ligtas applications
 * and centers live, and redraws the charts with OperationsReport whenever one of them changes.
 * The charts only draw; every number is computed (and unit-tested) in OperationsReport.
 */
public class ReportActivity extends BaseActivity {

    private static final DateTimeFormatter DAY_NAME = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    private final List<ListenerHandle> listeners = new ArrayList<>();
    private List<AuditEvent> events = new ArrayList<>();
    private List<LigtasApplication> applications = new ArrayList<>();
    private List<EvacuationCenter> centers = new ArrayList<>();
    private OperationsReport report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        applySystemBarInsets(findViewById(R.id.report_root));
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        StaffProfile staff = container().session().requireStaff();
        String city = staff.getJurisdiction().getDisplayName();
        ((TextView) findViewById(R.id.toolbar_title)).setText(getString(R.string.report_title, city));
        ((TextView) findViewById(R.id.report_intro)).setText(getString(R.string.report_intro, city));

        ((ColumnChartView) findViewById(R.id.report_activity)).setOnColumnSelected((index, entry) ->
                showDay(report.getDailyActivity().get(index)));
        ((BarListView) findViewById(R.id.report_centers)).setFixedMax(1.0); // a full bar = 100% full
        ((DonutChartView) findViewById(R.id.report_ligtas)).setTotalCaption("applications");

        listeners.add(container().audit().listenForCity(staff.getJurisdiction(), ui(list -> {
            events = list;
            render();
        })));
        listeners.add(container().ligtas().listenForReviewer(staff, ui(data -> {
            applications = data.getData();
            render();
        })));
        listeners.add(container().centers().listenForManager(staff, ui(data -> {
            centers = data.getData();
            render();
        })));
        render();
    }

    private void render() {
        report = OperationsReport.build(events, applications, centers, System.currentTimeMillis(), ZoneId.systemDefault());
        renderActivity();
        renderKinds();
        renderLigtas();
        renderCenters();
        renderLatest();
    }

    private void renderActivity() {
        List<ChartEntry> columns = new ArrayList<>();
        OperationsReport.DayCount busiest = null;
        for (OperationsReport.DayCount day : report.getDailyActivity()) {
            columns.add(new ChartEntry(String.valueOf(day.getDate().getDayOfMonth()), day.getCount(),
                    String.valueOf(day.getCount()), R.color.rr_navy_light));
            if (busiest == null || day.getCount() >= busiest.getCount()) busiest = day;
        }
        ColumnChartView chart = findViewById(R.id.report_activity);
        chart.setEmptyText("No changes in the last 14 days");
        chart.setEntries(columns);
        TextView note = findViewById(R.id.report_activity_note);
        if (busiest == null || busiest.getCount() == 0) {
            note.setVisibility(View.GONE);
        } else {
            note.setVisibility(View.VISIBLE);
            note.setText("Busiest day: " + busiest.getDate().format(DAY_NAME) + " • " + changes(busiest.getCount()));
        }
    }

    private void showDay(OperationsReport.DayCount day) {
        TextView note = findViewById(R.id.report_activity_note);
        note.setVisibility(View.VISIBLE);
        note.setText(day.getDate().format(DAY_NAME) + " • " + changes(day.getCount()));
    }

    private void renderKinds() {
        List<ChartEntry> bars = new ArrayList<>();
        for (OperationsReport.Category category : OperationsReport.Category.values()) {
            int count = report.getChangesByCategory().get(category);
            bars.add(new ChartEntry(category.getLabel(), count, String.valueOf(count), R.color.rr_navy_light));
        }
        BarListView chart = findViewById(R.id.report_kinds);
        chart.setEmptyText("No changes in the last 30 days");
        chart.setEntries(bars);
        ((TextView) findViewById(R.id.report_kinds_caption)).setText("Last " + OperationsReport.WINDOW_DAYS
                + " days • " + changes(report.getChangesInWindow()) + " in total");
    }

    private void renderLigtas() {
        List<ChartEntry> slices = new ArrayList<>();
        int total = report.getLigtasTotal();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            int count = report.getLigtasByStatus().get(status);
            int percent = total == 0 ? 0 : Math.round(100f * count / total);
            slices.add(new ChartEntry(status.getLabel(), count, count + " • " + percent + "%", ligtasColor(status)));
        }
        DonutChartView chart = findViewById(R.id.report_ligtas);
        chart.setEmptyText("No Ligtas applications yet");
        chart.setEntries(slices);
    }

    /** Status colours, each named in the legend: waiting = navy, needs documents = amber, approved = green, rejected = red. */
    private static int ligtasColor(ApplicationStatus status) {
        switch (status) {
            case APPROVED: return R.color.rr_green;
            case REJECTED: return R.color.rr_red;
            case NEEDS_DOCUMENTS: return R.color.rr_gauge_amber;
            default: return R.color.rr_navy_light;
        }
    }

    private void renderCenters() {
        List<ChartEntry> bars = new ArrayList<>();
        for (OperationsReport.CenterLoad load : report.getCenterLoads()) {
            int color;
            switch (load.getLevel()) {
                case RED: color = R.color.hazard_high; break;
                case AMBER: color = R.color.rr_gauge_amber; break;
                default: color = R.color.rr_green;
            }
            bars.add(new ChartEntry(load.getName(), load.getPercent() / 100.0,
                    load.getOccupancy() + " of " + load.getCapacity() + " • " + load.getPercent() + "%", color));
        }
        BarListView chart = findViewById(R.id.report_centers);
        chart.setEmptyText(bars.isEmpty() ? "No center has a capacity yet" : "Every center is empty (0 people)");
        chart.setEntries(bars);
        TextView note = findViewById(R.id.report_centers_note);
        int missing = report.getCentersWithoutCapacity();
        note.setVisibility(missing == 0 ? View.GONE : View.VISIBLE);
        note.setText(missing + (missing == 1 ? " center has" : " centers have")
                + " no capacity set yet (a moderator sets it under Data > Evacuation centers).");
    }

    private void renderLatest() {
        LinearLayout list = findViewById(R.id.report_latest);
        list.removeAllViews();
        long now = System.currentTimeMillis();
        for (AuditEvent event : report.getLatestEvents()) {
            TextView row = new TextView(this);
            row.setTextAppearance(R.style.TextAppearance_RR_Body);
            row.setText(OperationsReport.describe(event));
            TextView meta = new TextView(this);
            meta.setTextAppearance(R.style.TextAppearance_RR_Caption);
            meta.setText(Formats.dateTime(event.getTimestamp()) + " • " + Formats.relative(event.getTimestamp(), now)
                    + (event.getReason().isEmpty() ? "" : "\n" + event.getReason()));
            meta.setPadding(0, 0, 0, Math.round(10 * getResources().getDisplayMetrics().density));
            list.addView(row);
            list.addView(meta);
        }
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            empty.setText("No recorded changes yet.");
            list.addView(empty);
        }
    }

    private static String changes(int count) {
        return count + (count == 1 ? " change" : " changes");
    }

    @Override
    protected void onDestroy() {
        for (ListenerHandle listener : listeners) listener.remove();
        super.onDestroy();
    }
}
