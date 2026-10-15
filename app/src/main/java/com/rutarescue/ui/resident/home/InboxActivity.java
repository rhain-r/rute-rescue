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

/**
 * Notification inbox: every published LGU advisory, newest first. Previously synced
 * advisories stay readable offline and are labelled "Cached" or "Stale".
 */
public class InboxActivity extends BaseActivity {

    private ListenerHandle listener = ListenerHandle.NONE;

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
        SimpleListAdapter<Advisory> adapter = new SimpleListAdapter<>(R.layout.item_advisory,
                (row, advisory, position) -> ViewBinders.bindAdvisory(row, advisory));
        recycler.setAdapter(adapter);

        listener = container().advisories().listenPublished(ui(data -> {
            freshness.setVisibility(View.VISIBLE);
            Badges.freshness(freshness, data.getFreshness(), data.getRetrievedAt(), "");
            adapter.submit(data.getData());
            empty.setVisibility(data.getData().isEmpty() ? View.VISIBLE : View.GONE);
            empty.setText("No LGU advisories yet.");
        }));
    }

    @Override
    protected void onDestroy() {
        listener.remove();
        super.onDestroy();
    }
}
