package com.rutarescue.ui.lgu.overview;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rutarescue.R;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.SimpleListAdapter;
import com.rutarescue.ui.common.ViewBinders;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "See all advisories" (2026-10-20): the advisories of the staff member's city from the last
 * 30 days, newest first. The LGU sees all of them (pending and declined included); a moderator
 * sees the published ones plus their own center's pending and declined ones (2026-10-22).
 * Since 2026-10-25 the overviews no longer link here (the header bell opens Notifications);
 * bindForStaff is still the staff advisory card of the overviews and the Finalize tab.
 */
public class AdvisoryListActivity extends BaseActivity {

    private ListenerHandle listener = ListenerHandle.NONE;
    private SimpleListAdapter<Advisory> adapter;
    private List<Advisory> advisories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        applySystemBarInsets(findViewById(R.id.list_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.all_advisories);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        StaffProfile staff = container().session().requireStaff();

        TextView intro = findViewById(R.id.list_intro);
        intro.setVisibility(View.VISIBLE);
        intro.setText(staff.getJurisdiction().getDisplayName() + " • advisories older than 30 days are hidden here "
                + "and on residents' phones, but stay in the records.");
        TextView empty = findViewById(R.id.list_empty);
        RecyclerView recycler = findViewById(R.id.list_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleListAdapter<>(R.layout.item_advisory,
                (row, advisory, position) -> bindForStaff(row, advisory, this::showAdvisories));
        recycler.setAdapter(adapter);

        listener = container().advisories().listenForCity(staff.getJurisdiction(), ui(data -> {
            advisories = new ArrayList<>();
            for (Advisory advisory : data.getData()) {
                if (shownTo(staff, advisory)) advisories.add(advisory);
            }
            showAdvisories();
            empty.setVisibility(advisories.isEmpty() ? View.VISIBLE : View.GONE);
            empty.setText(R.string.no_recent_advisories);
        }));
    }

    /** The LGU sees every advisory of its city; a moderator the published ones and their own center's. */
    public static boolean shownTo(StaffProfile staff, Advisory advisory) {
        if (staff.canPublishToResidents() || advisory.isPublished()) return true;
        return staff instanceof ModeratorProfile && ((ModeratorProfile) staff).hasAssignedCenter()
                && ((ModeratorProfile) staff).getCenterId().equals(advisory.getCenterId());
    }

    /**
     * One advisory card as staff see it: no resident buttons, pending and declined ones marked, and
     * a tap opens the editor. Shared with the overviews and the Finalize tab.
     */
    public static void bindForStaff(View card, Advisory advisory) {
        bindForStaff(card, advisory, null);
    }

    /**
     * @param onSavedChanged runs after "Save update" is switched, so the list can put saved updates on top.
     *                       Staff save updates like everyone; "Helpful" is resident feedback and stays hidden.
     */
    public static void bindForStaff(View card, Advisory advisory, Runnable onSavedChanged) {
        ViewBinders.bindAdvisory(card, advisory, onSavedChanged);
        card.findViewById(R.id.advisory_helpful).setVisibility(View.GONE);
        // A draft is not an update anyone receives yet, so only published ones can be saved.
        View save = card.findViewById(R.id.advisory_save);
        save.setVisibility(advisory.isPublished() ? View.VISIBLE : View.GONE);
        // Without "Helpful" in front of it, "Save update" lines up with the text above.
        ((ViewGroup.MarginLayoutParams) save.getLayoutParams()).setMarginStart(0);
        TextView meta = card.findViewById(R.id.advisory_meta);
        // Only a published advisory carries an alert level (the one the LGU had set when publishing it).
        card.findViewById(R.id.advisory_level).setVisibility(advisory.isPublished() ? View.VISIBLE : View.GONE);
        if (!advisory.isPublished()) {
            // "PENDING APPROVAL", "DECLINED" or "HIDDEN", plus who wrote it.
            String by = advisory.getRequestedBy().isEmpty() ? "" : " • by " + advisory.getRequestedBy();
            meta.setText(advisory.getStateLabel().toUpperCase(Locale.ROOT) + by + " (not visible to residents)");
            meta.setTextColor(ContextCompat.getColor(card.getContext(),
                    advisory.isDeclined() ? R.color.rr_red : R.color.rr_orange));
            if (advisory.isDeclined()) {
                ((TextView) card.findViewById(R.id.advisory_message)).setText(advisory.getTitle() + ". "
                        + advisory.getMessage() + "\n\nLGU comment: " + advisory.getLguNote());
            }
        } else {
            meta.setTextColor(ContextCompat.getColor(card.getContext(), R.color.rr_green));
        }
        Context context = card.getContext();
        card.setOnClickListener(v -> context.startActivity(new Intent(context, AdvisoryEditorActivity.class)
                .putExtra(AdvisoryEditorActivity.EXTRA_ID, advisory.getId())));
    }

    /** Newest first: this is the full list of recent updates (saved ones lead in Notifications). */
    private void showAdvisories() {
        adapter.submit(advisories);
    }

    @Override
    protected void onDestroy() {
        listener.remove();
        super.onDestroy();
    }
}
