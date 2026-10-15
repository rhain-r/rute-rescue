package com.rutarescue.ui.resident.route;

import android.Manifest;
import android.content.Context;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.rutarescue.BuildConfig;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.Locatable;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.place.PlaceResult;
import com.rutarescue.model.place.SavedPlace;
import com.rutarescue.model.route.Maneuver;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteComparison;
import com.rutarescue.model.route.RouteSample;
import com.rutarescue.model.route.TravelMode;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.persistence.CachedRouteEntry;
import com.rutarescue.service.CenterService;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.SimpleListAdapter;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.common.UiMessages;
import com.rutarescue.ui.map.MapController;
import com.rutarescue.util.Formats;
import com.rutarescue.util.GeoMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe Route (DOCX section 4, Figma "Client / Safe Route").
 *
 * The resident picks a destination (search, saved place or center), a travel mode and a
 * Risk Horizon scenario. RouteService returns up to three alternatives assessed by the
 * bridge-aware RiskEngine; this controller shows them on the map and explains the ranking.
 * The top route is called "Safer among available alternatives", never "safe".
 */
public class SafeRouteFragment extends BaseFragment {

    private static final long SEARCH_DEBOUNCE_MS = 350; // ported from the legacy search box

    private MapController map;
    private NestedScrollView sheet; // the floating Figma sheet card (scrolls inside when long)
    private EditText searchInput;
    private View resultsCard;
    private SimpleListAdapter<Locatable> resultsAdapter;
    private SimpleListAdapter<RouteAssessment> alternativesAdapter;
    private SimpleListAdapter<Maneuver> maneuverAdapter;
    private TextView dataChip;
    private View stateIdle, stateLoading, stateError, stateResult, stateGuidance;

    private TravelMode mode = TravelMode.WALKING;
    private FloodScenario scenario = FloodScenario.DEFAULT;
    private Locatable destination;
    private Locatable pendingDestination;
    private GeoPoint origin;
    private boolean askedPermission;
    private boolean hazardVisible = true;
    private RouteComparison comparison;
    private int selectedIndex;
    private int currentStep;
    private List<EvacuationCenter> centers = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private boolean guiding;
    private LocationListener tracker; // continuous location updates while guiding

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> locate(false));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_safe_route, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        stateIdle = view.findViewById(R.id.route_state_idle);
        stateLoading = view.findViewById(R.id.route_state_loading);
        stateError = view.findViewById(R.id.route_state_error);
        stateResult = view.findViewById(R.id.route_state_result);
        stateGuidance = view.findViewById(R.id.route_state_guidance);
        dataChip = view.findViewById(R.id.route_data_chip);
        searchInput = view.findViewById(R.id.route_search);
        resultsCard = view.findViewById(R.id.route_results_card);

        sheet = view.findViewById(R.id.route_sheet);

        map = new MapController(view.findViewById(R.id.route_map), BuildConfig.TOMTOM_API_KEY);
        map.onCreate(state, ready -> {
            ready.showHazard(scenario, container().hazards, container().executors);
            ready.showCenters(centers);
            ready.setTrafficVisible(mode.usesTraffic());
            ready.setOnMapClick(point -> hideResults());
            if (pendingDestination != null) {
                Locatable target = pendingDestination;
                pendingDestination = null;
                setDestination(target);
            } else if (comparison != null) {
                drawComparison();
            }
        });

        setUpSearch(view);
        setUpChips(view);
        setUpButtons(view);
        setUpLists(view);
        updateDataChip();
        ((TextView) view.findViewById(R.id.route_legend)).setText(legendText());

        track(container().centers().listenPublished(ui(this::onCenters)));
        UserProfile profile = profile();
        if (profile != null) track(container().savedPlaces().listen(profile.getUid(), ui(this::onSavedPlaces)));
        loadCachedRoutes();
        locate(false);
        showState(stateIdle);
    }

    // ------------------------------------------------------------------ setup

    private void setUpSearch(View view) {
        RecyclerView results = view.findViewById(R.id.route_results);
        results.setLayoutManager(new LinearLayoutManager(requireContext()));
        resultsAdapter = new SimpleListAdapter<>(R.layout.item_simple_row, (row, place, position) -> {
            ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(
                    place instanceof EvacuationCenter ? R.drawable.ic_center : R.drawable.ic_pin);
            ((TextView) row.findViewById(R.id.row_title)).setText(place.getDisplayName());
            ((TextView) row.findViewById(R.id.row_subtitle)).setText(place instanceof EvacuationCenter
                    ? "Evacuation center • " + place.getSubtitle() : place.getSubtitle());
            row.setOnClickListener(v -> setDestination(place));
        });
        results.setAdapter(resultsAdapter);

        View clear = view.findViewById(R.id.route_search_clear);
        clear.setOnClickListener(v -> {
            searchInput.setText("");
            hideResults();
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                clear.setVisibility(text.length() > 0 ? View.VISIBLE : View.GONE);
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                if (!searchInput.hasFocus()) return;
                pendingSearch = () -> search(text.toString(), false);
                handler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                search(Dialogs.text(searchInput), true);
                return true;
            }
            return false;
        });
    }

    private void setUpChips(View view) {
        ChipGroup modes = view.findViewById(R.id.route_mode_chips);
        modes.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            mode = id == R.id.route_mode_moto ? TravelMode.MOTORCYCLE
                    : id == R.id.route_mode_car ? TravelMode.VEHICLE : TravelMode.WALKING;
            // Live traffic overlay (TomTom, on the OpenStreetMap map) for motorcycle and car trips.
            if (map.isReady()) map.setTrafficVisible(mode.usesTraffic());
            if (destination != null) requestRoutes();
        });
        ChipGroup scenarios = view.findViewById(R.id.route_scenario_chips);
        scenarios.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            scenario = id == R.id.route_scenario_25 ? FloodScenario.TWENTY_FIVE_YEAR
                    : id == R.id.route_scenario_100 ? FloodScenario.HUNDRED_YEAR : FloodScenario.FIVE_YEAR;
            updateDataChip();
            if (map.isReady()) map.showHazard(scenario, container().hazards, container().executors);
            // Switching the scenario never needs the internet: the cached provider answer is re-assessed.
            if (comparison != null && comparison.getCacheId() != null) {
                showState(stateLoading);
                container().routes().openCached(comparison.getCacheId(), scenario, routeCallback());
            } else if (destination != null) {
                requestRoutes();
            }
        });
    }

    private void setUpButtons(View view) {
        // Figma "Back": Safe Route is a tab, so Back returns to Home.
        view.findViewById(R.id.route_back).setOnClickListener(v ->
                ((com.rutarescue.ui.shell.MainShellActivity) requireActivity()).selectTab(0));
        view.findViewById(R.id.route_locate).setOnClickListener(v -> locate(true));
        view.findViewById(R.id.route_layers).setOnClickListener(v -> {
            hazardVisible = !hazardVisible;
            map.setHazardVisible(hazardVisible);
            showMessage(hazardVisible ? "Flood scenario layer shown" : "Flood scenario layer hidden");
        });
        View legend = view.findViewById(R.id.route_legend);
        view.findViewById(R.id.route_legend_button).setOnClickListener(v ->
                legend.setVisibility(legend.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        view.findViewById(R.id.route_retry).setOnClickListener(v -> requestRoutes());
        view.findViewById(R.id.route_clear).setOnClickListener(v -> clearRoute());
        view.findViewById(R.id.route_save_place).setOnClickListener(v -> savePlace());
        view.findViewById(R.id.route_start).setOnClickListener(v -> startGuidance());
        view.findViewById(R.id.route_end_guidance).setOnClickListener(v -> stopGuidance());
    }

    private void setUpLists(View view) {
        RecyclerView alternatives = view.findViewById(R.id.route_alternatives);
        alternatives.setLayoutManager(new LinearLayoutManager(requireContext()));
        alternativesAdapter = new SimpleListAdapter<>(R.layout.item_route_alternative, this::bindAlternative);
        alternatives.setAdapter(alternativesAdapter);

        RecyclerView maneuvers = view.findViewById(R.id.route_maneuvers);
        maneuvers.setLayoutManager(new LinearLayoutManager(requireContext()));
        maneuverAdapter = new SimpleListAdapter<>(R.layout.item_simple_row, (row, maneuver, position) -> {
            ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(
                    position == currentStep ? R.drawable.ic_navigation : R.drawable.ic_arrow_right);
            ((TextView) row.findViewById(R.id.row_title)).setText((position + 1) + ". " + maneuver.describe());
            ((TextView) row.findViewById(R.id.row_subtitle)).setText("at " + Formats.distance(maneuver.getRouteOffsetMeters()));
            row.setAlpha(position < currentStep ? 0.45f : 1f);
        });
        maneuvers.setAdapter(maneuverAdapter);
    }

    // ------------------------------------------------------------------ public API used by the shell

    /** Called from Home, Centers, saved places, or the center detail screen. */
    public void setDestination(Locatable target) {
        if (map == null || !map.isReady()) {
            pendingDestination = target;
            return;
        }
        destination = target;
        hideResults();
        searchInput.setText(target.getDisplayName());
        searchInput.clearFocus();
        hideKeyboard();
        requestRoutes();
    }

    public void focusSearch() {
        if (searchInput == null) return;
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    // ------------------------------------------------------------------ data

    private void onCenters(SourcedData<List<EvacuationCenter>> data) {
        centers = data.getData();
        if (map.isReady()) map.showCenters(centers);
        LinearLayout list = requireView().findViewById(R.id.route_center_list);
        list.removeAllViews();
        List<EvacuationCenter> nearby = CenterService.filter(centers, "", false, false, false, origin);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < Math.min(4, nearby.size()); i++) {
            EvacuationCenter center = nearby.get(i);
            View row = inflater.inflate(R.layout.item_simple_row, list, false);
            ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(R.drawable.ic_center);
            ((TextView) row.findViewById(R.id.row_title)).setText(center.getName());
            ((TextView) row.findViewById(R.id.row_subtitle)).setText((origin == null ? ""
                    : Formats.distance(CenterService.distance(center, origin)) + " • ") + center.getStatus().getLabel());
            row.setOnClickListener(v -> setDestination(center));
            list.addView(row);
        }
        if (nearby.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.no_center_yet);
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            list.addView(empty);
        }
    }

    private void onSavedPlaces(List<SavedPlace> places) {
        View view = requireView();
        view.findViewById(R.id.route_saved_title).setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
        ChipGroup chips = view.findViewById(R.id.route_saved_chips);
        chips.removeAllViews();
        for (SavedPlace place : places) {
            Chip chip = new Chip(requireContext());
            chip.setText(place.getDisplayName());
            chip.setCheckable(false);
            chip.setChipIconResource(R.drawable.ic_pin);
            chip.setOnClickListener(v -> setDestination(place));
            chip.setOnLongClickListener(v -> {
                Dialogs.confirm(requireContext(), "Remove saved place?", place.getDisplayName(), getString(R.string.remove),
                        () -> container().savedPlaces().delete(profile().getUid(), place.getId(), ui(ignored -> { })));
                return true;
            });
            chips.addView(chip);
        }
    }

    private void loadCachedRoutes() {
        container().routes().listCached(ui(entries -> {
            View view = requireView();
            LinearLayout list = view.findViewById(R.id.route_cached_list);
            list.removeAllViews();
            view.findViewById(R.id.route_cached_title).setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (CachedRouteEntry entry : entries) {
                View row = inflater.inflate(R.layout.item_simple_row, list, false);
                ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(R.drawable.ic_clock);
                ((TextView) row.findViewById(R.id.row_title)).setText(entry.destinationName);
                ((TextView) row.findViewById(R.id.row_subtitle)).setText(TravelMode.fromName(entry.travelMode).getLabel()
                        + " • cached " + Formats.relative(entry.fetchedAt, System.currentTimeMillis()));
                row.setOnClickListener(v -> {
                    destination = new PlaceResult(entry.destinationName, "",
                            new GeoPoint(entry.destinationLat, entry.destinationLon));
                    showState(stateLoading);
                    container().routes().openCached(entry.id, scenario, routeCallback());
                });
                list.addView(row);
            }
        }));
    }

    /**
     * Destination search. Published evacuation centers match while typing (offline too).
     * OpenStreetMap's Nominatim may not be called on every keystroke, so online results come
     * when the user presses Search (submitted = true).
     */
    private void search(String query, boolean submitted) {
        String text = query == null ? "" : query.strip();
        if (text.length() < 2) {
            hideResults();
            return;
        }
        // Local matches work offline: published evacuation centers.
        List<Locatable> matches = new ArrayList<>(CenterService.filter(centers, text, false, false, false, origin));
        if (matches.size() > 4) matches = new ArrayList<>(matches.subList(0, 4));
        showResults(matches);
        if (!submitted && !container().routes().allowsSearchAsYouType()) return;
        final List<Locatable> local = matches;
        container().routes().search(text, origin, UiCallback.of(this, places -> {
            List<Locatable> combined = new ArrayList<>(local);
            combined.addAll(places);
            showResults(combined);
        }, error -> {
            if (local.isEmpty()) showMessage(UiMessages.describe(error));
        }));
    }

    private void showResults(List<Locatable> places) {
        resultsAdapter.submit(places);
        resultsCard.setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void hideResults() {
        if (resultsCard != null) resultsCard.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ location

    private void locate(boolean recenter) {
        if (!container().location.hasPermission()) {
            if (!askedPermission) {
                askedPermission = true;
                permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
            } else if (recenter) {
                showMessage("Location permission is off. Routes will start from the map centre.");
            }
            return;
        }
        container().location.getCurrent(UiCallback.of(this, point -> {
            origin = point;
            if (map.isReady()) {
                map.showMarkers(origin, destination == null ? null : destination.getLocation(), null);
                if (recenter || comparison == null) map.moveTo(origin, 14);
            }
        }, error -> {
            if (recenter) showMessage(UiMessages.describe(error));
        }));
    }

    // ------------------------------------------------------------------ routes

    private void requestRoutes() {
        if (destination == null) return;
        stopGuidance();
        GeoPoint start = origin != null ? origin : map.cameraCenter();
        showState(stateLoading);
        sheet.scrollTo(0, 0);
        container().routes().findRoutes(start, destination, mode, scenario, routeCallback());
    }

    private UiCallback<RouteComparison> routeCallback() {
        return UiCallback.of(this, this::showComparison, error -> {
            ((TextView) requireView().findViewById(R.id.route_error_text)).setText(UiMessages.describe(error));
            showState(stateError);
        });
    }

    private void showComparison(RouteComparison result) {
        comparison = result;
        selectedIndex = 0;
        showState(stateResult);
        bindSelected();
        drawComparison();
        loadCachedRoutes();
        sheet.post(() -> sheet.smoothScrollTo(0, 0)); // show the Figma summary first
    }

    private void drawComparison() {
        if (comparison == null || !map.isReady()) return;
        map.showRoutes(comparison.getRanked(), selectedIndex);
        map.showMarkers(comparison.getOrigin(), comparison.getDestination(), null);
        // Frame the route in the visible map: below the top controls, left of the map buttons,
        // above the floating sheet. Posted so the sheet already has its final height.
        sheet.post(() -> {
            View root = getView();
            if (root == null || comparison == null || map == null || !map.isReady()) return;
            float density = getResources().getDisplayMetrics().density;
            int pad = (int) (16 * density);
            int top = dataChip.getBottom() + pad;
            int bottom = root.getHeight() - sheet.getTop() + pad;
            map.fitTo(comparison.getRanked().get(selectedIndex).getCandidate().getPoints(),
                    pad, top, (int) (72 * density), bottom);
        });
    }

    private void bindSelected() {
        View view = requireView();
        RouteAssessment route = comparison.getRanked().get(selectedIndex);
        ((TextView) view.findViewById(R.id.route_headline)).setText(route.getHeadline());
        Badges.riskLevel(view.findViewById(R.id.route_level_badge), route.getMaxLevel());
        // Figma "Route" line and "Route Stats" (distance, time, risk) below the sheet title.
        String via = route.getCandidate().getViaDescription();
        ((TextView) view.findViewById(R.id.route_summary)).setText(route.getCandidate().getLetterLabel()
                + (via.isEmpty() ? "" : " " + via) + " → " + comparison.getDestinationName());
        ((TextView) view.findViewById(R.id.route_stat_distance)).setText(
                Formats.distance(route.getCandidate().getLengthMeters()));
        ((TextView) view.findViewById(R.id.route_stat_time)).setText(
                Formats.duration(route.getCandidate().getTravelTimeSeconds()) + " " + tripWord(comparison.getTravelMode()));
        TextView risk = view.findViewById(R.id.route_stat_risk);
        risk.setText(riskSummary(route));
        risk.setTextColor(ContextCompat.getColor(requireContext(), riskColor(route.getMaxLevel())));
        ((TextView) view.findViewById(R.id.route_origin)).setText(origin == null
                ? R.string.origin_map_center : R.string.origin_my_location);

        HazardClass max = route.getMaxEffectiveHazard();
        ((TextView) view.findViewById(R.id.route_metric_hazard)).setText("Highest applicable hazard: "
                + max.getLabel() + (max == HazardClass.NONE ? "" : " (" + max.getDepthRange() + ")"));
        ((TextView) view.findViewById(R.id.route_metric_exposure)).setText("Distance exposed to mapped flood hazard: "
                + Formats.distance(route.getTotalExposureMeters()) + exposureBreakdown(route));
        ((TextView) view.findViewById(R.id.route_metric_structures)).setText("Unverified bridges/structures: "
                + Formats.distance(route.getUnverifiedStructureMeters())
                + (route.getBridgeMeters() > 0 ? " • bridges on route " + Formats.distance(route.getBridgeMeters()) : ""));
        ((TextView) view.findViewById(R.id.route_metric_traffic)).setText(trafficText(route));

        TextView freshness = view.findViewById(R.id.route_freshness);
        Badges.freshness(freshness, comparison.getFreshness(), comparison.getFetchedAt(), "");
        freshness.setText("Route data " + freshness.getText());
        ((TextView) view.findViewById(R.id.route_scenario_note)).setText(comparison.getScenario().getExplanation()
                + " Layer " + comparison.getHazardDatasetVersion() + ".");
        ((TextView) view.findViewById(R.id.route_explanation)).setText(String.join("\n\n", comparison.getExplanation()));
        ((TextView) view.findViewById(R.id.route_notes)).setText(route.getNotes().isEmpty() ? ""
                : "On this route:\n• " + String.join("\n• ", route.getNotes()));
        alternativesAdapter.submit(comparison.getRanked());
    }

    /** Figma wording: "18 min walk"; also "ride" for motorcycle and "drive" for car. */
    private static String tripWord(TravelMode mode) {
        switch (mode) {
            case MOTORCYCLE: return "ride";
            case VEHICLE: return "drive";
            default: return "walk";
        }
    }

    /** Short risk statement for the "Route Stats" row. Never claims a route is safe. */
    private static String riskSummary(RouteAssessment route) {
        if (route.isClosed()) return "Crosses a reported closure";
        HazardClass max = route.getMaxEffectiveHazard();
        String text = max == HazardClass.NONE ? "No mapped flood hazard" : max.getLabel() + " hazard on route";
        if (route.getUnverifiedStructureMeters() > 0) text += " • unverified bridge";
        return text;
    }

    private static int riskColor(com.rutarescue.model.route.RiskLevel level) {
        switch (level) {
            case GREEN: return R.color.rr_green;
            case AMBER: return R.color.rr_orange;
            case RED: return R.color.rr_red;
            default: return R.color.rr_navy;
        }
    }

    private static String exposureBreakdown(RouteAssessment route) {
        if (route.getTotalExposureMeters() <= 0) return "";
        return " (low " + Formats.distance(route.getExposureMeters(HazardClass.LOW))
                + ", medium " + Formats.distance(route.getExposureMeters(HazardClass.MEDIUM))
                + ", high " + Formats.distance(route.getExposureMeters(HazardClass.HIGH)) + ")";
    }

    private String trafficText(RouteAssessment route) {
        if (!comparison.getTravelMode().usesTraffic()) return "Traffic: not used for walking routes";
        if (!route.getCandidate().isTrafficConsidered()) {
            return "Traffic NOT included: OpenStreetMap routing uses typical speeds. Add a free TomTom key for live traffic.";
        }
        long delayMinutes = Math.round(route.getCandidate().getTrafficDelaySeconds() / 60.0);
        if (comparison.getFreshness() == DataFreshness.LIVE) {
            return "Live traffic: " + (delayMinutes > 0 ? "+" + delayMinutes + " min delay" : "no reported delay")
                    + " (as of " + Formats.clock(comparison.getFetchedAt()) + ")";
        }
        return "Traffic NOT live: provider time cached " + Formats.relative(comparison.getFetchedAt(), System.currentTimeMillis());
    }

    private void bindAlternative(View row, RouteAssessment route, int position) {
        ((TextView) row.findViewById(R.id.alt_rank)).setText(String.valueOf(route.getRank()));
        ((TextView) row.findViewById(R.id.alt_title)).setText(route.getCandidate().getLetterLabel()
                + (route.getRank() == 1 && route.getAlternativesCount() > 1 && !route.isClosed()
                ? " • " + getString(R.string.safer_label) : ""));
        Badges.riskLevel(row.findViewById(R.id.alt_badge), route.getMaxLevel());
        ((TextView) row.findViewById(R.id.alt_summary)).setText(Formats.distance(route.getCandidate().getLengthMeters())
                + " • " + Formats.duration(route.getCandidate().getTravelTimeSeconds())
                + (route.isClosed() ? " • crosses a closure" : ""));
        ((TextView) row.findViewById(R.id.alt_metrics)).setText("Max hazard " + route.getMaxEffectiveHazard().getLabel()
                + " • exposed " + Formats.distance(route.getTotalExposureMeters())
                + " • unverified structures " + Formats.distance(route.getUnverifiedStructureMeters()));
        row.setBackgroundResource(position == selectedIndex ? R.drawable.bg_route_selected : R.drawable.bg_pill_outline);
        row.setOnClickListener(v -> {
            selectedIndex = position;
            bindSelected();
            drawComparison();
        });
    }

    private void clearRoute() {
        stopGuidance();
        comparison = null;
        destination = null;
        searchInput.setText("");
        map.clearRoutes();
        map.showMarkers(origin, null, null);
        showState(stateIdle);
    }

    private void savePlace() {
        if (destination == null || profile() == null) return;
        Dialogs.input(requireContext(), getString(R.string.save_place), "Name for this place",
                destination.getDisplayName(), name -> {
                    try {
                        SavedPlace place = new SavedPlace(null, name, destination.getLocation(), System.currentTimeMillis());
                        container().savedPlaces().add(profile().getUid(), place, ui(ignored -> showMessage("Place saved")));
                    } catch (ValidationException e) {
                        showError(e);
                    }
                });
    }

    // ------------------------------------------------------------------ guidance (text maneuvers + progress)

    /**
     * Turn-by-turn guidance: follows the phone's position continuously (LocationHelper tracking),
     * highlights the current maneuver, shows the distance left and keeps the map centred.
     */
    private void startGuidance() {
        if (comparison == null) return;
        RouteAssessment route = comparison.getRanked().get(selectedIndex);
        guiding = true;
        currentStep = 0;
        maneuverAdapter.submit(route.getCandidate().getManeuvers());
        showState(stateGuidance);
        // First position straight away, then every new fix while guiding.
        container().location.getCurrent(UiCallback.of(this, this::onGuidancePosition, error ->
                ((TextView) requireView().findViewById(R.id.route_guidance_progress)).setText(UiMessages.describe(error))));
        tracker = container().location.startTracking(position -> {
            if (getView() != null) onGuidancePosition(position);
        });
    }

    private void stopGuidance() {
        guiding = false;
        container().location.stopTracking(tracker);
        tracker = null;
        if (comparison != null && stateGuidance.getVisibility() == View.VISIBLE) showState(stateResult);
    }

    private void onGuidancePosition(GeoPoint position) {
        if (!guiding || getView() == null || comparison == null) return;
        RouteAssessment route = comparison.getRanked().get(selectedIndex);
        List<Maneuver> steps = route.getCandidate().getManeuvers();
        origin = position;
        double along = distanceAlong(route, position);
        int step = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getRouteOffsetMeters() <= along + 15) step = i;
        }
        boolean stepChanged = step != currentStep;
        currentStep = step;
        maneuverAdapter.notifyDataSetChanged();
        if (stepChanged) scrollToCurrentStep();
        double remaining = Math.max(0, route.getCandidate().getLengthMeters() - along);
        ((TextView) requireView().findViewById(R.id.route_guidance_current)).setText(steps.isEmpty()
                ? "Follow the highlighted route" : steps.get(step).describe());
        ((TextView) requireView().findViewById(R.id.route_guidance_progress)).setText("About "
                + Formats.distance(remaining) + " left • voice guidance is not part of this version");
        map.showMarkers(position, comparison.getDestination(), null);
        map.moveTo(position, 16);
    }

    /** Keeps the current maneuver visible inside the floating sheet. */
    private void scrollToCurrentStep() {
        RecyclerView list = requireView().findViewById(R.id.route_maneuvers);
        list.post(() -> {
            RecyclerView.LayoutManager layout = list.getLayoutManager();
            View row = layout == null ? null : layout.findViewByPosition(currentStep);
            if (row != null) sheet.smoothScrollTo(0, stateGuidance.getTop() + list.getTop() + row.getTop());
        });
    }

    /** Distance along the route of the sample closest to the current position. */
    private static double distanceAlong(RouteAssessment route, GeoPoint position) {
        double best = Double.MAX_VALUE;
        double along = 0;
        for (RouteSample sample : route.getSamples()) {
            double d = GeoMath.distanceMeters(sample.getPoint(), position);
            if (d < best) {
                best = d;
                along = sample.getDistanceAlongMeters();
            }
        }
        return along;
    }

    // ------------------------------------------------------------------ helpers

    private void showState(View visible) {
        for (View state : new View[]{stateIdle, stateLoading, stateError, stateResult, stateGuidance}) {
            state.setVisibility(state == visible ? View.VISIBLE : View.GONE);
        }
    }

    private void updateDataChip() {
        String version = container().hazards.getManifest().versionOf(scenario);
        Badges.freshness(dataChip, DataFreshness.PREPARED, 0, "NOAH " + scenario.getLabel() + " • " + version);
    }

    private static String legendText() {
        return "Route colours: cyan = no mapped hazard • yellow/orange/red = LOW/MEDIUM/HIGH NOAH class on a "
                + "surface road • violet = bridge or structure with unverified passability • black = reported "
                + "closure • grey = outside prepared NOAH coverage.\nMap fill = selected prepared scenario "
                + "(not live flooding). Green dots = LGU-verified evacuation centers.";
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------ MapView lifecycle

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
    public void onLowMemory() {
        super.onLowMemory();
        if (map != null) map.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (map != null) map.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        guiding = false;
        if (tracker != null) container().location.stopTracking(tracker);
        tracker = null;
        handler.removeCallbacksAndMessages(null);
        if (map != null) map.onDestroy();
        map = null;
        super.onDestroyView();
    }
}
