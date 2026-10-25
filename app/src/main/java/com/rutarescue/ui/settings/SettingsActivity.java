package com.rutarescue.ui.settings;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rutarescue.R;
import com.rutarescue.model.hazard.DatasetVersion;
import com.rutarescue.model.priority.PriorityCard;
import com.rutarescue.model.priority.PriorityGroup;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.SyncWorker;
import com.rutarescue.ui.about.AboutActivity;
import com.rutarescue.ui.auth.LoginActivity;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.lgu.data.DatasetRows;
import com.rutarescue.ui.resident.priority.PriorityIdActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings (2026-10-22), the same screen for every role: the account, a password reset e-mail,
 * the resident's priority ID, the LGU's datasets (since 2026-10-25), phone notifications,
 * "Sync now", About, and Sign out.
 */
public class SettingsActivity extends BaseActivity {

    private ListenerHandle priorityListener = ListenerHandle.NONE;
    private ListenerHandle datasetsListener = ListenerHandle.NONE;
    private View priorityRow;
    /** LGU only: the bundled datasets and the city's own versions, shown under "Datasets". */
    private LguProfile lgu;
    private List<DatasetVersion> prepared = new ArrayList<>();
    private List<DatasetVersion> versions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        applySystemBarInsets(findViewById(R.id.settings_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.settings);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());

        UserProfile profile = container().session() == null ? null : container().session().getProfile();
        if (profile == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.settings_name)).setText(profile.getDisplayName());
        ((TextView) findViewById(R.id.settings_role)).setText(profile.getRoleLabel()
                + (profile instanceof ResidentProfile && profile.getCity() != null
                ? " • " + profile.getCity().getDisplayName() : ""));
        ((TextView) findViewById(R.id.settings_email)).setText(profile.getEmail());

        LinearLayout account = findViewById(R.id.settings_account_rows);
        addRow(account, R.drawable.ic_shield, getString(R.string.change_password),
                getString(R.string.change_password_sub), v -> sendPasswordReset(profile.getEmail()));

        if (profile instanceof ResidentProfile) {
            findViewById(R.id.settings_priority_header).setVisibility(View.VISIBLE);
            priorityRow = addRow(findViewById(R.id.settings_priority_rows), R.drawable.ic_qr,
                    getString(R.string.get_priority_id), getString(R.string.priority_id_who), v -> askForGroup(
                            (ResidentProfile) profile));
            priorityListener = container().priorityIds().listenMine(profile.getUid(), ui(this::showPriority));
        }

        if (profile instanceof LguProfile) {
            // 2026-10-25: the dataset versions moved here from the LGU's Data tab
            lgu = (LguProfile) profile;
            findViewById(R.id.settings_datasets_header).setVisibility(View.VISIBLE);
            prepared = container().datasets().preparedDatasets();
            showDatasets();
            container().datasets().loadPreparedDatasets(ui(list -> {
                prepared = list;
                showDatasets();
            }));
            datasetsListener = container().datasets().listenVersions(ui(list -> {
                versions = list;
                showDatasets();
            }));
        }

        LinearLayout phone = findViewById(R.id.settings_phone_rows);
        addRow(phone, R.drawable.ic_bell, getString(R.string.phone_notifications),
                getString(R.string.phone_notifications_sub), v -> openNotificationSettings());
        addRow(phone, R.drawable.ic_prepared_data, getString(R.string.sync_now), getString(R.string.sync_now_sub),
                v -> syncNow());
        addRow(phone, R.drawable.ic_info, getString(R.string.about_title), getString(R.string.about_sub),
                v -> startActivity(new Intent(this, AboutActivity.class)));

        findViewById(R.id.settings_sign_out).setOnClickListener(v -> {
            container().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        priorityListener.remove();
        datasetsListener.remove();
        super.onDestroy();
    }

    private void showDatasets() {
        if (isFinishing()) return;
        DatasetRows.render(this, findViewById(R.id.settings_datasets_rows), prepared, versions, lgu.getJurisdiction());
    }

    /** One compact row (icon tile, title, a meta line up to three lines, chevron). */
    private View addRow(LinearLayout parent, int icon, String title, String meta, View.OnClickListener onClick) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_data_row, parent, false);
        ImageView tile = row.findViewById(R.id.data_row_icon);
        tile.setImageResource(icon);
        tile.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.rr_navy_light)));
        ((TextView) row.findViewById(R.id.data_row_title)).setText(title);
        TextView metaView = row.findViewById(R.id.data_row_meta);
        metaView.setMaxLines(5);
        metaView.setText(meta);
        row.setOnClickListener(onClick);
        parent.addView(row);
        return row;
    }

    /** The resident's priority ID row: "My priority ID" when they have one, otherwise "Get a priority ID". */
    private void showPriority(PriorityCard card) {
        if (priorityRow == null || card == null) return;
        ((TextView) priorityRow.findViewById(R.id.data_row_title)).setText(R.string.my_priority_id);
        ((TextView) priorityRow.findViewById(R.id.data_row_meta)).setText(card.getGroup().getLabel() + " • "
                + card.getDisplayCode() + " • " + card.getStatusLabel());
        priorityRow.setOnClickListener(v -> startActivity(new Intent(this, PriorityIdActivity.class)));
    }

    /** No ID yet: pick the group, then the ID is issued and shown. */
    private void askForGroup(ResidentProfile resident) {
        PriorityGroup[] groups = PriorityGroup.values();
        String[] labels = new String[groups.length];
        for (int i = 0; i < groups.length; i++) labels[i] = groups[i].getLabel();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.get_priority_id)
                .setItems(labels, (dialog, which) -> container().priorityIds().issue(resident, groups[which],
                        ui(card -> startActivity(new Intent(this, PriorityIdActivity.class)))))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void sendPasswordReset(String email) {
        Dialogs.confirm(this, getString(R.string.change_password), "We will e-mail a link to " + email
                        + " to choose a new password. (Test addresses ending in .test receive nothing.)",
                getString(R.string.send), () -> container().auth().sendPasswordReset(email, ui(ignored ->
                        showMessage("Password e-mail sent to " + email))));
    }

    private void openNotificationSettings() {
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    /** Refreshes advisories, centers and Ligtas notices now instead of waiting for the hourly sync. */
    private void syncNow() {
        if (!container().connectivity.isOnline()) {
            showMessage("You are offline. Saved data stays on this phone and syncs when you reconnect.");
            return;
        }
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequest.from(SyncWorker.class));
        showMessage("Syncing now");
    }
}
