package com.rutarescue.service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.rutarescue.R;
import com.rutarescue.model.ligtas.LigtasApplication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phone notification when a moderator decides (or changes) one of the resident's Ligtas
 * applications (2026-10-20). Called while the app is open (MainShellActivity follows the
 * resident's applications) and from the hourly SyncWorker, so the resident hears about it even
 * when the app is closed. LigtasNotices decides what is new; this class only remembers what was
 * shown and talks to Android. No server is needed (free Spark plan).
 */
public final class LigtasNotifier {

    public static final String CHANNEL_ID = "ligtas_updates";
    /** Extra on the launcher Intent: open "My applications" after the login is restored. */
    public static final String EXTRA_OPEN_LIGTAS = "open_ligtas";
    private static final String PREFS = "ligtas_notices";

    private final Context app;

    public LigtasNotifier(Context context) {
        this.app = context.getApplicationContext();
    }

    /** Shows a notification for every new change, once; safe to call from any thread. */
    public synchronized void announce(List<LigtasApplication> applications) {
        // Not allowed to notify yet: keep the changes "new", so they are shown once permission is given.
        if (!canNotify()) return;
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Map<String, String> seen = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getValue() instanceof String) seen.put(entry.getKey(), (String) entry.getValue());
        }
        List<LigtasApplication> news = LigtasNotices.toAnnounce(applications, seen, System.currentTimeMillis());
        if (news.isEmpty()) return;
        for (LigtasApplication application : news) post(application);
        LigtasNotices.markSeen(news, seen);
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, String> entry : seen.entrySet()) editor.putString(entry.getKey(), entry.getValue());
        editor.apply();
    }

    /** Forgets what was shown (sign-out on a shared phone). */
    public void clear() {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** Android 13+ needs the resident's permission; without it the change still shows under Notifications. */
    private boolean canNotify() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void post(LigtasApplication application) {
        createChannel();
        Intent open = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (open == null) return;
        open.putExtra(EXTRA_OPEN_LIGTAS, true);
        PendingIntent tap = PendingIntent.getActivity(app, application.getId().hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = "Ligtas Priority: " + application.getStatus().getLabel();
        String text = application.getMemberName() + " - " + application.getReviewReason();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(app).notify(application.getId().hashCode(), builder.build());
        } catch (SecurityException permissionRevoked) {
            // The permission was taken away while posting; nothing else to do.
        }
    }

    private void createChannel() {
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                app.getString(R.string.notification_channel_ligtas), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("When the city's moderator decides or changes your Ligtas Priority application");
        manager.createNotificationChannel(channel);
    }
}
