package com.rutarescue.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.rutarescue.repository.firestore.FirestoreLigtasRepository;
import com.rutarescue.util.SafeLog;

import java.util.concurrent.TimeUnit;

/**
 * Background refresh with WorkManager (DOCX section 10): about every hour, while online,
 * pull the published advisories and centers from the server so the offline cache stays
 * as fresh as possible. Runs on WorkManager's background thread, so blocking calls are OK.
 */
public final class SyncWorker extends Worker {

    private static final String TAG = "RR-Sync";
    private static final String UNIQUE_NAME = "rutarescue-sync";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (FirebaseApp.getApps(getApplicationContext()).isEmpty()
                || FirebaseAuth.getInstance().getCurrentUser() == null) {
            return Result.success(); // nothing to sync until someone signs in
        }
        FreshnessService freshness = new FreshnessService(getApplicationContext());
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        try {
            Tasks.await(db.collection("advisories").whereEqualTo("published", true).get(Source.SERVER), 30, TimeUnit.SECONDS);
            freshness.markServerSync(FreshnessService.ADVISORIES, System.currentTimeMillis());
            Tasks.await(db.collection("centers").whereEqualTo("published", true).get(Source.SERVER), 30, TimeUnit.SECONDS);
            freshness.markServerSync(FreshnessService.CENTERS, System.currentTimeMillis());
            // 2026-10-20: tell the resident when a moderator decided one of their Ligtas applications.
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            new LigtasNotifier(getApplicationContext()).announce(FirestoreLigtasRepository.readMineFromServer(db, uid));
            SafeLog.i(TAG, "Background sync finished");
            return Result.success();
        } catch (Exception e) {
            SafeLog.w(TAG, "Background sync failed; will retry", e);
            return Result.retry();
        }
    }
}
