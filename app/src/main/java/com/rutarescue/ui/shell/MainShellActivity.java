package com.rutarescue.ui.shell;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.rutarescue.R;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.Locatable;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.place.PlaceResult;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.ConnectivityMonitor;
import com.rutarescue.ui.auth.LoginActivity;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.ScreenFit;
import com.rutarescue.ui.lgu.data.LguDataFragment;
import com.rutarescue.ui.lgu.finalize.FinalizeFragment;
import com.rutarescue.ui.lgu.hazardmap.LguHazardMapFragment;
import com.rutarescue.ui.lgu.overview.LguOverviewFragment;
import com.rutarescue.ui.lgu.reviews.LguReviewsFragment;
import com.rutarescue.ui.moderator.ModeratorCentersFragment;
import com.rutarescue.ui.moderator.ModeratorOverviewFragment;
import com.rutarescue.ui.resident.centers.CentersFragment;
import com.rutarescue.ui.resident.home.HomeFragment;
import com.rutarescue.ui.resident.record.MyRecordFragment;
import com.rutarescue.ui.resident.route.SafeRouteFragment;
import com.rutarescue.ui.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * The single authenticated shell (DOCX section 6). It chooses the tabs from the signed-in
 * profile: Resident (Home, Route, Centers, Record), Moderator (Overview, Centers, Reviews - since
 * 2026-10-22 the moderator runs one center and has no map or data tab) or LGU (Overview, Map,
 * Finalize, Data). Every tab's header has a Settings gear. Hiding tabs is only for convenience:
 * the database rules still block anything a role is not allowed to do.
 */
public class MainShellActivity extends BaseActivity implements ConnectivityMonitor.Listener {

    public static final int TAB_ROUTE = 1;
    public static final int TAB_CENTERS = 2;
    public static final int TAB_RECORD = 3;

    private static final String EXTRA_DEST_LAT = "dest_lat";
    private static final String EXTRA_DEST_LON = "dest_lon";
    private static final String EXTRA_DEST_NAME = "dest_name";
    private static final String STATE_TAB = "tab";
    private static final int REQUEST_NOTIFICATIONS = 52;
    private boolean askedForNotifications;
    private static final String EXTRA_FOCUS_LAT = "focus_lat";
    private static final String EXTRA_FOCUS_LON = "focus_lon";
    private static final String EXTRA_FOCUS_NAME = "focus_name";
    /** The staff Map tab. */
    private static final int TAB_STAFF_MAP = 1;

    private final Fragment[] tabs = new Fragment[4];
    private BottomNav nav;
    private View offlineBanner;
    /** City staff (moderator or LGU officer) get the staff tabs. */
    private boolean staff;
    /** Moderators get three tabs: Overview (their center), Centers and Reviews. */
    private boolean moderator;
    private int currentTab = -1;
    private boolean keyboardOpen;
    /** Residents: follows their Ligtas applications for phone notifications (2026-10-20). */
    private ListenerHandle ligtasListener = ListenerHandle.NONE;
    /** The resident's latest applications, announced again once notifications are allowed. */
    private List<LigtasApplication> latestApplications = new ArrayList<>();
    private boolean navHiddenByScreen;

    /** Intent that opens the Safe Route tab with a destination (used by detail screens). */
    public static Intent routeIntent(Context context, Locatable destination) {
        Intent intent = new Intent(context, MainShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_DEST_LAT, destination.getLocation().getLatitude());
        intent.putExtra(EXTRA_DEST_LON, destination.getLocation().getLongitude());
        intent.putExtra(EXTRA_DEST_NAME, destination.getDisplayName());
        return intent;
    }

    /** Intent that opens the staff Map tab zoomed to one place (a center's "Show on map"). */
    public static Intent staffMapIntent(Context context, Locatable place) {
        Intent intent = new Intent(context, MainShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_FOCUS_LAT, place.getLocation().getLatitude());
        intent.putExtra(EXTRA_FOCUS_LON, place.getLocation().getLongitude());
        intent.putExtra(EXTRA_FOCUS_NAME, place.getDisplayName());
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (container().session() == null || container().session().getProfile() == null) {
            // The process was restarted: go back through the launcher to restore the login.
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main_shell);
        applyShellInsets();
        offlineBanner = findViewById(R.id.shell_offline_banner);
        staff = container().session().isStaff();
        moderator = container().session().isModerator();

        int[] icons;
        int[] labels;
        if (moderator) {
            icons = new int[]{R.drawable.ic_overview, R.drawable.ic_center, R.drawable.ic_reviews};
            labels = new int[]{R.string.nav_overview, R.string.nav_centers, R.string.nav_reviews};
        } else if (staff) {
            icons = new int[]{R.drawable.ic_overview, R.drawable.ic_map, R.drawable.ic_reviews, R.drawable.ic_data};
            labels = new int[]{R.string.nav_overview, R.string.nav_map, R.string.nav_finalize, R.string.nav_data};
        } else {
            icons = new int[]{R.drawable.ic_home, R.drawable.ic_route, R.drawable.ic_center, R.drawable.ic_record};
            labels = new int[]{R.string.nav_home, R.string.nav_route, R.string.nav_centers, R.string.nav_record};
        }
        nav = new BottomNav(findViewById(R.id.bottom_nav), icons, labels, this::selectTab);
        // Fit every tab to this phone: content under the real header height, above the real bottom tabs,
        // and the offline banner under whatever is at the top. Re-applied on each layout (cheap, see ScreenFit).
        View root = findViewById(R.id.shell_root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::fitTabs);

        FragmentManager manager = getSupportFragmentManager();
        for (int i = 0; i < tabs.length; i++) tabs[i] = manager.findFragmentByTag(tag(i));
        selectTab(savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_TAB, 0));
        handleIntent(getIntent());
        if (!staff) followLigtasDecisions();
    }

    /**
     * While the app is open, a moderator's decision shows up as a phone notification at once
     * (the hourly SyncWorker covers the time the app is closed). Android 13+ asks the resident
     * for permission the first time they have an application.
     */
    private void followLigtasDecisions() {
        String uid = container().session().getProfile().getUid();
        ligtasListener = container().ligtas().listenMine(uid, new Callback<SourcedData<List<LigtasApplication>>>() {
            @Override
            public void onSuccess(SourcedData<List<LigtasApplication>> data) {
                if (!data.getData().isEmpty() && Build.VERSION.SDK_INT >= 33
                        && ContextCompat.checkSelfPermission(MainShellActivity.this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED && !askedForNotifications) {
                    askedForNotifications = true;
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
                }
                latestApplications = data.getData();
                container().ligtasNotifier.announce(latestApplications);
            }

            @Override
            public void onError(Exception error) {
                // Notifications are a convenience; the status screen still shows everything.
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) container().ligtasNotifier.announce(latestApplications);
    }

    @Override
    protected void onDestroy() {
        ligtasListener.remove();
        super.onDestroy();
    }

    /**
     * Figma draws the navigation bar flush with the bottom edge of the screen. The status bar
     * keeps its own space at the top; at the bottom the glass bar grows by the gesture-bar
     * height, so its 58 dp items stay clear of the system handle. While the keyboard is open the
     * bar is hidden and the content is lifted above the keyboard instead.
     */
    private void applyShellInsets() {
        View root = findViewById(R.id.shell_root);
        View content = findViewById(R.id.shell_content);
        View bar = findViewById(R.id.bottom_nav);
        int navBottomPadding = bar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            keyboardOpen = ime.bottom > bars.bottom;
            view.setPadding(bars.left, bars.top, bars.right, keyboardOpen ? ime.bottom : 0);
            content.setPadding(0, 0, 0, keyboardOpen ? 0 : bars.bottom);
            bar.setPadding(bar.getPaddingLeft(), bar.getPaddingTop(), bar.getPaddingRight(),
                    navBottomPadding + bars.bottom);
            updateNavVisibility();
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * How much of a tab the bottom tabs cover, plus a 16 dp gap: content and map cards end here.
     * Measured on the phone (large text makes the tabs taller), never a fixed number.
     */
    public int bottomNavSpace() {
        View bar = findViewById(R.id.bottom_nav);
        int gap = Math.round(16 * getResources().getDisplayMetrics().density);
        if (bar.getVisibility() != View.VISIBLE) return gap;
        if (bar.getHeight() == 0) return getResources().getDimensionPixelSize(R.dimen.bottom_nav_space);
        return bar.getHeight() - findViewById(R.id.shell_content).getPaddingBottom() + gap;
    }

    private void fitTabs() {
        int navSpace = bottomNavSpace();
        for (Fragment tab : tabs) {
            if (tab != null && tab.getView() != null) {
                ScreenFit.fitTab(tab.getView(), navSpace);
                wireSettings(tab.getView());
            }
        }
        if (offlineBanner != null && currentTab >= 0 && tabs[currentTab] != null && tabs[currentTab].getView() != null) {
            View page = tabs[currentTab].getView();
            ScreenFit.placeBelow(offlineBanner, 8, page.findViewById(R.id.header), page.findViewById(R.id.lgu_map_top),
                    page.findViewById(R.id.route_top), page.findViewById(R.id.route_guide_card));
        }
    }

    /**
     * The Settings gear in a tab's header (2026-10-22). Done here, on layout, because a tab's view
     * only exists once the shell has started (the first tab is added while the shell is created).
     */
    private void wireSettings(View tabView) {
        View gear = tabView.findViewById(R.id.header_settings);
        if (gear == null || gear.hasOnClickListeners()) return;
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    /**
     * Phase 1: the Safe Route tab hides the navigation bar while routes are compared and during
     * guidance, so the map stays visible. Leaving the tab shows it again.
     */
    public void setBottomNavHidden(boolean hidden) {
        navHiddenByScreen = hidden;
        updateNavVisibility();
    }

    private void updateNavVisibility() {
        View bar = findViewById(R.id.bottom_nav);
        bar.setVisibility(keyboardOpen || navHiddenByScreen ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_DEST_LAT) && !staff) {
            GeoPoint point = new GeoPoint(intent.getDoubleExtra(EXTRA_DEST_LAT, 0), intent.getDoubleExtra(EXTRA_DEST_LON, 0));
            openRouteTo(new PlaceResult(intent.getStringExtra(EXTRA_DEST_NAME), "", point));
            intent.removeExtra(EXTRA_DEST_LAT);
        }
        if (intent != null && intent.hasExtra(EXTRA_FOCUS_LAT) && staff && !moderator) {
            GeoPoint point = new GeoPoint(intent.getDoubleExtra(EXTRA_FOCUS_LAT, 0), intent.getDoubleExtra(EXTRA_FOCUS_LON, 0));
            selectTab(TAB_STAFF_MAP);
            if (tabs[TAB_STAFF_MAP] instanceof LguHazardMapFragment) {
                ((LguHazardMapFragment) tabs[TAB_STAFF_MAP]).focusOn(point, intent.getStringExtra(EXTRA_FOCUS_NAME));
            }
            intent.removeExtra(EXTRA_FOCUS_LAT);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (offlineBanner == null) return;
        container().connectivity.addListener(this);
        onConnectivityChanged(container().connectivity.isOnline());
    }

    @Override
    protected void onStop() {
        container().connectivity.removeListener(this);
        super.onStop();
    }

    @Override
    public void onConnectivityChanged(boolean online) {
        offlineBanner.setVisibility(online ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    /** Shows one tab and hides the others, keeping their state (and map views) alive. */
    public void selectTab(int index) {
        if (index == currentTab) return;
        setBottomNavHidden(false); // a tab that wants it hidden asks again in onTabShown()
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        for (int i = 0; i < tabs.length; i++) {
            if (i != index && tabs[i] != null) transaction.hide(tabs[i]);
        }
        if (tabs[index] == null) {
            tabs[index] = createTab(index);
            transaction.add(R.id.shell_content, tabs[index], tag(index));
        } else {
            transaction.show(tabs[index]);
        }
        transaction.commitNow();
        currentTab = index;
        nav.select(index);
        if (tabs[index] instanceof BaseFragment) ((BaseFragment) tabs[index]).onTabShown();
    }

    private Fragment createTab(int index) {
        if (moderator) {
            switch (index) {
                case 0: return new ModeratorOverviewFragment();
                case 1: return new ModeratorCentersFragment();
                default: return new LguReviewsFragment();
            }
        }
        if (staff) {
            switch (index) {
                case 0: return new LguOverviewFragment();
                case 1: return new LguHazardMapFragment();
                case 2: return new FinalizeFragment();
                default: return new LguDataFragment();
            }
        }
        switch (index) {
            case 0: return new HomeFragment();
            case 1: return new SafeRouteFragment();
            case 2: return new CentersFragment();
            default: return new MyRecordFragment();
        }
    }

    /** Opens the Safe Route tab with a destination (from Home, Centers, saved places...). */
    public void openRouteTo(Locatable destination) {
        selectTab(TAB_ROUTE);
        if (tabs[TAB_ROUTE] instanceof SafeRouteFragment) {
            ((SafeRouteFragment) tabs[TAB_ROUTE]).setDestination(destination);
        }
    }

    public void openRouteSearch() {
        selectTab(TAB_ROUTE);
        if (tabs[TAB_ROUTE] instanceof SafeRouteFragment) ((SafeRouteFragment) tabs[TAB_ROUTE]).focusSearch();
    }

    private static String tag(int index) {
        return "tab" + index;
    }
}
