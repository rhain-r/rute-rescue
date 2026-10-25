package com.rutarescue.ui.resident.priority;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.rutarescue.R;
import com.rutarescue.model.priority.PriorityCard;
import com.rutarescue.util.Formats;

/**
 * Draws the priority ID as one picture (1000 x 620 px, like a wallet card): a navy band with
 * "RutaRescue Priority ID" and the city, the holder's name and group, the issue date, the code
 * for typing, and the QR on the right. The same picture is shown on screen and saved to the phone.
 */
public final class PriorityIdCardRenderer {

    public static final int WIDTH = 1000;
    public static final int HEIGHT = 620;
    private static final int QR_SIZE = 360;

    private PriorityIdCardRenderer() {
    }

    public static Bitmap render(Context context, PriorityCard card) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int navy = ContextCompat.getColor(context, R.color.rr_navy);
        int accent = ContextCompat.getColor(context, R.color.rr_orange);
        int text = ContextCompat.getColor(context, R.color.rr_text);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Card with rounded corners and a thin border.
        paint.setColor(Color.WHITE);
        RectF outline = new RectF(4, 4, WIDTH - 4, HEIGHT - 4);
        canvas.drawRoundRect(outline, 36, 36, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(navy);
        canvas.drawRoundRect(outline, 36, 36, paint);
        paint.setStyle(Paint.Style.FILL);

        // Top band.
        canvas.save();
        canvas.clipRect(0, 0, WIDTH, 110);
        canvas.drawRoundRect(outline, 36, 36, paint);
        canvas.restore();
        Paint band = textPaint(Color.WHITE, 40, true);
        canvas.drawText("RutaRescue  •  Priority ID", 44, 72, band);
        Paint city = textPaint(Color.WHITE, 30, false);
        city.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(card.getCity() == null ? "" : card.getCity().getDisplayName(), WIDTH - 44, 70, city);

        // Holder, group, dates (left column, up to the QR).
        float left = 44;
        float maxWidth = WIDTH - QR_SIZE - 44 - 44 - 30;
        Paint name = textPaint(text, 50, true);
        canvas.drawText(fit(card.getHolderName(), name, maxWidth), left, 190, name);
        Paint group = textPaint(accent, 38, true);
        canvas.drawText(fit(card.getGroup().getLabel(), group, maxWidth), left, 250, group);
        Paint small = textPaint(text, 28, false);
        canvas.drawText("Issued " + Formats.dateTime(card.getIssuedAt()), left, 310, small);

        Paint label = textPaint(text, 26, false);
        canvas.drawText("ID code", left, 390, label);
        Paint code = textPaint(navy, 54, true);
        code.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        canvas.drawText(card.getDisplayCode(), left, 450, code);

        Paint footer = textPaint(text, 25, false);
        canvas.drawText("Show this at the evacuation center.", left, 530, footer);
        canvas.drawText("Staff scan the QR to open your record.", left, 566, footer);

        // QR (only the prefix and the code, no personal data).
        try {
            Bitmap qr = new BarcodeEncoder().encodeBitmap(card.getQrPayload(), BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            canvas.drawBitmap(qr, WIDTH - QR_SIZE - 44, 160, null);
        } catch (WriterException e) {
            Paint error = textPaint(text, 26, false);
            canvas.drawText("QR not available", WIDTH - QR_SIZE - 20, 340, error);
        }
        return bitmap;
    }

    private static Paint textPaint(int color, float size, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return paint;
    }

    /** Shortens a long name with "…" so it never runs under the QR. */
    private static String fit(String value, Paint paint, float maxWidth) {
        return TextUtils.ellipsize(value == null ? "" : value, new android.text.TextPaint(paint), maxWidth,
                TextUtils.TruncateAt.END).toString();
    }
}
