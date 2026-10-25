package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.DatasetStatus;
import com.rutarescue.model.hazard.DatasetType;
import com.rutarescue.model.hazard.DatasetVersion;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.route.RoadClosure;
import com.rutarescue.model.structure.StructureStatus;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.persistence.CenterCsvReader;
import com.rutarescue.persistence.PreparedDataManifest;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.CenterRepository;
import com.rutarescue.repository.ClosureRepository;
import com.rutarescue.repository.DatasetRepository;
import com.rutarescue.repository.HazardRepository;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.StructureStatusRepository;
import com.rutarescue.repository.local.AssetStructureRepository;
import com.rutarescue.util.AppExecutors;
import com.rutarescue.util.Formats;
import com.rutarescue.validation.TextRules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * LGU "Data & Centers" and "Hazard Map" use cases (DOCX sections 3, 5, 7, 8):
 *  - list prepared (bundled) datasets and LGU dataset versions,
 *  - import -> validate -> preview -> publish evacuation centers,
 *  - record bridge/tunnel passability evidence,
 *  - report and lift road closures.
 * Every change is written together with an audit event.
 */
public final class DatasetService {

    private final DatasetRepository datasets;
    private final CenterRepository centers;
    private final StructureStatusRepository structureStatuses;
    private final ClosureRepository closures;
    private final HazardRepository hazards;
    private final AssetStructureRepository structures;
    private final AppExecutors executors;

    public DatasetService(DatasetRepository datasets, CenterRepository centers, StructureStatusRepository structureStatuses,
                          ClosureRepository closures, HazardRepository hazards, AssetStructureRepository structures,
                          AppExecutors executors) {
        this.datasets = datasets;
        this.centers = centers;
        this.structureStatuses = structureStatuses;
        this.closures = closures;
        this.hazards = hazards;
        this.structures = structures;
        this.executors = executors;
    }

    public ListenerHandle listenVersions(Callback<List<DatasetVersion>> callback) {
        return datasets.listen(callback);
    }

    /** The read-only prepared datasets bundled in the app (NOAH layers and road structures). */
    public List<DatasetVersion> preparedDatasets() {
        List<DatasetVersion> list = new ArrayList<>();
        PreparedDataManifest manifest = hazards.getManifest();
        for (FloodScenario scenario : FloodScenario.values()) {
            PreparedDataManifest.ScenarioInfo info = manifest.get(scenario);
            list.add(new DatasetVersion("prepared-" + scenario.name(), DatasetType.FLOOD_HAZARD,
                    "NOAH " + scenario.getLabel() + " • " + manifest.versionOf(scenario), DatasetStatus.PREPARED,
                    info == null ? "Project NOAH" : info.getSource(), info == null ? "" : info.getGridSha256(),
                    3, "Prepared " + (info == null ? "" : info.getProcessedAt()), null, 0));
        }
        String version = structures.getVersion();
        list.add(new DatasetVersion("prepared-structures", DatasetType.ROAD_STRUCTURES,
                "Bridges & tunnels • " + (version.isEmpty() ? "loading" : version), DatasetStatus.PREPARED,
                "OpenStreetMap contributors (ODbL)", "", 0, "Statuses are recorded by LGU staff", null, 0));
        return list;
    }

    /**
     * The prepared datasets after the bridges and tunnels file has been read in the background,
     * so its row shows the real version instead of "loading" (2026-10-25, Settings).
     */
    public void loadPreparedDatasets(Callback<List<DatasetVersion>> callback) {
        executors.io().execute(() -> {
            try {
                structures.loadBaseBlocking();
            } catch (IOException ignored) {
                // The row keeps saying "loading"; the map shows the reading error.
            }
            executors.main().execute(() -> callback.onSuccess(preparedDatasets()));
        });
    }

    /** Reads and validates a center CSV in the background. */
    public void previewCenterCsv(InputStream stream, Callback<List<CenterCsvReader.Row>> callback) {
        executors.io().execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                List<CenterCsvReader.Row> rows = new CenterCsvReader().read(reader);
                executors.main().execute(() -> callback.onSuccess(rows));
            } catch (IOException e) {
                executors.main().execute(() -> callback.onError(e));
            }
        });
    }

    /**
     * LGU: imports the previewed centers of its own city as verified (the officer names the
     * verification source) and publishes them to residents; records a new dataset version.
     * Since 2026-10-22 only the LGU adds centers.
     */
    public void publishCenters(StaffProfile lgu, List<CenterCsvReader.Row> rows, String verificationSource,
                               String fileLabel, Callback<Integer> callback) {
        if (!lgu.canCreateCenters()) {
            callback.onError(new IllegalStateException("Only the LGU imports evacuation centers."));
            return;
        }
        String source;
        try {
            source = TextRules.requiredText("verificationSource", verificationSource);
        } catch (ValidationException e) {
            callback.onError(e);
            return;
        }
        long now = System.currentTimeMillis();
        List<EvacuationCenter> toSave = new ArrayList<>();
        List<AuditEvent> audits = new ArrayList<>();
        try {
            for (CenterCsvReader.Row row : rows) {
                if (!row.isValid() || !lgu.canManage(row.center.getCity())) continue;
                EvacuationCenter center = row.center;
                center.assignId(centers.newId());
                center.verify(source + " (imported from " + row.source + ")", lgu.getUid(), now);
                center.publishByLgu(lgu, now);
                toSave.add(center);
                audits.add(AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "IMPORT", center,
                        "Dataset import from " + fileLabel + " (published)", now));
            }
        } catch (ValidationException e) {
            callback.onError(e);
            return;
        }
        if (toSave.isEmpty()) {
            callback.onError(new ValidationException("rows", "No valid centers for " + lgu.getJurisdiction() + " in this file."));
            return;
        }
        centers.saveAll(toSave, audits, new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                DatasetVersion version = new DatasetVersion(datasets.newId(), DatasetType.EVACUATION_CENTERS,
                        lgu.getJurisdiction().getDisplayName() + " centers • " + Formats.dateTime(now), DatasetStatus.DRAFT,
                        fileLabel + "; verified: " + source, "", toSave.size(), "Imported and published " + toSave.size() + " centers",
                        lgu.getJurisdiction(), 0);
                version.publish(lgu.getUid(), now);
                datasets.save(version, AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "PUBLISH_DATASET",
                        version, "Published " + toSave.size() + " centers", now), new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        callback.onSuccess(toSave.size());
                    }

                    @Override
                    public void onError(Exception error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    /** LGU: records closure/open evidence for a bridge or tunnel (DOCX section 8). */
    public void setStructureStatus(StaffProfile lgu, String structureId, String structureName, StructureStatus status,
                                   String evidence, Callback<Void> callback) {
        if (!mayPostRoadNotices(lgu, callback)) return;
        String checkedEvidence;
        try {
            checkedEvidence = TextRules.requiredText("evidence", evidence);
        } catch (ValidationException e) {
            callback.onError(e);
            return;
        }
        long now = System.currentTimeMillis();
        AuditEvent audit = new AuditEvent(java.util.UUID.randomUUID().toString(), lgu.getUid(),
                lgu.getJurisdiction().code(), "SET_STRUCTURE_STATUS", "structure", structureId,
                status.name() + " - " + structureName + ": " + checkedEvidence, now);
        structureStatuses.setStatus(structureId, new StructureStatusRepository.StatusRecord(status, checkedEvidence,
                now, lgu.getUid()), lgu.getJurisdiction(), audit, callback);
    }

    /** LGU: posts a road closure; residents' routes are re-planned around it (SafeRouteFragment). */
    public void reportClosure(StaffProfile lgu, String name, GeoPoint location, int radiusMeters, String reason,
                              Callback<Void> callback) {
        if (!mayPostRoadNotices(lgu, callback)) return;
        long now = System.currentTimeMillis();
        try {
            RoadClosure closure = new RoadClosure(closures.newId(), name, location, radiusMeters, reason,
                    lgu.getJurisdiction(), true, now);
            closures.save(closure, AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "REPORT_CLOSURE",
                    closure, reason, now), callback);
        } catch (ValidationException e) {
            callback.onError(e);
        }
    }

    public void liftClosure(StaffProfile lgu, RoadClosure closure, String reason, Callback<Void> callback) {
        if (!mayPostRoadNotices(lgu, callback)) return;
        long now = System.currentTimeMillis();
        closure.lift(lgu.getUid(), now);
        closures.save(closure, AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "LIFT_CLOSURE",
                closure, reason == null || reason.isBlank() ? "Closure lifted" : reason, now), callback);
    }

    /** 2026-10-22: road closures and bridge/tunnel status change residents' routes, so only the LGU posts them. */
    private static boolean mayPostRoadNotices(StaffProfile staff, Callback<Void> callback) {
        if (!staff.canPostRoadNotices()) {
            callback.onError(new IllegalStateException("Only the LGU posts road closures and bridge or tunnel status."));
            return false;
        }
        return true;
    }

    public ListenerHandle listenClosures(Callback<List<RoadClosure>> callback) {
        return closures.listenActive(callback);
    }

    public ListenerHandle listenStructureStatuses(Callback<java.util.Map<String, StructureStatusRepository.StatusRecord>> callback) {
        return structureStatuses.listen(callback);
    }
}
