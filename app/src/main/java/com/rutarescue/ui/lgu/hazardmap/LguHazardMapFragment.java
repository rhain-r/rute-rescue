package com.rutarescue.ui.lgu.hazardmap;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.rutarescue.BuildConfig;
import com.rutarescue.R;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.hazard.PointHazard;
import com.rutarescue.model.map.MapLayerSettings;
import com.rutarescue.model.route.RoadClosure;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.model.structure.StructureStatus;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.repository.StructureStatusRepository;
import com.rutarescue.risk.StructureIndex;
import com.rutarescue.service.HazardQueryService;
import com.rutarescue.service.MapLayerPreferences;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.InfoTooltip;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.map.MapController;
import com.rutarescue.ui.map.MapLayersSheet;
import com.rutarescue.util.Formats;
import com.rutarescue.util.GeoMath;
import com.rutarescue.validation.InputValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LGU Hazard Map (Figma "LGU / Hazard Map", DOCX sections 5 and 8).
 *
 * The NOAH layers are prepared and READ-ONLY. What LGU staff publish here is field
 * evidence: bridge/tunnel passability (unverified / verified open / closed) and road
 * closures. Each change is written with an audit event and immediately affects the
 * residents' route ranking.
 */
public class LguHazardMapFragment extends BaseFragment implements MapLayersSheet.Listener {

    private LguProfile lgu;
    /** Parallel ways closer than this (metres, degrees) are parts of the same bridge or tunnel. */
    private static final double SAME_STRUCTURE_METERS = 25;
    private static final double SAME_STRUCTURE_DEGREES = 20;

    private MapController map;
    /** Tap-to-inspect lookups: off the UI thread, newest tap wins. */
    private HazardQueryService hazardQueries;
    private FloodScenario scenario = FloodScenario.DEFAULT;
    private MapLayerSettings layers;
    private boolean syncingPills; // true while the pills are updated from the Map layers sheet
    private StructureIndex structureIndex;
    private List<RoadClosure> closures = new ArrayList<>();
    private GeoPoint selected;
    private NestedScrollView sheet; // the floating Figma sheet card (scrolls inside when long)

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_lgu_hazard_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        lgu = container().session().requireLgu();
        hazardQueries = new HazardQueryService(container().hazards, container().executors);
        sheet = view.findViewById(R.id.lgu_map_sheet);
        ((TextView) view.findViewById(R.id.lgu_closures_title)).setText("Active closures • "
                + lgu.getJurisdiction().getDisplayName());
        layers = container().mapLayers.load(MapLayerPreferences.LGU_MAP);
        scenario = layers.getAssessmentScenario();
        updateLayerLabel();

        map = new MapController(view.findViewById(R.id.lgu_map), BuildConfig.TOMTOM_API_KEY);
        map.onCreate(state, ready -> {
            ready.applyLayers(layers, container().hazards, container().executors);
            ready.setOnMapClick(this::select);
            ready.setOnMapLongClick(point -> {
                select(point);
                reportClosure();
            });
            track(container().datasets().listenStructureStatuses(ui(this::onStatuses)));
            track(container().datasets().listenClosures(ui(this::onClosures)));
            track(container().centers().listenForManager(lgu, ui(data -> {
                List<EvacuationCenter> centers = data.getData();
                if (map != null) map.showCenters(centers);
                if (!centers.isEmpty() && selected == null && map != null) map.moveTo(centers.get(0).getLocation(), 13);
            })));
        });

        Chip zones = view.findViewById(R.id.lgu_layer_zones);
        Chip structures = view.findViewById(R.id.lgu_layer_structures);
        Chip centers = view.findViewById(R.id.lgu_layer_centers);
        syncPills(view);
        zones.setOnCheckedChangeListener((b, checked) -> map.setHazardVisible(checked));
        // Info tooltips: the risk zones are prepared NOAH scenarios, not a weather update.
        InfoTooltip.attach(view.findViewById(R.id.lgu_map_info), R.string.tip_risk_zones_title,
                R.string.tip_risk_zones_body);
        TooltipCompat.setTooltipText(zones, getString(R.string.tip_risk_zones_title));
        // "Routes" layer: bridges/tunnels, LGU closures and (with a TomTom key) live traffic.
        structures.setOnCheckedChangeListener((b, checked) -> {
            if (syncingPills) return;
            layers.setStructuresVisible(checked);
            layers.setTrafficVisible(checked);
            saveAndApplyLayers();
        });
        centers.setOnCheckedChangeListener((b, checked) -> {
            if (syncingPills) return;
            layers.setCentersVisible(checked);
            saveAndApplyLayers();
        });
        ChipGroup scenarios = view.findViewById(R.id.lgu_scenario_chips);
        scenarios.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty() || syncingPills) return;
            int id = ids.get(0);
            scenario = id == R.id.lgu_scenario_25 ? FloodScenario.TWENTY_FIVE_YEAR
                    : id == R.id.lgu_scenario_100 ? FloodScenario.HUNDRED_YEAR : FloodScenario.FIVE_YEAR;
            // A scenario pill shows that one scenario; the Map layers sheet can show several.
            for (FloodScenario each : FloodScenario.values()) layers.setFloodVisible(each, each == scenario);
            saveAndApplyLayers();
            updateLayerLabel();
            if (selected != null) select(selected);
        });

        // Figma "LGU Map Header" and "Map Controls"
        view.findViewById(R.id.lgu_back).setOnClickListener(v ->
                ((com.rutarescue.ui.shell.MainShellActivity) requireActivity()).selectTab(0));
        view.findViewById(R.id.lgu_more).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.map_legend_title)
                .setMessage(R.string.lgu_map_legend)
                .setPositiveButton(android.R.string.ok, null)
                .show());
        view.findViewById(R.id.lgu_locate).setOnClickListener(v -> locate());
        view.findViewById(R.id.lgu_layers).setOnClickListener(v -> openLayers());

        view.findViewById(R.id.lgu_sel_set_status).setOnClickListener(v -> setStructureStatus());
        view.findViewById(R.id.lgu_sel_report_closure).setOnClickListener(v -> reportClosure());
    }

    // ------------------------------------------------------------------ map layers (Phase 1 sheet)

    private void openLayers() {
        MapLayersSheet.TrafficState traffic = BuildConfig.TOMTOM_API_KEY == null || BuildConfig.TOMTOM_API_KEY.isEmpty()
                ? MapLayersSheet.TrafficState.NO_KEY
                : container().connectivity.isOnline() ? MapLayersSheet.TrafficState.LIVE : MapLayersSheet.TrafficState.CACHED;
        MapLayersSheet.newInstance(layers, traffic).show(getChildFragmentManager(), MapLayersSheet.TAG);
    }

    @Override
    public void onLayersChanged(MapLayerSettings settings) {
        layers = settings;
        scenario = settings.getAssessmentScenario();
        saveAndApplyLayers();
        updateLayerLabel();
        if (getView() != null) syncPills(getView());
        if (selected != null) select(selected);
    }

    private void saveAndApplyLayers() {
        container().mapLayers.save(MapLayerPreferences.LGU_MAP, layers);
        if (map != null && map.isReady()) map.applyLayers(layers, container().hazards, container().executors);
    }

    /** Shows the sheet's choices on the Figma pills without running their listeners. */
    private void syncPills(View view) {
        syncingPills = true;
        ((Chip) view.findViewById(R.id.lgu_layer_structures)).setChecked(layers.isStructuresVisible());
        ((Chip) view.findViewById(R.id.lgu_layer_centers)).setChecked(layers.isCentersVisible());
        int pill = scenario == FloodScenario.TWENTY_FIVE_YEAR ? R.id.lgu_scenario_25
                : scenario == FloodScenario.HUNDRED_YEAR ? R.id.lgu_scenario_100 : R.id.lgu_scenario_5;
        ((ChipGroup) view.findViewById(R.id.lgu_scenario_chips)).check(pill);
        syncingPills = false;
    }

    /** Centers the map on the officer's location, or on the jurisdiction's first center. */
    private void locate() {
        if (map == null || !map.isReady()) return;
        if (container().location.hasPermission()) {
            container().location.getCurrent(UiCallback.of(this, point -> {
                if (map != null) map.moveTo(point, 15);
            }, error -> showError(error)));
        } else {
            map.moveTo(MapController.METRO_MANILA_CENTER, 12);
            showMessage("Location permission is off. Showing Metro Manila.");
        }
    }

    private void updateLayerLabel() {
        // Figma "Meta" under the map title: "Layer v12 • Updated 9:42 AM" -> version + not-live label
        ((TextView) requireView().findViewById(R.id.lgu_map_layer)).setText("Layer "
                + container().hazards.getManifest().versionOf(scenario) + " • prepared, not live");
    }

    /** Rebuilds the bridge/tunnel index in the background whenever an LGU status changes. */
    private void onStatuses(Map<String, StructureStatusRepository.StatusRecord> statuses) {
        container().executors.io().execute(() -> {
            try {
                StructureIndex index = container().structures.buildIndex(statuses);
                container().executors.main().execute(() -> {
                    if (map == null) return;
                    structureIndex = index;
                    map.showStructures(index.getRecords());
                    if (selected != null) select(selected);
                });
            } catch (Exception e) {
                container().executors.main().execute(() -> showError(e));
            }
        });
    }

    private void onClosures(List<RoadClosure> active) {
        closures = active;
        if (map != null) map.showClosures(active);
        LinearLayout list = requireView().findViewById(R.id.lgu_closure_list);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (RoadClosure closure : active) {
            if (closure.getCity() != lgu.getJurisdiction()) continue;
            View row = inflater.inflate(R.layout.item_simple_row, list, false);
            ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(R.drawable.ic_warning);
            ((TextView) row.findViewById(R.id.row_title)).setText(closure.getName());
            ((TextView) row.findViewById(R.id.row_subtitle)).setText(closure.getReason() + " • "
                    + closure.getRadiusMeters() + " m • " + Formats.relative(closure.getReportedAt(), System.currentTimeMillis()));
            row.setOnClickListener(v -> {
                map.moveTo(closure.getLocation(), 16);
                select(closure.getLocation());
            });
            list.addView(row);
        }
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText("No active closures reported by your LGU.");
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            list.addView(empty);
        }
        if (selected != null) select(selected);
    }

    /** Inspects one point: NOAH class (prepared), nearest structure and any closure. */
    private void select(GeoPoint point) {
        selected = point;
        View view = requireView();
        view.findViewById(R.id.lgu_map_hint).setVisibility(View.GONE);
        view.findViewById(R.id.lgu_selection).setVisibility(View.VISIBLE);
        map.showMarkers(null, null, point);
        ((TextView) view.findViewById(R.id.lgu_sel_title)).setText("Selected point • " + point);

        // Background lookup; fast repeated taps or scenario switches only ever show the newest answer.
        hazardQueries.inspect(point, scenario, UiCallback.of(this, this::showPointHazard, error -> {
            ((TextView) requireView().findViewById(R.id.lgu_sel_hazard)).setText("Could not read the prepared NOAH data");
            Badges.hazard(requireView().findViewById(R.id.lgu_sel_hazard_badge), HazardClass.NONE);
        }));

        RoadStructureRecord structure = structureIndex == null ? null : structureIndex.nearest(point, 30);
        TextView structureText = view.findViewById(R.id.lgu_sel_structure);
        TextView structureBadge = view.findViewById(R.id.lgu_sel_structure_badge);
        View setStatus = view.findViewById(R.id.lgu_sel_set_status);
        if (structure == null) {
            structureText.setText(structureIndex == null ? "Loading bridges and tunnels…"
                    : "No bridge or tunnel within 30 m");
            structureBadge.setVisibility(View.GONE);
            setStatus.setVisibility(View.GONE);
        } else {
            structureText.setText(structure.getType().getLabel() + ": " + structure.getName()
                    + (structure.getEvidence().isEmpty() ? "" : "\nEvidence: " + structure.getEvidence()
                    + " (" + Formats.relative(structure.getStatusUpdatedAt(), System.currentTimeMillis()) + ")"));
            structureBadge.setVisibility(View.VISIBLE);
            Badges.structure(structureBadge, structure.getStatus());
            setStatus.setVisibility(View.VISIBLE);
        }

        RoadClosure closure = closureAt(point);
        View lift = view.findViewById(R.id.lgu_sel_lift_closure);
        ((TextView) view.findViewById(R.id.lgu_sel_closure)).setText(closure == null ? "No active closure here"
                : "Closure: " + closure.getName() + " — " + closure.getReason());
        lift.setVisibility(closure != null && closure.getCity() == lgu.getJurisdiction() ? View.VISIBLE : View.GONE);
        lift.setOnClickListener(v -> Dialogs.input(requireContext(), "Lift closure", "Reason (kept in the audit trail)", "",
                reason -> container().datasets().liftClosure(lgu, closure, reason, ui(ignored -> showMessage("Closure lifted")))));
        sheet.post(() -> sheet.smoothScrollTo(0, 0));
    }

    /** The answer names its own scenario, so the label always matches the class shown. */
    private void showPointHazard(PointHazard answer) {
        ((TextView) requireView().findViewById(R.id.lgu_sel_hazard)).setText(answer.describe());
        Badges.hazard(requireView().findViewById(R.id.lgu_sel_hazard_badge), answer.getHazardClass());
    }

    private RoadClosure closureAt(GeoPoint point) {
        for (RoadClosure closure : closures) {
            if (GeoMath.distanceMeters(point, closure.getLocation()) <= Math.max(30, closure.getRadiusMeters())) return closure;
        }
        return null;
    }

    private void setStructureStatus() {
        if (selected == null || structureIndex == null) return;
        RoadStructureRecord structure = structureIndex.nearest(selected, 30);
        if (structure == null) return;
        // One physical bridge is often several OpenStreetMap ways (each direction + sidewalks):
        // the status applies to all of them, so no direction is left open by mistake.
        List<RoadStructureRecord> parts = structureIndex.partsOfSameStructure(structure, SAME_STRUCTURE_METERS,
                SAME_STRUCTURE_DEGREES);
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_structure_status, null);
        ((TextView) form.findViewById(R.id.status_structure_name)).setText(structure.getType().getLabel() + ": "
                + structure.getName() + (parts.size() > 1
                ? "\nApplies to all " + parts.size() + " mapped parts (both directions and sidewalks)" : ""));
        RadioGroup group = form.findViewById(R.id.status_group);
        group.check(structure.getStatus() == StructureStatus.CLOSED ? R.id.status_closed
                : structure.getStatus() == StructureStatus.VERIFIED_OPEN ? R.id.status_open : R.id.status_unverified);
        TextInputEditText evidence = form.findViewById(R.id.status_evidence);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.set_structure_status)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    int checked = group.getCheckedRadioButtonId();
                    StructureStatus status = checked == R.id.status_closed ? StructureStatus.CLOSED
                            : checked == R.id.status_open ? StructureStatus.VERIFIED_OPEN : StructureStatus.UNVERIFIED;
                    String note = Dialogs.text(evidence);
                    int[] saved = {0};
                    for (RoadStructureRecord part : parts) {
                        container().datasets().setStructureStatus(lgu, part.getId(), part.getName().isEmpty()
                                ? structure.getName() : part.getName(), status, note, ui(ignored -> {
                            if (++saved[0] == parts.size()) {
                                showMessage("Status saved for " + parts.size() + " part(s); residents' routes now use it");
                            }
                        }));
                    }
                })
                .show();
    }

    private void reportClosure() {
        if (selected == null) return;
        GeoPoint location = selected;
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_closure, null);
        TextInputEditText name = form.findViewById(R.id.closure_name);
        TextInputEditText radius = form.findViewById(R.id.closure_radius);
        TextInputEditText reason = form.findViewById(R.id.closure_reason);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.report_closure_here)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    int meters;
                    try {
                        meters = InputValidator.parsePositiveInteger(Dialogs.text(radius));
                    } catch (IllegalArgumentException e) {
                        showMessage("Radius: " + e.getMessage());
                        return;
                    }
                    container().datasets().reportClosure(lgu, Dialogs.text(name), location, meters, Dialogs.text(reason),
                            ui(ignored -> showMessage("Closure reported — routes through it are now ranked last")));
                })
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (map != null) map.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    public void onPause() {
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        if (map != null) map.onStop();
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (map != null) map.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (hazardQueries != null) hazardQueries.cancel();
        if (map != null) map.onDestroy();
        map = null;
        super.onDestroyView();
    }
}
