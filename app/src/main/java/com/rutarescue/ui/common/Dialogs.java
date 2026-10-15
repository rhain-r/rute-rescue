package com.rutarescue.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.rutarescue.R;

import java.util.function.Consumer;

/** Small reusable dialogs: text input, confirmation and the Ligtas QR code. */
public final class Dialogs {

    private Dialogs() {
    }

    /** Shows a one-field input dialog; onOk receives the typed text. */
    public static void input(Context context, String title, String hint, String initial, Consumer<String> onOk) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_input, null);
        TextView label = view.findViewById(R.id.dialog_input_hint);
        TextInputEditText input = view.findViewById(R.id.dialog_input_text);
        label.setText(hint);
        input.setText(initial == null ? "" : initial);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("OK", (dialog, which) -> onOk.accept(text(input)))
                .show();
    }

    public static void confirm(Context context, String title, String message, String action, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(action, (dialog, which) -> onConfirm.run())
                .show();
    }

    /** QR for an approved Ligtas application. The payload is only an opaque id. */
    public static void qr(Context context, String title, String payload) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_qr, null);
        ImageView image = view.findViewById(R.id.qr_image);
        try {
            Bitmap bitmap = new BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, 640, 640);
            image.setImageBitmap(bitmap);
        } catch (WriterException e) {
            image.setVisibility(View.GONE);
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view)
                .setPositiveButton("Close", null)
                .show();
    }

    public static String text(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
