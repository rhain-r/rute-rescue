package com.rutarescue.ui.resident.priority;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rutarescue.R;
import com.rutarescue.model.priority.ChecklistItem;
import com.rutarescue.model.priority.PriorityCard;
import com.rutarescue.model.priority.PriorityGroup;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.resident.ligtas.LigtasApplyActivity;
import com.rutarescue.util.Formats;

import java.io.OutputStream;

/**
 * The resident's priority ID (2026-10-22). Shows the card picture (PriorityIdCardRenderer),
 * saves it to the phone's Pictures/RutaRescue folder, and shows what the moderator found at the
 * last check at an evacuation center. Opened after registration, from My Record and Settings.
 */
public class PriorityIdActivity extends BaseActivity {

    private ListenerHandle listener = ListenerHandle.NONE;
    private PriorityCard card;
    private Bitmap picture;

    /** Android 9 and older: the resident picks where to save the picture (no storage permission needed). */
    private final ActivityResultLauncher<String> saveAs = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("image/png"), uri -> {
                if (uri != null) writePicture(uri, "Priority ID saved");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_priority_id);
        applySystemBarInsets(findViewById(R.id.priority_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.my_priority_id);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());

        UserProfile profile = container().session() == null ? null : container().session().getProfile();
        if (!(profile instanceof ResidentProfile)) {
            finish();
            return;
        }
        findViewById(R.id.priority_save).setOnClickListener(v -> save());
        findViewById(R.id.priority_get).setOnClickListener(v -> askForGroup((ResidentProfile) profile));
        findViewById(R.id.priority_apply).setOnClickListener(v -> startActivity(
                new android.content.Intent(this, LigtasApplyActivity.class)));
        listener = container().priorityIds().listenMine(profile.getUid(), ui(this::show));
    }

    @Override
    protected void onDestroy() {
        listener.remove();
        super.onDestroy();
    }

    private void show(PriorityCard mine) {
        card = mine;
        boolean has = mine != null;
        findViewById(R.id.priority_card).setVisibility(has ? View.VISIBLE : View.GONE);
        findViewById(R.id.priority_save).setVisibility(has ? View.VISIBLE : View.GONE);
        findViewById(R.id.priority_empty).setVisibility(has ? View.GONE : View.VISIBLE);
        findViewById(R.id.priority_get).setVisibility(has ? View.GONE : View.VISIBLE);
        TextView status = findViewById(R.id.priority_status);
        TextView check = findViewById(R.id.priority_check_text);
        if (!has) {
            Badges.label(status, "No ID yet", Badges.Tone.ORANGE);
            check.setText(R.string.priority_id_who);
            return;
        }
        picture = PriorityIdCardRenderer.render(this, mine);
        ((ImageView) findViewById(R.id.priority_card)).setImageBitmap(picture);

        if (!mine.isChecked()) {
            Badges.label(status, mine.getStatusLabel(), Badges.Tone.TEAL);
            check.setText("Bring these to the evacuation center:\n" + itemList(mine, false));
            return;
        }
        boolean complete = mine.getMissingItems().isEmpty();
        Badges.label(status, mine.getStatusLabel(), complete ? Badges.Tone.GREEN : Badges.Tone.ORANGE);
        StringBuilder text = new StringBuilder("Checked ")
                .append(mine.getCheckedAtCenter().isEmpty() ? "" : "at " + mine.getCheckedAtCenter() + " ")
                .append("by ").append(mine.getCheckedByName()).append(" • ")
                .append(Formats.dateTime(mine.getCheckedAt()));
        if (!complete) text.append("\n\nStill needed:\n").append(itemList(mine, true));
        if (!mine.getFlagNote().isEmpty()) text.append("\n\nNote from the center: ").append(mine.getFlagNote());
        check.setText(text);
    }

    /** "• Valid ID ..." lines: every item, or only the missing ones. */
    private static String itemList(PriorityCard card, boolean missingOnly) {
        StringBuilder lines = new StringBuilder();
        for (ChecklistItem item : missingOnly ? card.getMissingItems() : card.getChecklist()) {
            if (lines.length() > 0) lines.append('\n');
            lines.append("• ").append(item.getLabel());
        }
        return lines.toString();
    }

    private void askForGroup(ResidentProfile resident) {
        PriorityGroup[] groups = PriorityGroup.values();
        String[] labels = new String[groups.length];
        for (int i = 0; i < groups.length; i++) labels[i] = groups[i].getLabel();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.get_priority_id)
                .setItems(labels, (dialog, which) -> container().priorityIds().issue(resident, groups[which],
                        ui(issued -> showMessage("Your priority ID is ready. Save it to your phone."))))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Android 10+: straight into Pictures/RutaRescue (shows in the Gallery). Older: "Save as". */
    private void save() {
        if (card == null || picture == null) return;
        String fileName = "RutaRescue-priority-ID-" + card.getId() + ".png";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            saveAs.launch(fileName);
            return;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RutaRescue");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            showMessage("Could not save the picture on this phone.");
            return;
        }
        if (writePicture(uri, "Saved to Pictures > RutaRescue")) {
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
        }
    }

    private boolean writePicture(Uri uri, String doneMessage) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null || !picture.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new java.io.IOException("No stream");
            showMessage(doneMessage);
            return true;
        } catch (java.io.IOException | SecurityException e) {
            showMessage("Could not save the picture on this phone.");
            return false;
        }
    }
}
