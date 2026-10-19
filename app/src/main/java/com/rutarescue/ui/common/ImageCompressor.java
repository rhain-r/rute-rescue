package com.rutarescue.ui.common;

import android.content.ContentResolver;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Shrinks a picked demo image to a small JPEG (longest side <= 1024 px, under ~280 KB)
 * before upload, so it fits the storage limits in storage.rules / Firestore.
 * Runs on a background thread (it reads and decodes a file).
 */
public final class ImageCompressor {

    private static final int MAX_SIDE = 1024;
    private static final int TARGET_BYTES = 280 * 1024;

    /** Opens the image again each time it is called (it is read twice: size first, then pixels). */
    private interface ImageSource {
        InputStream open() throws IOException;
    }

    private ImageCompressor() {
    }

    /** An image the resident picked with the photo picker. */
    public static byte[] compress(ContentResolver resolver, Uri uri) throws IOException {
        return compress(() -> resolver.openInputStream(uri));
    }

    /** An image bundled in the app's assets, e.g. the synthetic demo proof. */
    public static byte[] compressAsset(AssetManager assets, String path) throws IOException {
        return compress(() -> assets.open(path));
    }

    private static byte[] compress(ImageSource source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = source.open()) {
            if (in == null) throw new IOException("The image could not be opened.");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("The chosen file is not a supported image.");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > MAX_SIDE) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap;
        try (InputStream in = source.open()) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) throw new IOException("The image could not be decoded.");
        int quality = 80;
        byte[] bytes;
        do {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            bytes = out.toByteArray();
            quality -= 15;
        } while (bytes.length > TARGET_BYTES && quality >= 20);
        bitmap.recycle();
        if (bytes.length > TARGET_BYTES) throw new IOException("The image is still too large after compression.");
        return bytes;
    }
}
