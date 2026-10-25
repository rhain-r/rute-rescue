package com.rutarescue.ui.resident.home;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rutarescue.R;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.contact.Hotline;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.place.SavedPlace;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.service.AdvisoryService;
import com.rutarescue.service.CenterService;
import com.rutarescue.ui.common.AlertStrip;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.InfoTooltip;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.common.ViewBinders;
import com.rutarescue.ui.resident.centers.CenterDetailActivity;
import com.rutarescue.ui.shell.MainShellActivity;
import com.rutarescue.util.Formats;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Resident Home (Figma "Client / Home"): latest LGU advisory, search, quick actions,
 * saved places, the nearest center (LGU-published or from the bundled PLM SSC list), emergency
 * hotlines (the resident's city first, then national), LGU updates and data-source timestamps.
 * Cached, stale, prepared and live information are labelled differently.
 */
public class HomeFragment extends BaseFragment {

    /** National hotlines shown under the city's own before "See all" (911 and the Red Cross). */
    private static final int NATIONAL_FOLDED = 2;

    private TextView heroEyebrow;
    private TextView greeting;
    private TextView heroBody;
    private TextView heroFreshness;
    private View nearbyCard;
    private TextView noCenter;
    private LinearLayout advisoryList;
    /** Null until the first advisories arrive (so "No LGU updates yet" never flashes while loading). */
    private List<Advisory> latestAdvisories;
    private ChipGroup savedChips;
    private LinearLayout hotlineList;
    private TextView hotlineToggle;
    private boolean showAllHotlines;
    private List<EvacuationCenter> centers = new ArrayList<>();
    private GeoPoint origin;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        heroEyebrow = view.findViewById(R.id.home_hero_eyebrow);
        greeting = view.findViewById(R.id.home_greeting);
        heroBody = view.findViewById(R.id.home_hero_body);
        heroFreshness = view.findViewById(R.id.home_hero_freshness);
        nearbyCard = view.findViewById(R.id.home_nearby_center);
        noCenter = view.findViewById(R.id.home_no_center);
        advisoryList = view.findViewById(R.id.home_advisories);
        savedChips = view.findViewById(R.id.home_saved_chips);
        hotlineList = view.findViewById(R.id.home_hotlines);
        hotlineToggle = view.findViewById(R.id.home_hotlines_toggle);
        hotlineToggle.setOnClickListener(v -> {
            showAllHotlines = !showAllHotlines;
            bindHotlines();
        });
        bindHotlines();

        MainShellActivity shell = (MainShellActivity) requireActivity();
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        view.findViewById(R.id.home_find_route).setOnClickListener(v -> shell.selectTab(MainShellActivity.TAB_ROUTE));
        view.findViewById(R.id.home_search).setOnClickListener(v -> shell.openRouteSearch());
        view.findViewById(R.id.home_qa_route).setOnClickListener(v -> shell.selectTab(MainShellActivity.TAB_ROUTE));
        view.findViewById(R.id.home_qa_centers).setOnClickListener(v -> shell.selectTab(MainShellActivity.TAB_CENTERS));
        view.findViewById(R.id.home_qa_family).setOnClickListener(v -> shell.selectTab(MainShellActivity.TAB_RECORD));
        view.findViewById(R.id.home_see_all).setOnClickListener(v -> shell.selectTab(MainShellActivity.TAB_CENTERS));

        UserProfile profile = profile();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(String.format(Locale.US, "%s, %s.\n%s", Formats.greeting(hour),
                profile == null ? "there" : profile.getFirstName(), getString(R.string.stay_ready)));

        bindNoahSource(view.findViewById(R.id.home_source_noah));
        showHero();
        // Info tooltips so the prepared risk zones are not read as a weather update (and vice versa)
        InfoTooltip.attach(view.findViewById(R.id.home_hero_info), R.string.tip_advisories_title, R.string.tip_advisories_body);
        InfoTooltip.attach(view.findViewById(R.id.home_source_advisories_info), R.string.tip_advisories_title,
                R.string.tip_advisories_body);
        InfoTooltip.attach(view.findViewById(R.id.home_source_noah_info), R.string.tip_risk_zones_title,
                R.string.tip_risk_zones_body);

        track(container().advisories().listenPublished(ui(this::onAdvisories)));
        // The city's alert level, at the top of Home; only the city's LGU sets it.
        MetroManilaCity city = profile == null ? null : profile.getCity();
        track(container().alerts().listen(city, ui(alert ->
                AlertStrip.bind(requireView().findViewById(R.id.alert_strip), city, alert, null))));
        track(container().centers().listenPublished(ui(this::onCenters)));
        if (profile != null) {
            track(container().savedPlaces().listen(profile.getUid(), ui(this::onSavedPlaces)));
        }
        if (container().location.hasPermission()) {
            container().location.getCurrent(UiCallback.of(this, point -> {
                origin = point;
                bindNearest();
            }, ignored -> { }));
        }
    }

    private void bindNoahSource(TextView chip) {
        String version = container().hazards.getManifest().versionOf(FloodScenario.DEFAULT);
        Badges.freshness(chip, DataFreshness.PREPARED, 0, version + " • 5/25/100-year scenarios");
    }

    private void onAdvisories(SourcedData<List<Advisory>> data) {
        latestAdvisories = data.getData();
        Badges.freshness(requireView().findViewById(R.id.home_source_advisories), data.getFreshness(),
                data.getRetrievedAt(), "");
        showAdvisoryList();
    }

    /** Saving or un-saving in Notifications changes the "Saved" label here when Home comes back. */
    @Override
    public void onResume() {
        super.onResume();
        showAdvisoryList();
    }

    /**
     * Only ONE announcement on Home (2026-10-25): the newest one of the last 7 days, of the
     * resident's city (or the newest of all when the city has none). The others and the saved ones
     * are in Notifications (the header bell, 30 days). The city's alert level is a separate line above it.
     */
    private void showAdvisoryList() {
        if (getView() == null || latestAdvisories == null) return;
        advisoryList.removeAllViews();
        UserProfile profile = profile();
        Advisory newest = AdvisoryService.latestThisWeek(latestAdvisories, profile == null ? null : profile.getCity(),
                false, System.currentTimeMillis());
        if (newest == null) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.no_announcement_this_week);
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            advisoryList.addView(empty);
            return;
        }
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_advisory, advisoryList, false);
        ViewBinders.bindAdvisory(card, newest, this::showAdvisoryList);
        advisoryList.addView(card);
    }

    /**
     * The hero under the advisories: the resident's city, the greeting and the prepared-data note.
     * Since 2026-10-22 it no longer repeats the newest advisory, which is right above it.
     */
    private void showHero() {
        UserProfile profile = profile();
        heroEyebrow.setText(getString(R.string.home_hero_eyebrow,
                profile == null || profile.getCity() == null ? "Metro Manila" : profile.getCity().getDisplayName()));
        heroBody.setText(R.string.no_advisory_body);
        Badges.freshness(heroFreshness, DataFreshness.PREPARED, 0, "Project NOAH scenarios");
    }

    private void onCenters(SourcedData<List<EvacuationCenter>> data) {
        centers = data.getData();
        Badges.freshness(requireView().findViewById(R.id.home_source_centers), data.getFreshness(),
                data.getRetrievedAt(), data.getSource());
        bindNearest();
    }

    private void bindNearest() {
        if (getView() == null) return;
        // Measured from the phone's location; until it is known, from the resident's sign-up city
        // hall, so every city (not just Manila) gets its own nearest center. Distances are shown
        // only for a real location.
        UserProfile profile = profile();
        GeoPoint from = origin != null ? origin
                : profile != null && profile.getCity() != null ? profile.getCity().getHallLocation() : null;
        EvacuationCenter nearest = CenterService.nearest(centers, from);
        if (nearest == null) {
            // Never show a "nearby" center that is not: wait for the location (or ask for it).
            nearbyCard.setVisibility(View.GONE);
            noCenter.setVisibility(View.VISIBLE);
            noCenter.setText(from != null ? R.string.no_center_yet
                    : container().location.hasPermission() ? R.string.finding_nearest_center
                    : R.string.nearest_needs_location);
            return;
        }
        nearbyCard.setVisibility(View.VISIBLE);
        noCenter.setVisibility(View.GONE);
        ViewBinders.bindCenter(nearbyCard, nearest, origin);
        nearbyCard.setOnClickListener(v -> startActivity(CenterDetailActivity.intent(requireContext(), nearest.getId())));
    }

    /**
     * Emergency hotlines card: the hotline of the resident's own city (the city chosen at sign-up)
     * first, then the national lines. Folded, it shows the city lines plus 911 and the Red Cross;
     * "See all" shows every national line too.
     */
    private void bindHotlines() {
        UserProfile profile = profile();
        List<Hotline> cityLines = profile == null || profile.getCity() == null ? null
                : container().cityHotlines.get(profile.getCity());
        List<Hotline> all = new ArrayList<>();
        if (cityLines != null) all.addAll(cityLines);
        all.addAll(container().hotlines);
        int folded = (cityLines == null ? 0 : cityLines.size()) + NATIONAL_FOLDED;

        hotlineList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int shown = showAllHotlines ? all.size() : Math.min(folded, all.size());
        for (int i = 0; i < shown; i++) {
            Hotline hotline = all.get(i);
            View row = inflater.inflate(R.layout.item_simple_row, hotlineList, false);
            ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(R.drawable.ic_phone);
            ((TextView) row.findViewById(R.id.row_title)).setText(hotline.getAgency());
            ((TextView) row.findViewById(R.id.row_subtitle)).setText(hotline.getNumbersText());
            row.setOnClickListener(v -> call(hotline));
            hotlineList.addView(row);
        }
        hotlineToggle.setVisibility(all.size() > folded ? View.VISIBLE : View.GONE);
        hotlineToggle.setText(showAllHotlines ? R.string.show_less : R.string.see_all);
    }

    /** One number opens the dialer at once; several numbers ask which one first. */
    private void call(Hotline hotline) {
        List<String> numbers = hotline.getNumbers();
        if (numbers.size() == 1) {
            dial(numbers.get(0));
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(hotline.getAgency())
                .setItems(numbers.toArray(new String[0]), (dialog, which) -> dial(numbers.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Opens the phone's dialer with the number filled in; the resident still presses Call. */
    private void dial(String printedNumber) {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Hotline.dialDigits(printedNumber)));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException noDialer) {
            showMessage(getString(R.string.no_dialer));
        }
    }

    private void onSavedPlaces(List<SavedPlace> places) {
        View view = requireView();
        int visibility = places.isEmpty() ? View.GONE : View.VISIBLE;
        view.findViewById(R.id.home_saved_title).setVisibility(visibility);
        view.findViewById(R.id.home_saved_scroll).setVisibility(visibility);
        savedChips.removeAllViews();
        MainShellActivity shell = (MainShellActivity) requireActivity();
        for (SavedPlace place : places) {
            Chip chip = new Chip(requireContext());
            chip.setText(place.getDisplayName());
            chip.setCheckable(false);
            chip.setChipIconResource(R.drawable.ic_pin);
            chip.setOnClickListener(v -> shell.openRouteTo(place));
            savedChips.addView(chip);
        }
    }
}
