package com.rutarescue.ui.resident.home;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rutarescue.R;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.SimpleListAdapter;
import com.rutarescue.ui.common.ViewBinders;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.service.LigtasNotices;
import com.rutarescue.ui.resident.ligtas.LigtasStatusActivity;
import com.rutarescue.util.Formats;
import java.util.ArrayList;
import java.util.List;

/**
 * Notification inbox: every published LGU advisory of the last 30 days, newest first.
 * Previously synced advisories stay readable offline and are labelled "Cached" or "Stale".
 * Since 2026-10-20 a resident also sees their Ligtas status changes on top, with the
 * moderator's message; tapping one opens "My applications".
 */
public class InboxActivity extends BaseActivity {

    private ListenerHandle listener = ListenerHandle.NONE;
    private ListenerHandle ligtasListener = ListenerHandle.NONE;
    private SimpleListAdapter<Advisory> adapter;
    private List<Advisory> advisories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        applySystemBarInsets(findViewById(R.id.list_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText("Notifications");
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());

        TextView freshness = findViewById(R.id.list_freshness);
        TextView empty = findViewById(R.id.list_empty);
        RecyclerView recycler = findViewById(R.id.list_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleListAdapter<>(R.layout.item_advisory, (row, advisory, position) -> {
            ViewBinders.bindAdvisory(row, advisory, () -> {
                showAdvisories();
                recycler.scrollToPosition(0); // a just-saved update is now on top
            });
            // Staff open the editor from their own lists; here every account only reads and saves.
        });
        recycler.setAdapter(adapter);

        if (!container().session().isStaff()) {
            ligtasListener = container().ligtas().listenMine(container().session().getProfile().getUid(),
                    ui(data -> showLigtasNotices(data.getData())));
        }

        listener = container().advisories().listenForNotifications(ui(data -> {
            freshness.setVisibility(View.VISIBLE);
            Badges.freshness(freshness, data.getFreshness(), data.getRetrievedAt(), "");
            advisories = data.getData();
            showAdvisories();
            empty.setVisibility(data.getData().isEmpty() ? View.VISIBLE : View.GONE);
            empty.setText("No LGU advisories yet.");
        }));
    }

    /** Saved updates first (newest save on top), then the newest updates. */
    private void showAdvisories() {
        adapter.submit(container().advisoryMarks.savedFirst(advisories));
    }

    /** One small card per recent Ligtas status change, newest first. */
    private void showLigtasNotices(List<LigtasApplication> applications) {
        LinearLayout header = findViewById(R.id.list_header);
        header.removeAllViews();
        long now = System.currentTimeMillis();
        for (LigtasApplication application : LigtasNotices.forInbox(applications, now)) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_simple_row, header, false);
            ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(R.drawable.ic_reviews);
            ((TextView) row.findViewById(R.id.row_title)).setText("Ligtas Priority: " + application.getStatus().getLabel()
                    + " • " + application.getMemberName());
            String message = application.getStatus() == ApplicationStatus.PENDING
                    ? "New documents sent; waiting for the moderator."
                    : "Moderator: " + application.getReviewReason();
            ((TextView) row.findViewById(R.id.row_subtitle)).setText(message + " • "
                    + Formats.relative(application.getStatusChangedAt(), now));
            row.setOnClickListener(v -> startActivity(new Intent(this, LigtasStatusActivity.class)));
            header.addView(row);
        }
        header.setVisibility(header.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        ligtasListener.remove();
        listener.remove();
        super.onDestroy();
    }
}
