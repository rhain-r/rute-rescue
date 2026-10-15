package com.rutarescue.ui.common;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.rutarescue.R;
import com.rutarescue.RutaRescueApp;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.service.AdvisoryMarks;
import com.rutarescue.service.CenterService;
import com.rutarescue.util.Formats;

/**
 * Fills the reusable item layouts (item_center, item_advisory) so the same Figma
 * component looks identical on every screen that uses it.
 */
public final class ViewBinders {

    private ViewBinders() {
    }

    /** item_center.xml ("RR / Center Row"): name and "0.8 km • Open • LGU updated 8 min ago". */
    public static void bindCenter(View card, EvacuationCenter center, GeoPoint origin) {
        ((TextView) card.findViewById(R.id.center_name)).setText(center.getName());
        StringBuilder meta = new StringBuilder();
        if (origin != null) {
            meta.append(Formats.distance(CenterService.distance(center, origin))).append(" • ");
        }
        meta.append(center.getStatus().getLabel());
        if (center.getVerification().isVerified()) {
            meta.append(" • LGU updated ").append(Formats.relative(center.getVerification().getVerifiedAt(),
                    System.currentTimeMillis()));
        } else {
            meta.append(" • not yet verified");
        }
        ((TextView) card.findViewById(R.id.center_meta)).setText(meta);
    }

    /**
     * item_advisory.xml ("Verified LGU Update"): publisher, "LGU VERIFIED • 12 min ago", alert level,
     * the post, and the "Helpful" / "Save update" actions (remembered on this device only).
     */
    public static void bindAdvisory(View card, Advisory advisory) {
        String author = advisory.getAuthorName();
        ((TextView) card.findViewById(R.id.advisory_author)).setText(author.isEmpty() ? "LGU" : author);
        ((TextView) card.findViewById(R.id.advisory_meta)).setText("LGU verified • "
                + Formats.relative(advisory.getPublishedAt(), System.currentTimeMillis()));
        ((TextView) card.findViewById(R.id.advisory_message)).setText(advisory.getTitle() + ". " + advisory.getMessage());

        TextView level = card.findViewById(R.id.advisory_level);
        level.setText(advisory.getLevel().getLabel());
        int levelColor = advisory.getLevel() == AlertLevel.ALERT_3 ? R.color.rr_red
                : advisory.getLevel() == AlertLevel.ADVISORY ? R.color.rr_navy_light : R.color.rr_orange;
        level.setTextColor(ContextCompat.getColor(card.getContext(), levelColor));

        AdvisoryMarks marks = RutaRescueApp.container(card.getContext()).advisoryMarks;
        TextView helpful = card.findViewById(R.id.advisory_helpful);
        TextView save = card.findViewById(R.id.advisory_save);
        showMarks(helpful, save, marks, advisory.getId());
        helpful.setOnClickListener(v -> {
            boolean marked = marks.toggleHelpful(advisory.getId());
            showMarks(helpful, save, marks, advisory.getId());
            Snackbar.make(card, marked ? "Marked as helpful on this device" : "Helpful mark removed",
                    Snackbar.LENGTH_SHORT).show();
        });
        save.setOnClickListener(v -> {
            boolean saved = marks.toggleSaved(advisory.getId());
            showMarks(helpful, save, marks, advisory.getId());
            Snackbar.make(card, saved ? "Update saved; it stays readable offline" : "Removed from saved updates",
                    Snackbar.LENGTH_SHORT).show();
        });
    }

    /** Figma colours: an active action is brand-600, an inactive one is #667085 ("Save update"). */
    private static void showMarks(TextView helpful, TextView save, AdvisoryMarks marks, String id) {
        boolean isHelpful = marks.isHelpful(id);
        boolean isSaved = marks.isSaved(id);
        helpful.setText(isHelpful ? R.string.advisory_helpful_done : R.string.advisory_helpful);
        save.setText(isSaved ? R.string.advisory_saved : R.string.advisory_save);
        save.setTextColor(ContextCompat.getColor(save.getContext(),
                isSaved ? R.color.rr_navy_light : R.color.rr_text_secondary));
    }
}
