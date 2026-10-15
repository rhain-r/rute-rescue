package com.rutarescue.ui.resident.route;

import android.Manifest;
import android.content.Context;
import android.content.res.ColorStateList;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
import com.rutarescue.model.map.MapLayerSettings;
import com.rutarescue.model.place.PlaceResult;
import com.rutarescue.model.place.SavedPlace;
import com.rutarescue.model.route.Maneuver;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteComparison;
import com.rutarescue.model.route.RouteRole;
import com.rutarescue.model.route.RouteSample;
import com.rutarescue.model.route.RouteWarning;
import com.rutarescue.model.route.TravelMode;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.persistence.CachedRouteEntry;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.StructureStatusRepository;
import com.rutarescue.risk.RerouteAdvisor;
import com.rutarescue.risk.RouteRoleAssigner;
import com.rutarescue.risk.RouteWarningBuilder;
import com.rutarescue.risk.StructureIndex;
import com.rutarescue.service.CenterService;
import com.rutarescue.service.MapLayerPreferences;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.MaxHeightScrollView;
import com.rutarescue.ui.common.SimpleListAdapter;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.common.UiMessages;
import com.rutarescue.ui.map.MapController;
import com.rutarescue.ui.map.MapLayersSheet;
import com.rutarescue.ui.shell.MainShellActivity;
import com.rutarescue.util.Formats;
import com.rutarescue.util.GeoMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Safe Route (DOCX section 4, Figma "Client / Safe Route", approved Phase 1 frames).
 *
 * The resident picks a destination (search, saved place or center) and a travel mode. RouteService
 * returns up to three alternatives assessed by the bridge-aware RiskEngine against the largest
 * flood scenario switched on in the Map layers sheet. The cards name them Lowest risk, Balanced
 * and Most direct (RouteRoleAssigner); the warning drawer lists the chosen route's hazards
 * (RouteWarningBuilder). The top route is still "Safer among available alternatives", never "safe".
 *
 * During guidance only the next turn is shown. Every minute, while online, a fresh route from the
 * resident's position is compared with the rest of the current one (RerouteAdvisor) and a clearly
 * better path replaces it quietly, with a short toast.
 *
 * Implements MapLayersSheet.Listener so the map changes as soon as a layer switch moves.
 */
public class SafeRouteFragment extends BaseFragment implements MapLayersSheet.Listener {

    private static final long SEARCH_DEBOUNCE_MS = 350; // ported from the legacy search box
    private static final long REROUTE_CHECK_MS = 60_000;
    private static final long TOAST_MS = 4_000;
    /** Share of the screen the sheet may use while routes are compared (nav bar hidden). */
    private static final float RESULT_SHEET_SHARE = 0.60f;
    private static final float DEFAULT_SHEET_SHARE = 0.55f;

    private MapController map;
    private MaxHeightScrollView sheet; // the floating Figma sheet card (scrolls inside when long)
    private EditText searchInput;
    private View resultsCard;
    private View topControls, guideCard, modeRow;
    private TextView floodTag, toast;
    private SimpleListAdapter<Locatable> resultsAdapter;
    private SimpleListAdapter<RouteAssessment> optionsAdapter;
    private View stateIdle, stateLoading, stateError, stateResult, stateGuidance;

    private final RouteRoleAssigner roleAssigner = new RouteRoleAssigner();
    private final RouteWarningBuilder warningBuilder = new RouteWarningBuilder();
    private final RerouteAdvisor rerouteAdvisor = new RerouteAdvisor();

    private MapLayerSettings layers;
    private TravelMode mode = TravelMode.WALKING;
    private FloodScenario scenario = FloodScenario.DEFAULT;
    private Locatable destination;
    private Locatable pendingDestination;
    private GeoPoint origin;
    private boolean askedPermission;
    private RouteComparison comparison;
    private List<RouteRole> roles = new ArrayList<>();
    private int selectedIndex;
    private boolean drawerOpen;
    private List<EvacuationCenter> centers = new ArrayList<>();
    private ListenerHandle structureListener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private boolean guiding;
    private RouteAssessment guidedRoute;   // the route being followed (may be replaced while guiding)
    private RouteRole guidedRole = RouteRole.LOWEST_RISK;
    private double guidedAlong;             // metres already travelled on guidedRoute
    private boolean rerouteRunning;
    private LocationListener tracker; // continuous location updates while guiding
    private final Runnable rerouteCheck = this::checkForBetterRoute;
    private final Runnable hideToast = () -> {
        if (toast != null) toast.animate().alpha(0f).setDuration(250).withEndAction(() -> toast.setVisibility(View.GONE));
    };

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
        searchInput = view.findViewById(R.id.route_search);
        resultsCard = view.findViewById(R.id.route_results_card);
        topControls = view.findViewById(R.id.route_top);
        guideCard = view.findViewById(R.id.route_guide_card);
        modeRow = view.findViewById(R.id.route_mode_row);
        floodTag = view.findViewById(R.id.route_flood_tag);
        toast = view.findViewById(R.id.route_toast);
        sheet = view.findViewById(R.id.route_sheet);

        layers = container().mapLayers.load(MapLayerPreferences.RESIDENT_MAP);
        scenario = layers.getAssessmentScenario();
        updateFloodTag();

        map = new MapController(view.findViewById(R.id.route_map), BuildConfig.TOMTOM_API_KEY);
        map.onCreate(state, ready -> {
            ready.applyLayers(layers, container().hazards, container().executors);
            ready.showCenters(centers);
            ready.setOnMapClick(point -> hideResults());
            updateStructureListener();
            if (pendingDestination != null) {
                Locatable target = pendingDestination;
                pendingDestination = null;
                setDestination(target);
            } else if (comparison != null) {
                drawComparison();
            }
        });

        setUpSearch(view);
        setUpModes(view);
        setUpButtons(view);
        setUpLists(view);

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

    /** Phase 1 travel pills: icon only, plus the short label ("Walk") on the chosen one. */
    private void setUpModes(View view) {
        ChipGroup modes = view.findViewById(R.id.route_mode_chips);
        Chip walk = view.findViewById(R.id.route_mode_walk);
        Chip moto = view.findViewById(R.id.route_mode_moto);
        Chip car = view.findViewById(R.id.route_mode_car);
        modes.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            mode = id == R.id.route_mode_moto ? TravelMode.MOTORCYCLE
                    : id == R.id.route_mode_car ? TravelMode.VEHICLE : TravelMode.WALKING;
            labelModes(walk, moto, car);
            if (destination != null) requestRoutes();
        });
        labelModes(walk, moto, car);
    }

    private void labelModes(Chip walk, Chip moto, Chip car) {
        label(walk, mode == TravelMode.WALKING, R.string.travel_walk_short);
        label(moto, mode == TravelMode.MOTORCYCLE, R.string.travel_moto_short);
        label(car, mode == TravelMode.VEHICLE, R.string.travel_car_short);
    }

    /** Chosen pill: icon + short label; the others: icon only (12 dp each side). */
    private void label(Chip chip, boolean chosen, int text) {
        float density = getResources().getDisplayMetrics().density;
        chip.setText(chosen ? getString(text) : "");
        chip.setTextStartPadding(chosen ? 6 * density : 0);
        chip.setTextEndPadding(12 * density);
    }

    private void setUpButtons(View view) {
        // Figma "Back": Safe Route is a tab, so Back returns to Home.
        view.findViewById(R.id.route_back).setOnClickListener(v -> ((MainShellActivity) requireActivity()).selectTab(0));
        view.findViewById(R.id.route_locate).setOnClickListener(v -> locate(true));
        view.findViewById(R.id.route_layers).setOnClickListener(v -> openLayers());
        floodTag.setOnClickListener(v -> openLayers());
        view.findViewById(R.id.route_retry).setOnClickListener(v -> requestRoutes());
        view.findViewById(R.id.route_clear).setOnClickListener(v -> clearRoute());
        view.findViewById(R.id.route_save_place).setOnClickListener(v -> savePlace());
        view.findViewById(R.id.route_start).setOnClickListener(v -> startGuidance());
        view.findViewById(R.id.route_start_anyway).setOnClickListener(v -> startGuidance());
        view.findViewById(R.id.route_end_guidance).setOnClickListener(v -> stopGuidance());
        view.findViewById(R.id.route_drawer_head).setOnClickListener(v -> {
            drawerOpen = !drawerOpen;
            if (comparison != null) bindDrawer(comparison.getRanked().get(selectedIndex));
        });
    }

    private void setUpLists(View view) {
        RecyclerView options = view.findViewById(R.id.route_options);
        options.setLayoutManager(new LinearLayoutManager(requireContext()));
        optionsAdapter = new SimpleListAdapter<>(R.layout.item_route_option, this::bindOption);
        options.setAdapter(optionsAdapter);
    }

    // ------------------------------------------------------------------ public API used by the shell

    /** Called from Home, Centers, saved places, or the center detail screen. */
    public void setDestination(Locatable target) {
        if (map == null || !map.isReady()) {
            pendingDestination = target;
            return;
        }
        destination = target;
        // Drop focus first and cancel a queued search, or the text change re-opens the results.
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        searchInput.clearFocus();
        searchInput.setText(target.getDisplayName());
        hideResults();
        hideKeyboard();
        requestRoutes();
    }

    public void focusSearch() {
        if (searchInput == null) return;
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    /** The shell shows the navigation bar on every tab change; hide it again if routes are open. */
    @Override
    public void onTabShown() {
        super.onTabShown();
        applyNavForState();
    }

    // ------------------------------------------------------------------ map layers

    private void openLayers() {
        MapLayersSheet.TrafficState traffic = BuildConfig.TOMTOM_API_KEY == null || BuildConfig.TOMTOM_API_KEY.isEmpty()
                ? MapLayersSheet.TrafficState.NO_KEY
                : container().connectivity.isOnline() ? MapLayersSheet.TrafficState.LIVE : MapLayersSheet.TrafficState.CACHED;
        MapLayersSheet.newInstance(layers, traffic).show(getChildFragmentManager(), MapLayersSheet.TAG);
    }

    @Override
    public void onLayersChanged(MapLayerSettings settings) {
        layers = settings;
        container().mapLayers.save(MapLayerPreferences.RESIDENT_MAP, settings);
        if (map != null && map.isReady()) map.applyLayers(settings, container().hazards, container().executors);
        updateStructureListener();
        FloodScenario newScenario = settings.getAssessmentScenario();
        if (newScenario == scenario) return;
        scenario = newScenario;
        updateFloodTag();
        // Switching the scenario never needs the internet: the cached provider answer is re-assessed.
        if (guiding) return; // the followed route keeps its assessment until guidance ends
        if (comparison != null && comparison.getCacheId() != null) {
            showState(stateLoading);
            container().routes().openCached(comparison.getCacheId(), scenario, routeCallback());
        } else if (destination != null) {
            requestRoutes();
        }
    }

    private void updateFloodTag() {
        floodTag.setText(getString(R.string.flood_tag, scenario.getLabel()));
        floodTag.setContentDescription("Routes checked against the NOAH " + scenario.getLabel()
                + " flood map. Open map layers.");
    }

    /** Bridges and tunnels (coloured by LGU status) are only loaded while their switch is on. */
    private void updateStructureListener() {
        boolean wanted = layers.isStructuresVisible() && map != null && map.isReady();
        if (wanted && structureListener == null) {
            structureListener = container().datasets().listenStructureStatuses(ui(this::onStatuses));
            track(structureListener); // also removed when the screen closes
        } else if (!wanted && structureListener != null) {
            structureListener.remove();
            structureListener = null;
        }
    }

    private void onStatuses(Map<String, StructureStatusRepository.StatusRecord> statuses) {
        container().executors.io().execute(() -> {
            try {
                StructureIndex index = container().structures.buildIndex(statuses);
                container().executors.main().execute(() -> {
                    if (map != null && map.isReady()) map.showStructures(index.getRecords());
                });
            } catch (Exception e) {
                container().executors.main().execute(() -> showError(e));
            }
        });
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
        roles = roleAssigner.assign(result.getRanked());
        selectedIndex = 0;
        showState(stateResult);
        bindSelected();
        drawComparison();
        loadCachedRoutes();
        sheet.post(() -> sheet.smoothScrollTo(0, 0)); // show the route cards first
    }

    private void drawComparison() {
        if (comparison == null || !map.isReady()) return;
        List<RouteAssessment> ranked = comparison.getRanked();
        List<String> colors = new ArrayList<>();
        List<String> pills = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            colors.add(hex(roleColor(roleAt(i))));
            pills.add(Formats.duration(ranked.get(i).getCandidate().getTravelTimeSeconds()));
        }
        map.showRoutes(ranked, selectedIndex, colors, pills);
        map.showMarkers(comparison.getOrigin(), comparison.getDestination(), null);
        // Frame the route in the visible map: below the top controls, left of the map buttons,
        // above the floating sheet. Posted so the sheet already has its final height.
        sheet.post(() -> {
            View root = getView();
            if (root == null || comparison == null || map == null || !map.isReady()) return;
            float density = getResources().getDisplayMetrics().density;
            int pad = (int) (16 * density);
            int top = topControls.getTop() + modeRow.getBottom() + pad;
            int bottom = root.getHeight() - sheet.getTop() + pad;
            List<GeoPoint> all = new ArrayList<>();
            for (RouteAssessment route : comparison.getRanked()) all.addAll(route.getCandidate().getPoints());
            map.fitTo(all, pad, top, (int) (64 * density), bottom);
        });
    }

    private RouteRole roleAt(int index) {
        return index < roles.size() ? roles.get(index) : RouteRole.ALTERNATIVE;
    }

    /** Route line and colour-key colour for each card (cyan / brand / grey). */
    private static @ColorRes int roleColor(RouteRole role) {
        switch (role) {
            case LOWEST_RISK:
            case ONLY_ROUTE: return R.color.rr_teal;
            case BALANCED: return R.color.route_balanced;
            default: return R.color.route_other;
        }
    }

    private String hex(@ColorRes int color) {
        return String.format(Locale.ROOT, "#%06X", 0xFFFFFF & ContextCompat.getColor(requireContext(), color));
    }

    private void bindSelected() {
        View view = requireView();
        List<RouteAssessment> ranked = comparison.getRanked();
        RouteAssessment route = ranked.get(selectedIndex);
        String destinationName = comparison.getDestinationName();
        ((TextView) view.findViewById(R.id.route_title)).setText(ranked.size() > 1
                ? getString(R.string.routes_title_many, ranked.size(), destinationName)
                : getString(R.string.routes_title_one, destinationName));
        ((TextView) view.findViewById(R.id.route_caption)).setText(getString(R.string.routes_caption,
                comparison.getTravelMode().getLabel(), comparison.getScenario().getLabel()));
        optionsAdapter.submit(ranked);

        List<RouteWarning> warnings = warningBuilder.build(route, comparison.getTravelMode(),
                comparison.getFreshness() == DataFreshness.LIVE);
        boolean serious = RouteWarningBuilder.hasSerious(warnings);
        drawerOpen = serious; // serious warnings open the drawer; cautions stay on one line
        bindDrawer(route);
        view.findViewById(R.id.route_start).setVisibility(serious ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.route_start_anyway).setVisibility(serious ? View.VISIBLE : View.GONE);

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
        com.rutarescue.ui.common.Badges.freshness(freshness, comparison.getFreshness(), comparison.getFetchedAt(), "");
        freshness.setText("Route data " + freshness.getText());
        ((TextView) view.findViewById(R.id.route_scenario_note)).setText(comparison.getScenario().getExplanation()
                + " Layer " + comparison.getHazardDatasetVersion() + ".");
        ((TextView) view.findViewById(R.id.route_explanation)).setText(String.join("\n\n", comparison.getExplanation()));
        ((TextView) view.findViewById(R.id.route_notes)).setText(route.getNotes().isEmpty() ? ""
                : "On this route:\n• " + String.join("\n• ", route.getNotes()));
    }

    /** One route card: name, chip, time, colour key and one plain reason. */
    private void bindOption(View row, RouteAssessment route, int position) {
        RouteRole role = roleAt(position);
        boolean chosen = position == selectedIndex;
        List<RouteWarning> warnings = warningBuilder.build(route, comparison.getTravelMode(),
                comparison.getFreshness() == DataFreshness.LIVE);
        boolean serious = RouteWarningBuilder.hasSerious(warnings);

        ((TextView) row.findViewById(R.id.option_name)).setText(role.getLabel());
        ((TextView) row.findViewById(R.id.option_time)).setText(
                Formats.duration(route.getCandidate().getTravelTimeSeconds()));
        bindChip(row.findViewById(R.id.option_chip), route, role);
        row.findViewById(R.id.option_key).setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), chosen && role == RouteRole.MOST_DIRECT
                        ? R.color.route_direct_selected : roleColor(role))));
        ((TextView) row.findViewById(R.id.option_reason)).setText(reason(route, role));

        View radio = row.findViewById(R.id.option_radio);
        radio.setBackgroundResource(chosen ? R.drawable.bg_radio_on : R.drawable.bg_radio_off);
        if (chosen && serious) {
            radio.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.rr_red)));
        } else {
            radio.setBackgroundTintList(null);
        }
        row.setBackgroundResource(!chosen ? R.drawable.bg_route_option
                : serious ? R.drawable.bg_route_option_serious : R.drawable.bg_route_option_selected);
        row.setContentDescription(role.getLabel() + ", " + Formats.duration(route.getCandidate().getTravelTimeSeconds())
                + ", " + reason(route, role) + (chosen ? ", selected" : ""));
        row.setOnClickListener(v -> {
            if (selectedIndex == position) return;
            selectedIndex = position;
            bindSelected();
            drawComparison();
        });
    }

    /** "Recommended" on the lowest-risk card; otherwise the worst hazard (or "Closed"). */
    private void bindChip(TextView chip, RouteAssessment route, RouteRole role) {
        String text;
        @ColorRes int background;
        @ColorRes int foreground;
        if (route.isClosed()) {
            text = "Closed"; background = R.color.rr_navy; foreground = R.color.white;
        } else if (role == RouteRole.LOWEST_RISK) {
            text = getString(R.string.recommended); background = R.color.rr_teal_soft; foreground = R.color.rr_step_plus_text;
        } else {
            switch (route.getMaxEffectiveHazard()) {
                case HIGH: text = "High"; background = R.color.rr_red_soft; foreground = R.color.rr_red; break;
                case MEDIUM: text = "Medium"; background = R.color.rr_orange_soft; foreground = R.color.rr_orange; break;
                case LOW: text = "Low"; background = R.color.rr_orange_soft; foreground = R.color.rr_orange; break;
                default: text = "None mapped"; background = R.color.rr_green_tint; foreground = R.color.rr_green;
            }
        }
        chip.setText(text);
        chip.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), background)));
        chip.setTextColor(ContextCompat.getColor(requireContext(), foreground));
    }

    /** One plain reason under the card, e.g. "3.0 km · 120 m in medium hazard". */
    private static String reason(RouteAssessment route, RouteRole role) {
        String distance = Formats.distance(route.getCandidate().getLengthMeters());
        if (route.isClosed()) return distance + " · crosses a reported closure";
        if (role == RouteRole.LOWEST_RISK) return distance + " · " + RouteAssessment.TOP_LABEL;
        HazardClass max = route.getMaxEffectiveHazard();
        if (max == HazardClass.NONE) return distance + " · no mapped flood hazard";
        return distance + " · " + Formats.distance(route.getExposureMeters(max)) + " in "
                + max.getLabel().toLowerCase(Locale.ROOT) + " hazard";
    }

    /** The warning drawer: one header line (tap to open) and a row per warning. */
    private void bindDrawer(RouteAssessment route) {
        View view = requireView();
        List<RouteWarning> warnings = warningBuilder.build(route, comparison.getTravelMode(),
                comparison.getFreshness() == DataFreshness.LIVE);
        boolean serious = RouteWarningBuilder.hasSerious(warnings);
        TextView title = view.findViewById(R.id.route_drawer_title);
        ImageView icon = view.findViewById(R.id.route_drawer_icon);
        ImageView chevron = view.findViewById(R.id.route_drawer_chevron);
        LinearLayout rows = view.findViewById(R.id.route_warnings);
        rows.removeAllViews();

        if (warnings.isEmpty()) {
            title.setText(R.string.warnings_none);
            icon.setImageResource(R.drawable.ic_check_circle);
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.rr_green)));
            chevron.setVisibility(View.GONE);
            rows.setVisibility(View.GONE);
            return;
        }
        icon.setImageResource(R.drawable.ic_warning);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                serious ? R.color.rr_red : R.color.rr_orange)));
        String first = warnings.get(0).getTitle();
        title.setText(warnings.size() == 1
                ? getString(R.string.warnings_one, Character.toLowerCase(first.charAt(0)) + first.substring(1))
                : getString(R.string.warnings_many, warnings.size()));
        chevron.setVisibility(View.VISIBLE);
        chevron.setRotation(drawerOpen ? -90 : 90);
        rows.setVisibility(drawerOpen ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (RouteWarning warning : warnings) {
            View row = inflater.inflate(R.layout.item_route_warning, rows, false);
            ImageView tile = row.findViewById(R.id.warning_icon);
            tile.setImageResource(warningIcon(warning.getKind()));
            @ColorRes int tint = warningTint(warning.getKind());
            tile.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), tint)));
            tile.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), warningSoft(tint))));
            ((TextView) row.findViewById(R.id.warning_title)).setText(warning.getTitle());
            ((TextView) row.findViewById(R.id.warning_detail)).setText(warning.getDetail());
            rows.addView(row);
        }
    }

    private static int warningIcon(RouteWarning.Kind kind) {
        switch (kind) {
            case UNVERIFIED_STRUCTURE: return R.drawable.ic_bridge;
            case TRAFFIC: return R.drawable.ic_car;
            case OUTSIDE_FLOOD_MAP: return R.drawable.ic_info;
            default: return R.drawable.ic_warning;
        }
    }

    private static @ColorRes int warningTint(RouteWarning.Kind kind) {
        switch (kind) {
            case CLOSURE: return R.color.rr_navy;
            case HIGH_FLOOD: return R.color.rr_red;
            case UNVERIFIED_STRUCTURE: return R.color.rr_violet;
            case OUTSIDE_FLOOD_MAP: return R.color.rr_text_secondary;
            default: return R.color.rr_orange;
        }
    }

    private static @ColorRes int warningSoft(@ColorRes int tint) {
        if (tint == R.color.rr_red) return R.color.rr_red_soft;
        if (tint == R.color.rr_violet) return R.color.rr_violet_soft;
        if (tint == R.color.rr_orange) return R.color.rr_orange_soft;
        return R.color.rr_surface_secondary;
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

    // ------------------------------------------------------------------ guidance with quiet rerouting

    /**
     * Turn-by-turn guidance: follows the phone's position continuously (LocationHelper tracking),
     * shows the next turn on the navy card and the time left in the sheet, keeps the map centred,
     * and checks for a better route every minute.
     */
    private void startGuidance() {
        if (comparison == null) return;
        guidedRoute = comparison.getRanked().get(selectedIndex);
        guidedRole = roleAt(selectedIndex);
        guidedAlong = 0;
        guiding = true;
        showState(stateGuidance);
        map.showRoutes(Collections.singletonList(guidedRoute), 0);
        container().location.getCurrent(UiCallback.of(this, this::onGuidancePosition, error ->
                ((TextView) requireView().findViewById(R.id.route_guidance_progress)).setText(UiMessages.describe(error))));
        tracker = container().location.startTracking(position -> {
            if (getView() != null) onGuidancePosition(position);
        });
        handler.postDelayed(rerouteCheck, REROUTE_CHECK_MS);
    }

    private void stopGuidance() {
        boolean wasGuiding = guiding;
        guiding = false;
        handler.removeCallbacks(rerouteCheck);
        container().location.stopTracking(tracker);
        tracker = null;
        if (wasGuiding && comparison != null) {
            showState(stateResult);
            drawComparison();
        }
    }

    private void onGuidancePosition(GeoPoint position) {
        if (!guiding || getView() == null || guidedRoute == null) return;
        origin = position;
        guidedAlong = distanceAlong(guidedRoute, position);
        double length = guidedRoute.getCandidate().getLengthMeters();
        double remaining = Math.max(0, length - guidedAlong);

        // Next turn ahead of the resident (the navy card).
        Maneuver next = null;
        for (Maneuver maneuver : guidedRoute.getCandidate().getManeuvers()) {
            if (maneuver.getRouteOffsetMeters() > guidedAlong + 10) {
                next = maneuver;
                break;
            }
        }
        View view = requireView();
        TextView turnText = view.findViewById(R.id.route_guide_text);
        TextView turnDistance = view.findViewById(R.id.route_guide_distance);
        ImageView turnIcon = view.findViewById(R.id.route_guide_icon);
        if (next == null) {
            turnText.setText(comparison == null ? getString(R.string.guide_follow) : "Arrive at " + comparison.getDestinationName());
            turnDistance.setText(getString(R.string.guide_in_distance, Formats.distance(remaining)));
            turnIcon.setImageResource(R.drawable.ic_pin);
            turnIcon.setRotation(0);
        } else {
            turnText.setText(next.describe());
            turnDistance.setText(getString(R.string.guide_in_distance,
                    Formats.distance(next.getRouteOffsetMeters() - guidedAlong)));
            turnIcon.setImageResource(R.drawable.ic_arrow_right);
            turnIcon.setRotation(turnRotation(next.getMessage()));
        }

        // Time left and arrival in the compact sheet.
        long totalSeconds = guidedRoute.getCandidate().getTravelTimeSeconds();
        long secondsLeft = length <= 0 ? 0 : Math.round(totalSeconds * (remaining / length));
        ((TextView) view.findViewById(R.id.route_guidance_time)).setText(Formats.duration(secondsLeft));
        ((TextView) view.findViewById(R.id.route_guidance_progress)).setText(getString(R.string.guide_left,
                Formats.distance(remaining), guidedRole.getLabel() + " route",
                Formats.clock(System.currentTimeMillis() + secondsLeft * 1000)));
        map.showMarkers(position, guidedRoute.getCandidate().getPoints().get(guidedRoute.getCandidate().getPoints().size() - 1), null);
        map.moveTo(position, 16);
    }

    /** The arrow points right by default: left turns turn it round, straight on points it up. */
    private static float turnRotation(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        if (text.contains("left")) return 180;
        if (text.contains("right")) return 0;
        return -90;
    }

    /**
     * Every minute while guiding and online: ask for routes from where the resident is now and
     * let RerouteAdvisor decide whether the best one clearly beats the rest of the current route.
     * Nothing is shown unless the route is replaced (Phase 1: no choices while moving).
     */
    private void checkForBetterRoute() {
        if (!guiding || destination == null || origin == null) return;
        handler.postDelayed(rerouteCheck, REROUTE_CHECK_MS);
        if (rerouteRunning || !container().connectivity.isOnline()) return;
        rerouteRunning = true;
        container().routes().findRoutes(origin, destination, mode, scenario, UiCallback.of(this, fresh -> {
            rerouteRunning = false;
            if (!guiding || fresh.getTopRoute() == null) return;
            RerouteAdvisor.Reason reason = rerouteAdvisor.compare(guidedRoute, guidedAlong, fresh.getTopRoute());
            if (reason == RerouteAdvisor.Reason.NONE) return;
            comparison = fresh;
            roles = roleAssigner.assign(fresh.getRanked());
            selectedIndex = 0;
            guidedRoute = fresh.getTopRoute();
            guidedRole = roleAt(0);
            guidedAlong = 0;
            map.showRoutes(Collections.singletonList(guidedRoute), 0);
            showToast(getString(reason == RerouteAdvisor.Reason.LOWER_RISK
                    ? R.string.reroute_lower_risk : R.string.reroute_less_traffic));
            onGuidancePosition(origin);
        }, error -> rerouteRunning = false));
    }

    private void showToast(String text) {
        handler.removeCallbacks(hideToast);
        // Sit 12 dp above the floating sheet, whatever its current height.
        View root = getView();
        if (root != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) toast.getLayoutParams();
            params.bottomMargin = root.getHeight() - sheet.getTop() + Math.round(12 * getResources().getDisplayMetrics().density);
            toast.setLayoutParams(params);
        }
        toast.setText(text);
        toast.animate().cancel();
        toast.setAlpha(1f);
        toast.setVisibility(View.VISIBLE);
        toast.bringToFront(); // draw above the sheet card
        handler.postDelayed(hideToast, TOAST_MS);
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
        boolean guidance = visible == stateGuidance;
        topControls.setVisibility(guidance ? View.GONE : View.VISIBLE);
        guideCard.setVisibility(guidance ? View.VISIBLE : View.GONE);
        if (!guidance) toast.setVisibility(View.GONE);
        applyNavForState();
    }

    /**
     * Phase 1: the navigation bar hides while routes are compared and during guidance; the sheet
     * then sits 16 dp above the bottom edge and may grow taller.
     */
    private void applyNavForState() {
        if (stateResult == null || !(getActivity() instanceof MainShellActivity)) return;
        boolean focus = stateResult.getVisibility() == View.VISIBLE || stateGuidance.getVisibility() == View.VISIBLE;
        if (isVisible()) ((MainShellActivity) requireActivity()).setBottomNavHidden(focus);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        params.bottomMargin = focus ? Math.round(16 * getResources().getDisplayMetrics().density)
                : getResources().getDimensionPixelSize(R.dimen.bottom_nav_space);
        sheet.setLayoutParams(params);
        sheet.setMaxShareOfParent(stateResult.getVisibility() == View.VISIBLE ? RESULT_SHEET_SHARE : DEFAULT_SHEET_SHARE);
        sheet.setMaxHeight(stateResult.getVisibility() == View.VISIBLE ? Integer.MAX_VALUE
                : Math.round(300 * getResources().getDisplayMetrics().density));
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
