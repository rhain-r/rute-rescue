package com.rutarescue.ui.resident.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.rutarescue.R;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.place.SavedPlace;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.service.AdvisoryService;
import com.rutarescue.service.CenterService;
import com.rutarescue.ui.about.AboutActivity;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
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
 * saved places, the nearest verified center, LGU updates and data-source timestamps.
 * Cached, stale, prepared and live information are labelled differently.
 */
public class HomeFragment extends BaseFragment {

    private TextView heroEyebrow;
    private TextView greeting;
    private TextView heroBody;
    private TextView heroFreshness;
    private View nearbyCard;
    private TextView noCenter;
    private LinearLayout advisoryList;
    private ChipGroup savedChips;
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

        MainShellActivity shell = (MainShellActivity) requireActivity();
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        view.findViewById(R.id.header_logo).setOnClickListener(v -> startActivity(new Intent(requireContext(), AboutActivity.class)));
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
        showHero(null, null);

        track(container().advisories().listenPublished(ui(this::onAdvisories)));
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
        Badges.freshness(requireView().findViewById(R.id.home_source_advisories), data.getFreshness(),
                data.getRetrievedAt(), "");
        UserProfile profile = profile();
        Advisory latest = AdvisoryService.latestFor(data.getData(), profile == null ? null : profile.getCity());
        showHero(latest, data);

        advisoryList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int shown = 0;
        for (Advisory advisory : data.getData()) {
            if (shown++ == 3) break;
            View card = inflater.inflate(R.layout.item_advisory, advisoryList, false);
            ViewBinders.bindAdvisory(card, advisory);
            advisoryList.addView(card);
        }
        if (data.getData().isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No LGU updates yet.");
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            advisoryList.addView(empty);
        }
    }

    private void showHero(Advisory latest, SourcedData<List<Advisory>> data) {
        if (latest == null) {
            heroEyebrow.setText(R.string.no_advisory_eyebrow);
            heroBody.setText(R.string.no_advisory_body);
            Badges.freshness(heroFreshness, DataFreshness.PREPARED, 0, "Project NOAH scenarios");
            return;
        }
        heroEyebrow.setText(latest.getLevel().getLabel() + " • " + latest.getCity().getDisplayName());
        heroBody.setText(latest.getTitle() + ". " + latest.getMessage());
        Badges.freshness(heroFreshness, data.getFreshness(), data.getRetrievedAt(), "");
    }

    private void onCenters(SourcedData<List<EvacuationCenter>> data) {
        centers = data.getData();
        Badges.freshness(requireView().findViewById(R.id.home_source_centers), data.getFreshness(),
                data.getRetrievedAt(), "");
        bindNearest();
    }

    private void bindNearest() {
        if (getView() == null) return;
        EvacuationCenter nearest = CenterService.nearest(centers, origin);
        if (nearest == null) {
            nearbyCard.setVisibility(View.GONE);
            noCenter.setVisibility(View.VISIBLE);
            return;
        }
        nearbyCard.setVisibility(View.VISIBLE);
        noCenter.setVisibility(View.GONE);
        ViewBinders.bindCenter(nearbyCard, nearest, origin);
        nearbyCard.setOnClickListener(v -> startActivity(CenterDetailActivity.intent(requireContext(), nearest.getId())));
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
