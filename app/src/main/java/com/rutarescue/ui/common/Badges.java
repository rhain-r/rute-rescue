package com.rutarescue.ui.common;

import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.rutarescue.R;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.structure.StructureStatus;
import com.rutarescue.util.Formats;

/**
 * Colours the small rounded badges used across the Figma screens ("RR / Status Chip"):
 * a pill with a 15 dp check-circle icon and a Mobile/Micro label in capitals.
 * Figma tones: Safe = #21956F on #F1F3F4, Watch = #D89025 on #FFF4DF, Urgent = #DF5B59 on #FCECEA.
 */
public final class Badges {

    public enum Tone { GREEN, ORANGE, RED, TEAL, GREY, VIOLET, NAVY, GLASS }

    private Badges() {
    }

    public static void style(TextView badge, String text, Tone tone) {
        style(badge, text, tone, R.drawable.ic_status_check);
    }

    /** Same as {@link #style(TextView, String, Tone)} with a different (or no, use 0) leading icon. */
    public static void style(TextView badge, String text, Tone tone, @DrawableRes int icon) {
        int background;
        int foreground;
        switch (tone) {
            case GREEN: background = R.color.rr_green_soft; foreground = R.color.rr_green; break;
            case ORANGE: background = R.color.rr_orange_soft; foreground = R.color.rr_orange; break;
            case RED: background = R.color.rr_red_soft; foreground = R.color.rr_red; break;
            case TEAL: background = R.color.rr_teal_soft; foreground = R.color.rr_navy_light; break;
            case VIOLET: background = R.color.rr_violet_soft; foreground = R.color.rr_violet; break;
            case NAVY: background = R.color.rr_navy; foreground = R.color.white; break;
            case GLASS: background = R.color.rr_glass; foreground = R.color.rr_navy_light; break;
            default: background = R.color.rr_grey_soft; foreground = R.color.rr_text_secondary;
        }
        int textColor = ContextCompat.getColor(badge.getContext(), foreground);
        badge.setText(text);
        badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(badge.getContext(), background)));
        badge.setTextColor(textColor);
        badge.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(badge, ColorStateList.valueOf(textColor));
    }

    /**
     * Plain coloured state text with no pill, as in the Figma review cards ("URGENT", "NEW")
     * and member rows ("READY", "PRIORITY").
     */
    public static void label(TextView label, String text, Tone tone) {
        int color;
        switch (tone) {
            case GREEN: color = R.color.rr_green; break;
            case ORANGE: color = R.color.rr_orange; break;
            case RED: color = R.color.rr_red; break;
            case TEAL: color = R.color.rr_navy_light; break;
            case VIOLET: color = R.color.rr_violet; break;
            case NAVY: color = R.color.rr_navy; break;
            default: color = R.color.rr_text_secondary;
        }
        label.setText(text);
        label.setTextColor(ContextCompat.getColor(label.getContext(), color));
    }

    public static void riskLevel(TextView badge, RiskLevel level) {
        switch (level) {
            case GREEN: style(badge, "No mapped hazard", Tone.GREEN); break;
            case AMBER: style(badge, "Caution", Tone.ORANGE); break;
            case RED: style(badge, "High risk", Tone.RED); break;
            default: style(badge, "Closed", Tone.NAVY);
        }
    }

    public static void hazard(TextView badge, HazardClass hazardClass) {
        switch (hazardClass) {
            case HIGH: style(badge, "High", Tone.RED); break;
            case MEDIUM: style(badge, "Medium", Tone.ORANGE); break;
            case LOW: style(badge, "Low", Tone.ORANGE); break;
            default: style(badge, "None mapped", Tone.GREEN);
        }
    }

    public static void center(TextView badge, CenterStatus status) {
        switch (status) {
            case OPEN: style(badge, "Open", Tone.GREEN); break;
            case STANDBY: style(badge, "Standby", Tone.ORANGE); break;
            case CLOSED: style(badge, "Closed", Tone.RED); break;
            default: style(badge, "Not reported", Tone.GREY);
        }
    }

    public static void application(TextView badge, ApplicationStatus status) {
        switch (status) {
            case APPROVED: style(badge, "Approved", Tone.GREEN); break;
            case REJECTED: style(badge, "Rejected", Tone.RED); break;
            default: style(badge, "Pending", Tone.ORANGE);
        }
    }

    public static void structure(TextView badge, StructureStatus status) {
        switch (status) {
            case VERIFIED_OPEN: style(badge, "Verified open", Tone.GREEN); break;
            case CLOSED: style(badge, "Closed", Tone.NAVY); break;
            default: style(badge, "Unverified", Tone.VIOLET);
        }
    }

    /** "LIVE • 9:42 AM", "CACHED • 2 h ago", "STALE • Oct 6", "PREPARED • noah-5yr-v1". */
    public static void freshness(TextView chip, DataFreshness freshness, long when, String detail) {
        String time = freshness == DataFreshness.PREPARED ? detail
                : (when > 0 ? Formats.relative(when, System.currentTimeMillis()) : "never synced");
        String text = freshness.getLabel() + " • " + time;
        // Figma "Risk Data Tag": prepared data carries the prepared-data icon; the others carry none.
        switch (freshness) {
            case LIVE: style(chip, text, Tone.GREEN, 0); break;
            case CACHED: style(chip, text, Tone.TEAL, 0); break;
            case STALE: style(chip, text, Tone.ORANGE, 0); break;
            default: style(chip, text, Tone.GLASS, R.drawable.ic_prepared_data);
        }
    }
}
