package com.rutarescue.emulator;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.rutarescue.emulator.Docs.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * DOCX acceptance journey 9: "Security-rule tests prove that resident-to-resident access
 * and unauthorized LGU writes are denied."
 *
 * Runs against the Firebase Local Emulator Suite with firebase/firestore.rules loaded:
 *     cd firebase
 *     firebase emulators:start --project demo-rutarescue
 *     ./gradlew :tools:emulator:test
 * When the emulators are not running, every test is SKIPPED (not failed).
 */
public class SecurityRulesTest {

    private static final EmulatorClient db = EmulatorClient.local();
    private static String residentA, tokenA;
    private static String residentB, tokenB;
    private static String lguManila, tokenManila;
    private static String lguPasig, tokenPasig;

    @BeforeClass
    public static void setUp() throws IOException {
        Assume.assumeTrue("Firebase emulators are not running - rules tests skipped", db.isRunning());
        db.resetAll();
        String[] a = db.signUp("resident.a@rules.test", "Password123");
        String[] b = db.signUp("resident.b@rules.test", "Password123");
        String[] m = db.signUp("lgu.manila@rules.test", "Password123");
        String[] p = db.signUp("lgu.pasig@rules.test", "Password123");
        residentA = a[0]; tokenA = a[1];
        residentB = b[0]; tokenB = b[1];
        lguManila = m[0]; tokenManila = m[1];
        lguPasig = p[0]; tokenPasig = p[1];
        // Pre-provisioned LGU staff: only an administrator (owner) can create these.
        assertTrue(db.set("lgu_staff/" + lguManila, Docs.lguStaff("Manila Officer", "lgu.manila@rules.test", "MANILA", "DRRM Officer"), EmulatorClient.OWNER).ok());
        assertTrue(db.set("lgu_staff/" + lguPasig, Docs.lguStaff("Pasig Officer", "lgu.pasig@rules.test", "PASIG", "DRRM Officer"), EmulatorClient.OWNER).ok());
    }

    // ------------------------------------------------------------------ residents

    @Test
    public void residentReadsOwnFamilyButNeverAnotherResidents() throws IOException {
        assertTrue(db.set("families/" + residentA, Docs.family(residentA, "MANILA"), tokenA).ok());
        assertTrue(db.get("families/" + residentA, tokenA).ok());
        assertTrue("Resident B must not read Resident A's record", db.get("families/" + residentA, tokenB).denied());
        assertTrue("Resident B must not overwrite Resident A's record",
                db.set("families/" + residentA, Docs.family(residentB, "MANILA"), tokenB).denied());
    }

    @Test
    public void registrationCanOnlyCreateResidents() throws IOException {
        Map<String, Object> pretendLgu = Docs.resident("Sneaky", "resident.a@rules.test", "MANILA");
        pretendLgu.put("role", "LGU");
        assertTrue(db.set("users/" + residentA, pretendLgu, tokenA).denied());
        assertTrue(db.set("users/" + residentA, Docs.resident("Resident A", "resident.a@rules.test", "MANILA"), tokenA).ok());
        assertTrue("Nobody can create LGU staff from the app",
                db.set("lgu_staff/" + residentA, Docs.lguStaff("Fake", "x@rules.test", "MANILA", "Fake"), tokenA).denied());
    }

    @Test
    public void residentCannotWriteLguData() throws IOException {
        String centerId = "center-" + UUID.randomUUID();
        String auditId = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("centers/" + centerId, Docs.center("MANILA", true, true, auditId));
        batch.put("auditEvents/" + auditId, Docs.audit(residentA, "MANILA", "CREATE", "center", centerId, "fake"));
        assertTrue(db.commit(batch, tokenA).denied());
    }

    // ------------------------------------------------------------------ LGU jurisdiction + audit

    @Test
    public void lguWritesNeedAnAuditEventInTheSameBatch() throws IOException {
        String advisoryId = "adv-" + UUID.randomUUID();
        String auditId = UUID.randomUUID().toString();
        assertTrue("Advisory without its audit event must be rejected",
                db.set("advisories/" + advisoryId, Docs.advisory("MANILA", "Heavy rain", "Avoid Riverside Road.", auditId), tokenManila).denied());

        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("advisories/" + advisoryId, Docs.advisory("MANILA", "Heavy rain", "Avoid Riverside Road.", auditId));
        batch.put("auditEvents/" + auditId, Docs.audit(lguManila, "MANILA", "PUBLISH", "advisory", advisoryId, "demo"));
        assertTrue(db.commit(batch, tokenManila).ok());
        assertTrue("Residents can read published advisories", db.get("advisories/" + advisoryId, tokenA).ok());
    }

    @Test
    public void lguCannotWriteAnotherJurisdiction() throws IOException {
        String advisoryId = "adv-" + UUID.randomUUID();
        String auditId = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("advisories/" + advisoryId, Docs.advisory("MANILA", "Wrong city", "Pasig writing for Manila.", auditId));
        batch.put("auditEvents/" + auditId, Docs.audit(lguPasig, "PASIG", "PUBLISH", "advisory", advisoryId, "demo"));
        assertTrue(db.commit(batch, tokenPasig).denied());
    }

    @Test
    public void auditEventsAreAppendOnly() throws IOException {
        String auditId = UUID.randomUUID().toString();
        assertTrue(db.set("auditEvents/" + auditId, Docs.audit(lguManila, "MANILA", "NOTE", "test", "r1", "first"), tokenManila).ok());
        assertTrue(db.set("auditEvents/" + auditId, Docs.audit(lguManila, "MANILA", "NOTE", "test", "r1", "edited"), tokenManila).denied());
        assertTrue(db.delete("auditEvents/" + auditId, tokenManila).denied());
        assertTrue("Other jurisdictions cannot read it", db.get("auditEvents/" + auditId, tokenPasig).denied());
    }

    @Test
    public void unverifiedCentersCannotBePublishedAndDraftsStayHidden() throws IOException {
        String centerId = "center-" + UUID.randomUUID();
        String draftAudit = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> draft = new LinkedHashMap<>();
        draft.put("centers/" + centerId, Docs.center("MANILA", false, false, draftAudit));
        draft.put("auditEvents/" + draftAudit, Docs.audit(lguManila, "MANILA", "SAVE", "center", centerId, "draft"));
        assertTrue(db.commit(draft, tokenManila).ok());
        assertTrue("Draft centers are hidden from residents", db.get("centers/" + centerId, tokenA).denied());

        String badAudit = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> unverifiedPublish = new LinkedHashMap<>();
        unverifiedPublish.put("centers/" + centerId, Docs.center("MANILA", false, true, badAudit));
        unverifiedPublish.put("auditEvents/" + badAudit, Docs.audit(lguManila, "MANILA", "PUBLISH", "center", centerId, "x"));
        assertTrue(db.commit(unverifiedPublish, tokenManila).denied());

        String goodAudit = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> publish = new LinkedHashMap<>();
        publish.put("centers/" + centerId, Docs.center("MANILA", true, true, goodAudit));
        publish.put("auditEvents/" + goodAudit, Docs.audit(lguManila, "MANILA", "VERIFY_AND_PUBLISH", "center", centerId, "checked"));
        assertTrue(db.commit(publish, tokenManila).ok());
        assertTrue(db.get("centers/" + centerId, tokenA).ok());
    }

    /** Phase 2 capacity screen: the database refuses a headcount above 120% of capacity. */
    @Test
    public void centerHeadcountCannotPassOneHundredTwentyPercent() throws IOException {
        String centerId = "center-" + UUID.randomUUID();

        String overAudit = UUID.randomUUID().toString();
        Map<String, Object> over = Docs.center("MANILA", true, true, overAudit);
        over.put("capacity", 120);
        over.put("occupancy", 145); // limit is 144
        Map<String, Map<String, Object>> tooMany = new LinkedHashMap<>();
        tooMany.put("centers/" + centerId, over);
        tooMany.put("auditEvents/" + overAudit, Docs.audit(lguManila, "MANILA", "UPDATE_CAPACITY", "center", centerId, "x"));
        assertTrue("145 of 120 people is refused", db.commit(tooMany, tokenManila).denied());

        String okAudit = UUID.randomUUID().toString();
        Map<String, Object> atLimit = Docs.center("MANILA", true, true, okAudit);
        atLimit.put("capacity", 120);
        atLimit.put("occupancy", 144);
        Map<String, Map<String, Object>> allowed = new LinkedHashMap<>();
        allowed.put("centers/" + centerId, atLimit);
        allowed.put("auditEvents/" + okAudit, Docs.audit(lguManila, "MANILA", "UPDATE_CAPACITY", "center", centerId, "ok"));
        assertTrue("144 of 120 people (exactly 120%) is allowed", db.commit(allowed, tokenManila).ok());
    }

    // ------------------------------------------------------------------ Ligtas Priority

    @Test
    public void applicationsAreVisibleOnlyToOwnerAndSameCityLgu() throws IOException {
        String appId = "app-" + UUID.randomUUID();
        assertTrue(db.set("ligtasApplications/" + appId, Docs.application(residentA, "MANILA"), tokenA).ok());
        assertTrue(db.get("ligtasApplications/" + appId, tokenA).ok());
        assertTrue("Another resident is denied", db.get("ligtasApplications/" + appId, tokenB).denied());
        assertTrue("Another city's LGU is denied", db.get("ligtasApplications/" + appId, tokenPasig).denied());
        assertTrue(db.get("ligtasApplications/" + appId, tokenManila).ok());
        assertTrue(db.queryEquals("ligtasApplications", "city", "MANILA", tokenManila).ok());
        assertTrue(db.queryEquals("ligtasApplications", "city", "MANILA", tokenPasig).denied());
        assertTrue("A resident cannot submit for someone else",
                db.set("ligtasApplications/x-" + appId, Docs.application(residentB, "MANILA"), tokenA).denied());
    }

    @Test
    public void onlySameCityLguDecidesOnceWithReasonAndAudit() throws IOException {
        String appId = "app-" + UUID.randomUUID();
        Map<String, Object> pending = Docs.application(residentA, "MANILA");
        assertTrue(db.set("ligtasApplications/" + appId, pending, tokenA).ok());

        assertTrue("The resident cannot approve their own application",
                db.set("ligtasApplications/" + appId, Docs.decided(pending, "APPROVED", residentA, "Self approval", null), tokenA).denied());

        String pasigAudit = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> wrongCity = new LinkedHashMap<>();
        wrongCity.put("ligtasApplications/" + appId, Docs.decided(pending, "APPROVED", lguPasig, "Looks fine", pasigAudit));
        wrongCity.put("auditEvents/" + pasigAudit, Docs.audit(lguPasig, "PASIG", "APPROVE", "ligtasApplication", appId, "x"));
        assertTrue(db.commit(wrongCity, tokenPasig).denied());

        String noReasonAudit = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> noReason = new LinkedHashMap<>();
        noReason.put("ligtasApplications/" + appId, Docs.decided(pending, "APPROVED", lguManila, "", noReasonAudit));
        noReason.put("auditEvents/" + noReasonAudit, Docs.audit(lguManila, "MANILA", "APPROVE", "ligtasApplication", appId, ""));
        assertTrue("A reason is required", db.commit(noReason, tokenManila).denied());

        assertTrue("A decision without an audit event is rejected", db.set("ligtasApplications/" + appId,
                Docs.decided(pending, "APPROVED", lguManila, "Proof matches category", "missing-audit"), tokenManila).denied());

        String auditId = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> approve = new LinkedHashMap<>();
        Map<String, Object> approved = Docs.decided(pending, "APPROVED", lguManila, "Synthetic proof matches the senior category", auditId);
        approve.put("ligtasApplications/" + appId, approved);
        approve.put("auditEvents/" + auditId, Docs.audit(lguManila, "MANILA", "APPROVE", "ligtasApplication", appId, "ok"));
        assertTrue(db.commit(approve, tokenManila).ok());

        String secondAudit = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> again = new LinkedHashMap<>();
        again.put("ligtasApplications/" + appId, Docs.decided(approved, "REJECTED", lguManila, "Changed my mind", secondAudit));
        again.put("auditEvents/" + secondAudit, Docs.audit(lguManila, "MANILA", "REJECT", "ligtasApplication", appId, "x"));
        assertTrue("Decisions are final", db.commit(again, tokenManila).denied());
    }

    @Test
    public void structureStatusNeedsLguAuditAndValidStatus() throws IOException {
        String auditId = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("structureStatus/w123", map("status", "CLOSED", "evidence", "Approach flooded (demo)",
                "updatedAt", System.currentTimeMillis(), "updatedBy", lguManila, "jurisdiction", "MANILA", "lastAuditId", auditId));
        batch.put("auditEvents/" + auditId, Docs.audit(lguManila, "MANILA", "SET_STRUCTURE_STATUS", "structure", "w123", "closed"));
        assertTrue(db.commit(batch, tokenManila).ok());
        assertTrue("Residents can read statuses (needed for routing)", db.get("structureStatus/w123", tokenA).ok());
        assertEquals(403, db.set("structureStatus/w999", map("status", "VERIFIED_OPEN", "evidence", "x",
                "updatedAt", 1L, "updatedBy", residentA, "jurisdiction", "MANILA", "lastAuditId", "none"), tokenA).status);
    }
}
