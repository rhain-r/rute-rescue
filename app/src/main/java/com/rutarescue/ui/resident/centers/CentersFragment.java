package com.rutarescue.ui.resident.centers;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.rutarescue.BuildConfig;
import com.rutarescue.R;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.service.CenterService;
import com.rutarescue.ui.about.AboutActivity;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.SimpleListAdapter;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.common.ViewBinders;
import com.rutarescue.ui.map.MapController;
import com.rutarescue.ui.resident.home.InboxActivity;
import com.rutarescue.util.GeoMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Evacuation centers (DOCX section 4 "Centers"): search and filter LGU-published centers
 * by proximity, operating status and accessibility. Works offline from the cache, and
 * never presents capacity as live.
 */
public class CentersFragment extends BaseFragment {

    private SimpleListAdapter<EvacuationCenter> adapter;
    private EditText search;
    private Chip nearby;
    private Chip open;
    private Chip accessible;
    private TextView empty;
    private MapController map;
    private List<EvacuationCenter> all = new ArrayList<>();
    private List<EvacuationCenter> shown = new ArrayList<>();
    private GeoPoint origin;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_centers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        view.findViewById(R.id.header_logo).setOnClickListener(v -> startActivity(new Intent(requireContext(), AboutActivity.class)));

        search = view.findViewById(R.id.centers_search);
        nearby = view.findViewById(R.id.centers_filter_nearby);
        open = view.findViewById(R.id.centers_filter_open);
        accessible = view.findViewById(R.id.centers_filter_accessible);
        empty = view.findViewById(R.id.centers_empty);

        RecyclerView list = view.findViewById(R.id.centers_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SimpleListAdapter<>(R.layout.item_center, (row, center, position) -> {
            ViewBinders.bindCenter(row, center, origin);
            row.setOnClickListener(v -> startActivity(CenterDetailActivity.intent(requireContext(), center.getId())));
        });
        list.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilters();
            }
        });
        for (Chip chip : new Chip[]{nearby, open, accessible}) {
            chip.setOnCheckedChangeListener((button, checked) -> applyFilters());
        }

        map = new MapController(view.findViewById(R.id.centers_map), BuildConfig.TOMTOM_API_KEY);
        map.onCreate(state, ready -> {
            ready.setHazardVisible(false);
            ready.showCenters(shown);
            ready.setOnMapClick(this::openNearestCenterTo);
        });
        View scroll = view.findViewById(R.id.centers_scroll);
        View mapMode = view.findViewById(R.id.centers_map_mode);
        view.findViewById(R.id.centers_show_map).setOnClickListener(v -> {
            scroll.setVisibility(View.GONE);
            mapMode.setVisibility(View.VISIBLE);
            if (map.isReady()) {
                map.showCenters(shown);
                if (origin != null) map.moveTo(origin, 13);
            }
        });
        view.findViewById(R.id.centers_show_list).setOnClickListener(v -> {
            mapMode.setVisibility(View.GONE);
            scroll.setVisibility(View.VISIBLE);
        });

        track(container().centers().listenPublished(ui(this::onCenters)));
        if (container().location.hasPermission()) {
            container().location.getCurrent(UiCallback.of(this, point -> {
                origin = point;
                // Figma "Center Filters": "Nearby" is the selected filter once a location is known.
                if (!nearby.isChecked()) nearby.setChecked(true); // the chip listener re-applies the filters
                else applyFilters();
            }, ignored -> nearby.setEnabled(false)));
        } else {
            nearby.setText("Nearby (needs location)");
        }
    }

    private void onCenters(SourcedData<List<EvacuationCenter>> data) {
        all = data.getData();
        Badges.freshness(requireView().findViewById(R.id.centers_freshness), data.getFreshness(),
                data.getRetrievedAt(), "");
        applyFilters();
    }

    private void applyFilters() {
        if (adapter == null) return;
        shown = CenterService.filter(all, search.getText().toString(), nearby.isChecked(), open.isChecked(),
                accessible.isChecked(), origin);
        adapter.submit(shown);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(all.isEmpty() ? getString(R.string.no_center_yet) : "No center matches these filters.");
        if (map != null && map.isReady()) map.showCenters(shown);
    }

    private void openNearestCenterTo(GeoPoint tapped) {
        EvacuationCenter best = null;
        double bestDistance = 120; // metres of tap tolerance
        for (EvacuationCenter center : shown) {
            double d = GeoMath.distanceMeters(center.getLocation(), tapped);
            if (d < bestDistance) {
                best = center;
                bestDistance = d;
            }
        }
        if (best != null) startActivity(CenterDetailActivity.intent(requireContext(), best.getId()));
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
        if (map != null) map.onDestroy();
        map = null;
        super.onDestroyView();
    }
}
