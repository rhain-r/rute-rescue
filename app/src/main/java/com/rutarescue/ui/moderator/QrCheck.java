package com.rutarescue.ui.moderator;

import android.content.Context;
import android.content.Intent;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.journeyapps.barcodescanner.ScanOptions;
import com.rutarescue.R;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.priority.PriorityCard;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.lgu.reviews.ReviewDetailActivity;

import java.util.function.Consumer;

/**
 * The moderator's QR check, shared by the Overview and Reviews tabs (2026-10-22): one scanner
 * that opens a priority ID or a Ligtas Priority QR, and a "type the code" fallback for when the
 * camera cannot read the picture.
 */
public final class QrCheck {

    private QrCheck() {
    }

    /** Scanner settings: QR only, no beep, any orientation. */
    public static ScanOptions scanOptions() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan a resident's priority ID or Ligtas QR");
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        return options;
    }

    /**
     * Where a scanned or typed text leads: the priority ID check, the Ligtas application, or null
     * when it is not one of ours.
     */
    public static Intent destination(Context context, String contents) {
        if (contents == null) return null;
        String ligtasId = LigtasApplication.parseQrPayload(contents);
        if (ligtasId != null) return ReviewDetailActivity.intent(context, ligtasId, true);
        if (PriorityCard.parseQrPayload(contents) != null) return PriorityCheckActivity.intent(context, contents);
        return null;
    }

    /** "Scan the QR" or "Type the code" (the code is printed under the QR on the resident's ID). */
    public static void choose(Context context, Runnable scan, Consumer<String> typed) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.check_priority_id)
                .setItems(new CharSequence[]{context.getString(R.string.scan_qr), context.getString(R.string.type_the_code)},
                        (dialog, which) -> {
                            if (which == 0) scan.run();
                            else Dialogs.input(context, context.getString(R.string.type_the_code),
                                    "The 12-letter code under the QR, e.g. ABCD-EFGH-JKLM", "", typed);
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
