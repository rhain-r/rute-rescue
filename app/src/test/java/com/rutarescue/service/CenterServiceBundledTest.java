package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.persistence.BundledCenterReader;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.CenterRepository;
import com.rutarescue.repository.CenterSnapshot;
import com.rutarescue.repository.ListenerHandle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Residents always see the bundled PLM SSC list; an LGU-published center replaces its copy. */
public class CenterServiceBundledTest {

    private static EvacuationCenter center(String id, String name, MetroManilaCity city, boolean verified)
            throws ValidationException {
        VerificationInfo info = verified
                ? VerificationInfo.verified("Field check", "uid-1", 1_000L)
                : VerificationInfo.unverified("PLM SSC post 24 Jul 2024");
        return new EvacuationCenter(id, name, "Barangay 128, Tondo", city, new GeoPoint(14.631, 120.962),
                CenterStatus.NOT_REPORTED, EnumSet.noneOf(AccessibilityFeature.class), info, verified, "");
    }

    private static List<EvacuationCenter> bundled() throws ValidationException {
        List<EvacuationCenter> list = new ArrayList<>();
        list.add(center(BundledCenterReader.ID_PREFIX + "2", "Building 3, Barangay 128", MetroManilaCity.MANILA, false));
        list.add(center(BundledCenterReader.ID_PREFIX + "3", "Building 18, Barangay 128", MetroManilaCity.MANILA, false));
        return list;
    }

    @Test
    public void bundledCentersShowWhenNothingIsPublished() throws ValidationException {
        List<EvacuationCenter> shown = CenterService.withBundled(Collections.emptyList(), bundled());
        assertEquals(2, shown.size());
        assertNotNull(CenterService.nearest(shown, new GeoPoint(14.60, 120.98)));
    }

    @Test
    public void anLguCenterWithTheSameNameAndCityReplacesTheBundledCopy() throws ValidationException {
        EvacuationCenter lgu = center("abc", "  building 3,  BARANGAY 128 ", MetroManilaCity.MANILA, true);
        List<EvacuationCenter> shown = CenterService.withBundled(List.of(lgu), bundled());
        assertEquals(2, shown.size());
        assertTrue(shown.contains(lgu));
        for (EvacuationCenter center : shown) {
            assertFalse(center.getName().equals("Building 3, Barangay 128") && CenterService.isBundled(center));
        }
    }

    @Test
    public void theSameNameInAnotherCityKeepsBoth() throws ValidationException {
        EvacuationCenter lgu = center("abc", "Building 3, Barangay 128", MetroManilaCity.PASAY, true);
        assertEquals(3, CenterService.withBundled(List.of(lgu), bundled()).size());
    }

    @Test
    public void onlyBundledIdsCountAsBundled() throws ValidationException {
        assertTrue(CenterService.isBundled(bundled().get(0)));
        assertFalse(CenterService.isBundled(center("abc", "Almario School", MetroManilaCity.MANILA, true)));
        assertFalse(CenterService.isBundled(center(null, "Almario School", MetroManilaCity.MANILA, false)));
    }

    @Test
    public void listenPublishedAddsTheBundledListAndKeepsTheFreshness() throws ValidationException {
        EvacuationCenter lgu = center("abc", "Almario School", MetroManilaCity.MANILA, true);
        FakeRepository repository = new FakeRepository();
        repository.answer = new SourcedData<>(List.of(lgu), DataFreshness.LIVE, 5_000L, "LGU-published centers");
        CenterService service = new CenterService(repository, bundled());

        List<SourcedData<List<EvacuationCenter>>> received = new ArrayList<>();
        service.listenPublished(collect(received));

        assertEquals(1, received.size());
        assertEquals(3, received.get(0).getData().size());
        assertEquals(DataFreshness.LIVE, received.get(0).getFreshness());
    }

    @Test
    public void listenPublishedStillShowsTheBundledListWhenTheServerFails() throws ValidationException {
        FakeRepository repository = new FakeRepository();
        repository.failure = new Exception("offline");
        CenterService service = new CenterService(repository, bundled());

        List<SourcedData<List<EvacuationCenter>>> received = new ArrayList<>();
        service.listenPublished(collect(received));

        assertEquals(1, received.size());
        assertEquals(2, received.get(0).getData().size());
        assertEquals(DataFreshness.PREPARED, received.get(0).getFreshness());
    }

    @Test
    public void getFindsABundledCenterWithoutTheServer() throws ValidationException {
        FakeRepository repository = new FakeRepository();
        List<EvacuationCenter> list = bundled();
        CenterService service = new CenterService(repository, list);

        List<EvacuationCenter> found = new ArrayList<>();
        service.get(list.get(1).getId(), new Callback<EvacuationCenter>() {
            @Override public void onSuccess(EvacuationCenter result) { found.add(result); }
            @Override public void onError(Exception error) { throw new AssertionError(error); }
        });

        assertEquals(1, found.size());
        assertSame(list.get(1), found.get(0));
        assertFalse("the server was not asked", repository.getCalled);
    }

    private static Callback<SourcedData<List<EvacuationCenter>>> collect(List<SourcedData<List<EvacuationCenter>>> into) {
        return new Callback<SourcedData<List<EvacuationCenter>>>() {
            @Override public void onSuccess(SourcedData<List<EvacuationCenter>> result) { into.add(result); }
            @Override public void onError(Exception error) { throw new AssertionError("error reached the screen", error); }
        };
    }

    /** Answers listenPublished at once with a fixed result or error. */
    private static final class FakeRepository implements CenterRepository {
        SourcedData<List<EvacuationCenter>> answer;
        Exception failure;
        boolean getCalled;

        @Override
        public ListenerHandle listenPublished(Callback<SourcedData<List<EvacuationCenter>>> callback) {
            if (failure != null) callback.onError(failure);
            else callback.onSuccess(answer);
            return ListenerHandle.NONE;
        }

        @Override
        public ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<EvacuationCenter>>> callback) {
            return ListenerHandle.NONE;
        }

        @Override
        public void get(String id, Callback<EvacuationCenter> callback) {
            getCalled = true;
        }

        @Override
        public ListenerHandle listen(String id, Callback<CenterSnapshot> callback) {
            return ListenerHandle.NONE;
        }

        @Override
        public String newId() {
            return "new-id";
        }

        @Override
        public void save(EvacuationCenter center, AuditEvent audit, Callback<Void> callback) { }

        @Override
        public void saveAll(List<EvacuationCenter> centers, List<AuditEvent> audits, Callback<Void> callback) { }
    }
}
