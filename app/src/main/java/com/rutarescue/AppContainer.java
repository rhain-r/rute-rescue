package com.rutarescue;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.rutarescue.network.CachedRouteProvider;
import com.rutarescue.network.HttpJsonClient;
import com.rutarescue.network.FallbackRouteProvider;
import com.rutarescue.network.NominatimSearchProvider;
import com.rutarescue.network.OsrmRouteProvider;
import com.rutarescue.network.RouteProvider;
import com.rutarescue.network.TomTomRouteProvider;
import com.rutarescue.persistence.RouteCacheStore;
import com.rutarescue.repository.AuditRepository;
import com.rutarescue.repository.ClosureRepository;
import com.rutarescue.repository.ProofStorage;
import com.rutarescue.repository.SavedPlaceRepository;
import com.rutarescue.repository.StructureStatusRepository;
import com.rutarescue.repository.UserRepository;
import com.rutarescue.repository.firestore.FirebaseProofStorage;
import com.rutarescue.repository.firestore.FirestoreAdvisoryRepository;
import com.rutarescue.repository.firestore.FirestoreAuditRepository;
import com.rutarescue.repository.firestore.FirestoreCenterRepository;
import com.rutarescue.repository.firestore.FirestoreClosureRepository;
import com.rutarescue.repository.firestore.FirestoreDatasetRepository;
import com.rutarescue.repository.firestore.FirestoreFamilyRepository;
import com.rutarescue.repository.firestore.FirestoreInlineProofStorage;
import com.rutarescue.repository.firestore.FirestoreLigtasRepository;
import com.rutarescue.repository.firestore.FirestoreSavedPlaceRepository;
import com.rutarescue.repository.firestore.FirestoreStructureStatusRepository;
import com.rutarescue.repository.firestore.FirestoreUserRepository;
import com.rutarescue.repository.local.AssetHazardRepository;
import com.rutarescue.repository.local.AssetStructureRepository;
import com.rutarescue.service.AdvisoryMarks;
import com.rutarescue.service.AdvisoryService;
import com.rutarescue.service.AuthService;
import com.rutarescue.service.CenterService;
import com.rutarescue.service.ConnectivityMonitor;
import com.rutarescue.service.DatasetService;
import com.rutarescue.service.FamilyService;
import com.rutarescue.service.FreshnessService;
import com.rutarescue.service.LigtasService;
import com.rutarescue.service.LocationHelper;
import com.rutarescue.service.RouteService;
import com.rutarescue.service.SessionManager;
import com.rutarescue.util.AppExecutors;

import java.io.File;

/**
 * Creates every service and repository once and wires them together ("manual dependency
 * injection"). Activities ask this container for services instead of creating Firebase
 * or HTTP clients themselves, which keeps the controllers small (DOCX section 10:
 * "controllers call use cases rather than Firebase or HTTP clients directly").
 */
public final class AppContainer {

    public final AppExecutors executors = new AppExecutors();
    public final ConnectivityMonitor connectivity;
    public final FreshnessService freshness;
    public final LocationHelper location;
    public final AssetHazardRepository hazards;
    public final AssetStructureRepository structures;
    public final boolean hasTomTomKey;
    /** The resident's own "Helpful" / "Save update" marks on advisories (device only). */
    public final AdvisoryMarks advisoryMarks;

    private final boolean firebaseReady;
    private final RouteCacheStore routeCache;
    private AuthService authService;
    private SessionManager session;
    private FamilyService familyService;
    private RouteService routeService;
    private CenterService centerService;
    private AdvisoryService advisoryService;
    private LigtasService ligtasService;
    private DatasetService datasetService;
    private SavedPlaceRepository savedPlaces;
    private AuditRepository audit;

    public AppContainer(Context context) {
        Context app = context.getApplicationContext();
        connectivity = new ConnectivityMonitor(app);
        freshness = new FreshnessService(app);
        location = new LocationHelper(app);
        hazards = new AssetHazardRepository(app.getAssets());
        structures = new AssetStructureRepository(app.getAssets());
        advisoryMarks = new AdvisoryMarks(app);
        hasTomTomKey = !BuildConfig.TOMTOM_API_KEY.isEmpty();

        // google-services.json is private configuration; without it Firebase is not initialised.
        firebaseReady = !FirebaseApp.getApps(app).isEmpty();
        HttpJsonClient http = new HttpJsonClient(15_000);
        routeCache = new RouteCacheStore(new File(app.getFilesDir(), "route_cache").toPath());
        CachedRouteProvider cachedRoutes = new CachedRouteProvider(routeCache);

        StructureStatusRepository structureStatuses = null;
        ClosureRepository closures = null;
        if (firebaseReady) {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            FirebaseStorage storage = FirebaseStorage.getInstance();
            if (BuildConfig.USE_FIREBASE_EMULATOR) {
                auth.useEmulator(BuildConfig.EMULATOR_HOST, 9099);
                db.useEmulator(BuildConfig.EMULATOR_HOST, 8080);
                storage.useEmulator(BuildConfig.EMULATOR_HOST, 9199);
            }
            // Offline persistence: Firestore keeps a local cache so previously synced data
            // (advisories, centers, applications, family record) stays readable offline.
            db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                            .setSizeBytes(100L * 1024 * 1024).build())
                    .build());

            UserRepository users = new FirestoreUserRepository(db);
            authService = new AuthService(auth, users);
            session = new SessionManager(authService, users);
            familyService = new FamilyService(new FirestoreFamilyRepository(db, freshness, connectivity), executors,
                    new File(app.getFilesDir(), "data").toPath());
            FirestoreCenterRepository centers = new FirestoreCenterRepository(db, freshness, connectivity);
            centerService = new CenterService(centers);
            advisoryService = new AdvisoryService(new FirestoreAdvisoryRepository(db, freshness, connectivity));
            ProofStorage proofs = BuildConfig.USE_FIREBASE_STORAGE
                    ? new FirebaseProofStorage(storage)
                    : new FirestoreInlineProofStorage(db, connectivity);
            ligtasService = new LigtasService(new FirestoreLigtasRepository(db, freshness, connectivity), proofs);
            structureStatuses = new FirestoreStructureStatusRepository(db, connectivity);
            closures = new FirestoreClosureRepository(db, connectivity);
            datasetService = new DatasetService(new FirestoreDatasetRepository(db, connectivity), centers,
                    structureStatuses, closures, hazards, structures, executors);
            savedPlaces = new FirestoreSavedPlaceRepository(db, connectivity);
            audit = new FirestoreAuditRepository(db);
        }
        // Free providers only (no payment):
        //  - search: OpenStreetMap Nominatim (no key);
        //  - routes: TomTom free tier (live traffic) when a key is configured, falling back to
        //    OpenStreetMap routing (OSRM) when TomTom cannot answer; OSRM alone without a key.
        RouteProvider osmRoutes = new OsrmRouteProvider(http);
        RouteProvider liveRoutes = hasTomTomKey
                ? new FallbackRouteProvider(new TomTomRouteProvider(http, BuildConfig.TOMTOM_API_KEY), osmRoutes)
                : osmRoutes;
        routeService = new RouteService(liveRoutes, cachedRoutes, routeCache, new NominatimSearchProvider(http),
                hazards, structures, structureStatuses, closures, connectivity, executors);
    }

    /**
     * Signs out and removes the personal data this phone kept for the resident: the Lab family
     * file, cached routes (they start at the user's location) and advisory marks. Phones are
     * often shared in a household or barangay, so the next person must not see them.
     */
    public void signOut() {
        if (session != null) session.signOut();
        if (familyService != null) familyService.clearLocalCopy();
        executors.io().execute(routeCache::clear);
        advisoryMarks.clear();
    }

    public boolean isFirebaseReady() { return firebaseReady; }
    public AuthService auth() { return authService; }
    public SessionManager session() { return session; }
    public FamilyService family() { return familyService; }
    public RouteService routes() { return routeService; }
    public CenterService centers() { return centerService; }
    public AdvisoryService advisories() { return advisoryService; }
    public LigtasService ligtas() { return ligtasService; }
    public DatasetService datasets() { return datasetService; }
    public SavedPlaceRepository savedPlaces() { return savedPlaces; }
    public AuditRepository audit() { return audit; }
}
