package com.rutarescue.ui.shell;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.rutarescue.R;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.Locatable;
import com.rutarescue.model.place.PlaceResult;
import com.rutarescue.service.ConnectivityMonitor;
import com.rutarescue.ui.auth.LoginActivity;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.lgu.data.LguDataFragment;
import com.rutarescue.ui.lgu.hazardmap.LguHazardMapFragment;
import com.rutarescue.ui.lgu.overview.LguOverviewFragment;
import com.rutarescue.ui.lgu.reviews.LguReviewsFragment;
import com.rutarescue.ui.resident.centers.CentersFragment;
import com.rutarescue.ui.resident.home.HomeFragment;
import com.rutarescue.ui.resident.record.MyRecordFragment;
import com.rutarescue.ui.resident.route.SafeRouteFragment;

/**
 * The single authenticated shell (DOCX section 6). It chooses the Resident or LGU tabs
 * from the signed-in profile. Hiding tabs is only for convenience: the database rules
 * still block anything a role is not allowed to do.
 */
public class MainShellActivity extends BaseActivity implements ConnectivityMonitor.Listener {

    public static final int TAB_ROUTE = 1;
    public static final int TAB_CENTERS = 2;
    public static final int TAB_RECORD = 3;

    private static final String EXTRA_DEST_LAT = "dest_lat";
    private static final String EXTRA_DEST_LON = "dest_lon";
    private static final String EXTRA_DEST_NAME = "dest_name";
    private static final String STATE_TAB = "tab";

    private final Fragment[] tabs = new Fragment[4];
    private BottomNav nav;
    private View offlineBanner;
    private boolean lgu;
    private int currentTab = -1;
    private boolean keyboardOpen;
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
        lgu = container().session().isLgu();

        int[] icons = lgu
                ? new int[]{R.drawable.ic_overview, R.drawable.ic_map, R.drawable.ic_reviews, R.drawable.ic_data}
                : new int[]{R.drawable.ic_home, R.drawable.ic_route, R.drawable.ic_center, R.drawable.ic_record};
        int[] labels = lgu
                ? new int[]{R.string.nav_overview, R.string.nav_map, R.string.nav_reviews, R.string.nav_data}
                : new int[]{R.string.nav_home, R.string.nav_route, R.string.nav_centers, R.string.nav_record};
        nav = new BottomNav(findViewById(R.id.bottom_nav), icons, labels, this::selectTab);

        FragmentManager manager = getSupportFragmentManager();
        for (int i = 0; i < tabs.length; i++) tabs[i] = manager.findFragmentByTag(tag(i));
        selectTab(savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_TAB, 0));
        handleIntent(getIntent());
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
        if (intent != null && intent.hasExtra(EXTRA_DEST_LAT) && !lgu) {
            GeoPoint point = new GeoPoint(intent.getDoubleExtra(EXTRA_DEST_LAT, 0), intent.getDoubleExtra(EXTRA_DEST_LON, 0));
            openRouteTo(new PlaceResult(intent.getStringExtra(EXTRA_DEST_NAME), "", point));
            intent.removeExtra(EXTRA_DEST_LAT);
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
        if (lgu) {
            switch (index) {
                case 0: return new LguOverviewFragment();
                case 1: return new LguHazardMapFragment();
                case 2: return new LguReviewsFragment();
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
