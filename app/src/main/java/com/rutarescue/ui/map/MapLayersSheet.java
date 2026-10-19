package com.rutarescue.ui.map;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.rutarescue.R;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.map.BaseMap;
import com.rutarescue.model.map.MapLayerSettings;
import com.rutarescue.ui.common.InfoTooltip;

/**
 * Phase 1 "Map layers" sheet. The resident chooses one base map and switches each flood
 * scenario and overlay on or off. Every change is sent to the screen at once (Listener), so the
 * map updates behind the sheet; "Done" only closes it and "Reset" restores the defaults.
 *
 * The screen that opens the sheet (SafeRouteFragment or LguHazardMapFragment) implements
 * Listener and shows it with getChildFragmentManager(), so the link survives rotation.
 */
public class MapLayersSheet extends BottomSheetDialogFragment {

    /** Implemented by the map screen that opened the sheet. */
    public interface Listener {
        void onLayersChanged(MapLayerSettings settings);
    }

    /** What the traffic row can say. */
    public enum TrafficState { LIVE, CACHED, NO_KEY }

    public static final String TAG = "map_layers";

    private static final String ARG_SETTINGS = "settings";
    private static final String ARG_TRAFFIC = "traffic";

    private static final BaseMap[] BASE_MAPS = {BaseMap.STANDARD, BaseMap.TOPOGRAPHIC, BaseMap.SATELLITE};
    private static final int[] BASE_THUMBS = {R.drawable.thumb_map_standard, R.drawable.thumb_map_topo,
            R.drawable.thumb_map_satellite};

    private MapLayerSettings settings;
    private TrafficState trafficState;

    public static MapLayersSheet newInstance(MapLayerSettings current, TrafficState traffic) {
        MapLayersSheet sheet = new MapLayersSheet();
        Bundle args = new Bundle();
        args.putString(ARG_SETTINGS, current.encode());
        args.putString(ARG_TRAFFIC, traffic.name());
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_RR_LayersSheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_map_layers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Bundle args = requireArguments();
        settings = MapLayerSettings.decode(state != null ? state.getString(ARG_SETTINGS) : args.getString(ARG_SETTINGS));
        trafficState = TrafficState.valueOf(args.getString(ARG_TRAFFIC, TrafficState.NO_KEY.name()));
        view.findViewById(R.id.layers_close).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.layers_done).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.layers_reset).setOnClickListener(v -> {
            settings = MapLayerSettings.defaults();
            bind(view);
            notifyChanged();
        });
        // The "Prepared, not live" chip and its "i" both explain that risk zones are not weather.
        InfoTooltip.attach(view.findViewById(R.id.layers_flood_info), R.string.tip_risk_zones_title,
                R.string.tip_risk_zones_body);
        InfoTooltip.attach(view.findViewById(R.id.layers_prepared_chip), R.string.tip_risk_zones_title,
                R.string.tip_risk_zones_body);
        bind(view);
    }

    /** Opens fully expanded, like the Figma frame (the sheet is taller than half the screen). */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (settings != null) outState.putString(ARG_SETTINGS, settings.encode());
    }

    // ------------------------------------------------------------------ binding

    private void bind(View view) {
        bindBaseMaps(view.findViewById(R.id.layers_bases));

        LinearLayout floods = view.findViewById(R.id.layers_floods);
        floods.removeAllViews();
        addFloodRow(floods, FloodScenario.FIVE_YEAR, R.color.rr_swatch_5yr, R.color.hazard_medium);
        addFloodRow(floods, FloodScenario.TWENTY_FIVE_YEAR, R.color.rr_swatch_25yr, R.color.rr_hazard_25yr);
        addFloodRow(floods, FloodScenario.HUNDRED_YEAR, R.color.rr_swatch_100yr, R.color.hazard_high);

        LinearLayout overlays = view.findViewById(R.id.layers_overlays);
        overlays.removeAllViews();
        addRow(overlays, R.drawable.ic_center, R.color.rr_teal_soft, R.color.rr_teal_green,
                getString(R.string.sheet_layer_centers), getString(R.string.layer_centers_caption), null,
                settings.isCentersVisible(), true, checked -> settings.setCentersVisible(checked));
        addRow(overlays, R.drawable.ic_bridge, R.color.rr_violet_soft, R.color.rr_violet,
                getString(R.string.sheet_layer_structures), getString(R.string.layer_structures_caption), null,
                settings.isStructuresVisible(), true, checked -> settings.setStructuresVisible(checked));
        boolean hasTraffic = trafficState != TrafficState.NO_KEY;
        addRow(overlays, R.drawable.ic_car, R.color.rr_red_soft, R.color.rr_red,
                getString(R.string.layer_traffic),
                getString(hasTraffic ? R.string.layer_traffic_caption : R.string.layer_traffic_no_key),
                hasTraffic ? trafficState : null, settings.isTrafficVisible() && hasTraffic, hasTraffic,
                checked -> settings.setTrafficVisible(checked));
    }

    private void bindBaseMaps(LinearLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int gap = Math.round(10 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < BASE_MAPS.length; i++) {
            BaseMap baseMap = BASE_MAPS[i];
            View tile = inflater.inflate(R.layout.item_base_map, container, false);
            boolean chosen = settings.getBaseMap() == baseMap;
            ((ImageView) tile.findViewById(R.id.base_thumb)).setImageResource(BASE_THUMBS[i]);
            ((TextView) tile.findViewById(R.id.base_label)).setText(baseMap.getLabel());
            tile.findViewById(R.id.base_frame).setBackgroundResource(chosen ? R.drawable.bg_base_selected : 0);
            tile.findViewById(R.id.base_check).setVisibility(chosen ? View.VISIBLE : View.GONE);
            tile.setContentDescription(baseMap.getLabel() + (chosen ? ", selected" : ""));
            if (i > 0) ((LinearLayout.LayoutParams) tile.getLayoutParams()).setMarginStart(gap);
            tile.setOnClickListener(v -> {
                if (settings.getBaseMap() == baseMap) return;
                settings.setBaseMap(baseMap);
                bindBaseMaps(container);
                notifyChanged();
            });
            container.addView(tile);
        }
    }

    private void addFloodRow(LinearLayout group, FloodScenario scenario, @ColorRes int swatch, @ColorRes int tint) {
        addRow(group, R.drawable.ic_layers_two, swatch, tint, getString(R.string.flood_tag, scenario.getLabel()),
                scenario.getAnnualChancePercent() + "% chance in any year", null,
                settings.isFloodVisible(scenario), true, checked -> settings.setFloodVisible(scenario, checked));
    }

    /** Small callback for a switch row (keeps addRow() readable). */
    private interface Toggle {
        void apply(boolean checked);
    }

    private void addRow(LinearLayout group, @DrawableRes int icon, @ColorRes int swatch, @ColorRes int tint,
                        String title, String caption, @Nullable TrafficState chip, boolean checked, boolean enabled,
                        Toggle toggle) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_layer_row, group, false);
        ImageView swatchView = row.findViewById(R.id.layer_swatch);
        swatchView.setImageResource(icon);
        swatchView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), swatch)));
        swatchView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), tint)));
        ((TextView) row.findViewById(R.id.layer_title)).setText(title);
        ((TextView) row.findViewById(R.id.layer_caption)).setText(caption);
        TextView chipView = row.findViewById(R.id.layer_chip);
        if (chip != null) {
            chipView.setVisibility(View.VISIBLE);
            boolean live = chip == TrafficState.LIVE;
            chipView.setText(live ? "Live" : "Cached");
            chipView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                    live ? R.color.rr_green_tint : R.color.rr_surface_secondary)));
            chipView.setTextColor(ContextCompat.getColor(requireContext(), live ? R.color.rr_green : R.color.rr_text_secondary));
        }
        MaterialSwitch toggleView = row.findViewById(R.id.layer_switch);
        toggleView.setChecked(checked);
        toggleView.setEnabled(enabled);
        toggleView.setContentDescription(title);
        toggleView.setOnCheckedChangeListener((button, isChecked) -> {
            toggle.apply(isChecked);
            notifyChanged();
        });
        row.setOnClickListener(v -> {
            if (toggleView.isEnabled()) toggleView.toggle();
        });
        if (group.getChildCount() > 0) {
            View divider = new View(requireContext());
            divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.rr_divider));
            group.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        group.addView(row);
    }

    private void notifyChanged() {
        if (getParentFragment() instanceof Listener) {
            ((Listener) getParentFragment()).onLayersChanged(settings.copy());
        }
    }
}
