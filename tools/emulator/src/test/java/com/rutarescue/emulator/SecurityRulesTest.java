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
    private static String modManila, tokenModManila;
    /** The center the Manila moderator runs, and another Manila center and a Pasig one. */
    private static final String MOD_CENTER = "center-almario";
    private static final String OTHER_CENTER = "center-delpan";
    private static final String PASIG_CENTER = "center-pasig";

    @BeforeClass
    public static void setUp() throws IOException {
        Assume.assumeTrue("Firebase emulators are not running - rules tests skipped", db.isRunning());
        db.resetAll();
        String[] a = db.signUp("resident.a@rules.test", "Password123");
        String[] b = db.signUp("resident.b@rules.test", "Password123");
        String[] m = db.signUp("lgu.manila@rules.test", "Password123");
        String[] p = db.signUp("lgu.pasig@rules.test", "Password123");
        String[] mm = db.signUp("moderator.manila@rules.test", "Password123");
        residentA = a[0]; tokenA = a[1];
        residentB = b[0]; tokenB = b[1];
        lguManila = m[0]; tokenManila = m[1];
        lguPasig = p[0]; tokenPasig = p[1];
        modManila = mm[0]; tokenModManila = mm[1];
        // Pre-provisioned LGU staff: only an administrator (owner) can create these.
        assertTrue(db.set("lgu_staff/" + lguManila, Docs.lguStaff("Manila Officer", "lgu.manila@rules.test", "MANILA", "DRRM Officer"), EmulatorClient.OWNER).ok());
        assertTrue(db.set("lgu_staff/" + lguPasig, Docs.lguStaff("Pasig Officer", "lgu.pasig@rules.test", "PASIG", "DRRM Officer"), EmulatorClient.OWNER).ok());
        // Centers made by the LGUs, and a Manila moderator who runs MOD_CENTER (2026-10-22).
        assertTrue(db.set("centers/" + MOD_CENTER, Docs.center("MANILA", true, true, "seed"), EmulatorClient.OWNER).ok());
        assertTrue(db.set("centers/" + OTHER_CENTER, Docs.center("MANILA", true, true, "seed"), EmulatorClient.OWNER).ok());
        assertTrue(db.set("centers/" + PASIG_CENTER, Docs.center("PASIG", true, true, "seed"), EmulatorClient.OWNER).ok());
        assertTrue(db.set("lgu_staff/" + modManila, Docs.moderator("Manila Moderator", "moderator.manila@rules.test", "MANILA",
                lguManila, MOD_CENTER, "Almario Elementary School"), EmulatorClient.OWNER).ok());
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
    public void onlyTheLguCreatesAndPublishesCenters() throws IOException {
        String centerId = "center-" + UUID.randomUUID();
        String modAudit = UUID.randomUUID().toString();
        assertTrue("Moderators no longer add centers",
                commit(centerId, Docs.center("MANILA", true, false, modAudit), modAudit, modManila, tokenModManila, "SAVE").denied());

        String unverifiedAudit = UUID.randomUUID().toString();
        assertTrue("A published center must be verified",
                commit(centerId, Docs.center("MANILA", false, true, unverifiedAudit), unverifiedAudit, lguManila, tokenManila, "SAVE").denied());

        String draftAudit = UUID.randomUUID().toString();
        Map<String, Object> draft = Docs.center("MANILA", false, false, draftAudit);
        assertTrue(commit(centerId, draft, draftAudit, lguManila, tokenManila, "SAVE").ok());
        assertTrue("Draft centers are hidden from residents", db.get("centers/" + centerId, tokenA).denied());
        assertTrue("Staff of the city can read the draft", db.get("centers/" + centerId, tokenModManila).ok());

        String pasigAudit = UUID.randomUUID().toString();
        Map<String, Object> published = Docs.center("MANILA", true, true, pasigAudit);
        assertTrue("Another city's LGU cannot publish it", commit(centerId, published, pasigAudit, lguPasig, tokenPasig, "PUBLISH").denied());

        String publishAudit = UUID.randomUUID().toString();
        published.put("lastAuditId", publishAudit);
        assertTrue(commit(centerId, published, publishAudit, lguManila, tokenManila, "VERIFY_AND_PUBLISH").ok());
        assertTrue("Residents see it once the LGU published it", db.get("centers/" + centerId, tokenA).ok());
    }

    @Test
    public void aModeratorChangesOnlyTheHeadcountOfTheirOwnCenter() throws IOException {
        String countAudit = UUID.randomUUID().toString();
        Map<String, Object> counted = Docs.center("MANILA", true, true, countAudit);
        counted.put("capacity", 100);
        counted.put("occupancy", 40);
        counted.put("status", "NEAR_CAPACITY");
        assertTrue(commit(MOD_CENTER, counted, countAudit, modManila, tokenModManila, "UPDATE_CAPACITY").ok());

        String renameAudit = UUID.randomUUID().toString();
        Map<String, Object> renamed = new java.util.HashMap<>(counted);
        renamed.put("name", "Renamed by a moderator");
        renamed.put("lastAuditId", renameAudit);
        assertTrue("Only the LGU edits the details", commit(MOD_CENTER, renamed, renameAudit, modManila, tokenModManila, "SAVE").denied());

        String hideAudit = UUID.randomUUID().toString();
        Map<String, Object> hidden = new java.util.HashMap<>(counted);
        hidden.put("published", false);
        hidden.put("lastAuditId", hideAudit);
        assertTrue("A moderator cannot hide a center", commit(MOD_CENTER, hidden, hideAudit, modManila, tokenModManila, "SAVE").denied());

        String otherAudit = UUID.randomUUID().toString();
        Map<String, Object> other = Docs.center("MANILA", true, true, otherAudit);
        other.put("capacity", 100);
        other.put("occupancy", 10);
        assertTrue("Not another center of the city", commit(OTHER_CENTER, other, otherAudit, modManila, tokenModManila, "UPDATE_CAPACITY").denied());
    }

    @Test
    public void theLguCannotPublishAnUnverifiedCenter() throws IOException {
        String centerId = "center-" + UUID.randomUUID();
        String draftAudit = UUID.randomUUID().toString();
        assertTrue(commit(centerId, Docs.center("MANILA", false, false, draftAudit), draftAudit, lguManila, tokenManila, "SAVE").ok());
        String lguAudit = UUID.randomUUID().toString();
        Map<String, Object> publish = Docs.center("MANILA", false, true, lguAudit);
        assertTrue(commit(centerId, publish, lguAudit, lguManila, tokenManila, "PUBLISH").denied());
    }

    /** Phase 2 capacity screen: the database refuses a headcount above 120% of capacity. */
    @Test
    public void centerHeadcountCannotPassOneHundredTwentyPercent() throws IOException {
        String overAudit = UUID.randomUUID().toString();
        Map<String, Object> over = Docs.center("MANILA", true, true, overAudit);
        over.put("capacity", 120);
        over.put("occupancy", 145); // limit is 144
        assertTrue("145 of 120 people is refused", commit(MOD_CENTER, over, overAudit, modManila, tokenModManila, "UPDATE_CAPACITY").denied());

        String okAudit = UUID.randomUUID().toString();
        Map<String, Object> atLimit = Docs.center("MANILA", true, true, okAudit);
        atLimit.put("capacity", 120);
        atLimit.put("occupancy", 144);
        assertTrue("144 of 120 people (exactly 120%) is allowed",
                commit(MOD_CENTER, atLimit, okAudit, modManila, tokenModManila, "UPDATE_CAPACITY").ok());
    }

    // ------------------------------------------------------------------ announcements

    @Test
    public void moderatorAdvisoriesStayPendingUntilTheLguAcceptsThem() throws IOException {
        String advisoryId = "adv-" + UUID.randomUUID();
        String publishAudit = UUID.randomUUID().toString();
        assertTrue("A moderator cannot publish an advisory",
                commitAdvisory(advisoryId, Docs.advisory("MANILA", "Center open", "Almario school is open.", publishAudit),
                        publishAudit, modManila, tokenModManila).denied());

        String levelAudit = UUID.randomUUID().toString();
        Map<String, Object> withLevel = Docs.moderatorAdvisory("MANILA", MOD_CENTER, modManila, levelAudit);
        withLevel.put("level", "ALERT_3");
        assertTrue("Only the LGU sets alert levels", commitAdvisory(advisoryId, withLevel, levelAudit, modManila, tokenModManila).denied());

        String otherAudit = UUID.randomUUID().toString();
        assertTrue("Only about their own center", commitAdvisory(advisoryId,
                Docs.moderatorAdvisory("MANILA", OTHER_CENTER, modManila, otherAudit), otherAudit, modManila, tokenModManila).denied());

        String pendingAudit = UUID.randomUUID().toString();
        Map<String, Object> pending = Docs.moderatorAdvisory("MANILA", MOD_CENTER, modManila, pendingAudit);
        assertTrue(commitAdvisory(advisoryId, pending, pendingAudit, modManila, tokenModManila).ok());
        assertTrue("Residents do not see pending advisories", db.get("advisories/" + advisoryId, tokenA).denied());

        String fixAudit = UUID.randomUUID().toString();
        Map<String, Object> fixed = new java.util.HashMap<>(pending);
        fixed.put("message", "Almario school has room for 10 more people.");
        fixed.put("lastAuditId", fixAudit);
        assertTrue("Fixed while pending", commitAdvisory(advisoryId, fixed, fixAudit, modManila, tokenModManila).ok());

        String approveAudit = UUID.randomUUID().toString();
        Map<String, Object> approved = new java.util.HashMap<>(fixed);
        approved.put("published", true);
        approved.put("review", "APPROVED");
        approved.put("awaitingLgu", false);
        approved.put("level", "ALERT_2");
        approved.put("lastAuditId", approveAudit);
        assertTrue(commitAdvisory(advisoryId, approved, approveAudit, lguManila, tokenManila).ok());
        assertTrue(db.get("advisories/" + advisoryId, tokenA).ok());

        String editAudit = UUID.randomUUID().toString();
        Map<String, Object> edited = new java.util.HashMap<>(approved);
        edited.put("published", false);
        edited.put("lastAuditId", editAudit);
        assertTrue("A moderator cannot change or hide a published advisory",
                commitAdvisory(advisoryId, edited, editAudit, modManila, tokenModManila).denied());
    }

    @Test
    public void aDeclinedAdvisoryIsNeverPublished() throws IOException {
        String advisoryId = "adv-" + UUID.randomUUID();
        String pendingAudit = UUID.randomUUID().toString();
        Map<String, Object> pending = Docs.moderatorAdvisory("MANILA", MOD_CENTER, modManila, pendingAudit);
        assertTrue(commitAdvisory(advisoryId, pending, pendingAudit, modManila, tokenModManila).ok());

        String declineAudit = UUID.randomUUID().toString();
        Map<String, Object> declined = new java.util.HashMap<>(pending);
        declined.put("review", "DECLINED");
        declined.put("awaitingLgu", false);
        declined.put("lguNote", "Please add how many people it can still take.");
        declined.put("lastAuditId", declineAudit);
        assertTrue(commitAdvisory(advisoryId, declined, declineAudit, lguManila, tokenManila).ok());

        String publishAudit = UUID.randomUUID().toString();
        Map<String, Object> publishedAnyway = new java.util.HashMap<>(declined);
        publishedAnyway.put("published", true);
        publishedAnyway.put("lastAuditId", publishAudit);
        assertTrue("Not even the LGU publishes a declined advisory",
                commitAdvisory(advisoryId, publishedAnyway, publishAudit, lguManila, tokenManila).denied());
        Map<String, Object> reopened = new java.util.HashMap<>(declined);
        reopened.put("review", "APPROVED");
        reopened.put("published", true);
        reopened.put("lastAuditId", publishAudit);
        assertTrue(commitAdvisory(advisoryId, reopened, publishAudit, lguManila, tokenManila).denied());

        String retryAudit = UUID.randomUUID().toString();
        Map<String, Object> retried = new java.util.HashMap<>(declined);
        retried.put("review", "PENDING");
        retried.put("lastAuditId", retryAudit);
        assertTrue("The moderator writes a new one instead", commitAdvisory(advisoryId, retried, retryAudit, modManila, tokenModManila).denied());
    }

    // ------------------------------------------------------------------ alert level and road notices (2026-10-22)

    @Test
    public void onlyTheCitysLguSetsTheAlertLevel() throws IOException {
        String auditId = UUID.randomUUID().toString();
        assertTrue("A moderator cannot", commitAlert("MANILA", "ALERT_2", auditId, modManila, tokenModManila, "MANILA").denied());
        assertTrue("Another city's LGU cannot", commitAlert("MANILA", "ALERT_2", auditId, lguPasig, tokenPasig, "PASIG").denied());
        assertTrue("Only real levels", commitAlert("MANILA", "ALERT_9", auditId, lguManila, tokenManila, "MANILA").denied());
        assertTrue(commitAlert("MANILA", "ALERT_2", auditId, lguManila, tokenManila, "MANILA").ok());
        assertTrue("Residents read it", db.get("cityAlerts/MANILA", tokenA).ok());
    }

    @Test
    public void onlyTheLguPostsRoadClosures() throws IOException {
        String closureId = "closure-" + UUID.randomUUID();
        String modAudit = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("closures/" + closureId, Docs.closure("MANILA", modAudit));
        batch.put("auditEvents/" + modAudit, Docs.audit(modManila, "MANILA", "REPORT_CLOSURE", "roadClosure", closureId, "x"));
        assertTrue("Moderators no longer post closures", db.commit(batch, tokenModManila).denied());

        String lguAudit = UUID.randomUUID().toString();
        batch.clear();
        batch.put("closures/" + closureId, Docs.closure("MANILA", lguAudit));
        batch.put("auditEvents/" + lguAudit, Docs.audit(lguManila, "MANILA", "REPORT_CLOSURE", "roadClosure", closureId, "x"));
        assertTrue(db.commit(batch, tokenManila).ok());
        assertTrue("Residents read closures (routes avoid them)", db.get("closures/" + closureId, tokenA).ok());
    }

    private static EmulatorClient.Response commitAlert(String city, String level, String auditId, String actor, String token,
                                                       String actorCity) throws IOException {
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("cityAlerts/" + city, Docs.cityAlert(city, level, auditId));
        batch.put("auditEvents/" + auditId, Docs.audit(actor, actorCity, "SET_ALERT_LEVEL", "cityAlert", city, level));
        return db.commit(batch, token);
    }

    // ------------------------------------------------------------------ priority IDs (2026-10-22)

    @Test
    public void priorityIdsOpenOnlyByTheirCodeForModerators() throws IOException {
        String cardId = "ABCDEFGHJKLM";
        Map<String, Object> card = Docs.priorityCard(residentA, "MANILA", "SENIOR_CITIZEN");
        Map<String, Object> prechecked = new java.util.HashMap<>(card);
        prechecked.put("checkedBy", residentA);
        assertTrue("A resident cannot check their own ID", db.set("priorityIds/" + cardId, prechecked, tokenA).denied());
        assertTrue("Not for someone else", db.set("priorityIds/" + cardId, Docs.priorityCard(residentB, "MANILA", "PREGNANT"), tokenA).denied());
        assertTrue(db.set("priorityIds/" + cardId, card, tokenA).ok());

        assertTrue(db.get("priorityIds/" + cardId, tokenA).ok());
        assertTrue("Another resident cannot open it", db.get("priorityIds/" + cardId, tokenB).denied());
        assertTrue("A moderator opens it by its code", db.get("priorityIds/" + cardId, tokenModManila).ok());
        assertTrue("...but cannot list everyone's IDs", db.queryEquals("priorityIds", "city", "MANILA", tokenModManila).denied());
        assertTrue("The owner finds their own", db.queryEquals("priorityIds", "ownerUid", residentA, tokenA).ok());
        assertTrue("The LGU does not check IDs", db.get("priorityIds/" + cardId, tokenManila).denied());

        String auditId = UUID.randomUUID().toString();
        Map<String, Object> checked = new java.util.HashMap<>(card);
        checked.put("checks", map("valid_id", "OK", "senior_id", "MISSING", "address", "OK", "emergency_contact", "OK",
                "medicines", "OK"));
        checked.put("missingCount", 1);
        checked.put("flagNote", "Bring the OSCA ID.");
        checked.put("checkedBy", modManila);
        checked.put("checkedByName", "Manila Moderator");
        checked.put("checkedAt", System.currentTimeMillis());
        checked.put("checkedAtCenter", "Almario Elementary School");
        checked.put("lastAuditId", auditId);
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("priorityIds/" + cardId, checked);
        batch.put("auditEvents/" + auditId, Docs.audit(modManila, "MANILA", "CHECK_PRIORITY_ID", "priorityId", cardId, "1 missing"));
        assertTrue(db.commit(batch, tokenModManila).ok());

        Map<String, Object> renamed = new java.util.HashMap<>(checked);
        renamed.put("holderName", "Someone Else");
        renamed.put("lastAuditId", UUID.randomUUID().toString());
        assertTrue("A moderator changes only the check", db.set("priorityIds/" + cardId, renamed, tokenModManila).denied());
    }

    // ------------------------------------------------------------------ moderators and their centers (2026-10-22)

    @Test
    public void theLguAssignsItsModeratorsToItsOwnCenters() throws IOException {
        assertTrue("A moderator cannot pick their own center",
                assign(modManila, OTHER_CENTER, modManila, tokenModManila, "MANILA").denied());
        assertTrue("Never a center of another city", assign(modManila, PASIG_CENTER, lguManila, tokenManila, "MANILA").denied());
        assertTrue("Another city's LGU cannot", assign(modManila, OTHER_CENTER, lguPasig, tokenPasig, "PASIG").denied());
        assertTrue(assign(modManila, OTHER_CENTER, lguManila, tokenManila, "MANILA").ok());
        assertTrue("and back to the first center", assign(modManila, MOD_CENTER, lguManila, tokenManila, "MANILA").ok());
    }

    private static EmulatorClient.Response assign(String moderatorUid, String centerId, String actor, String token,
                                                  String actorCity) throws IOException {
        String auditId = UUID.randomUUID().toString();
        Map<String, Object> staff = Docs.moderator("Manila Moderator", "moderator.manila@rules.test", "MANILA", lguManila,
                centerId, "Center " + centerId);
        EmulatorClient.Response stored = db.get("lgu_staff/" + moderatorUid, EmulatorClient.OWNER);
        staff.put("approvedAt", Long.parseLong(stored.body.getAsJsonObject("fields").getAsJsonObject("approvedAt")
                .get("integerValue").getAsString()));
        staff.put("assignedBy", actor);
        staff.put("assignedAt", System.currentTimeMillis());
        staff.put("lastAuditId", auditId);
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("lgu_staff/" + moderatorUid, staff);
        batch.put("auditEvents/" + auditId, Docs.audit(actor, actorCity, "ASSIGN_CENTER", "staff", moderatorUid, "assign"));
        return db.commit(batch, token);
    }

    // ------------------------------------------------------------------ staff sign-up

    @Test
    public void aStaffSignUpStaysLockedUntilTheSameCityLguApprovesIt() throws IOException {
        String[] newcomer = db.signUp("new.moderator@rules.test", "Password123");
        String uid = newcomer[0];
        String token = newcomer[1];
        Map<String, Object> request = Docs.staffRequest("New Moderator", "new.moderator@rules.test", "MODERATOR", "MANILA");
        Map<String, Object> approvedStaff = Docs.staff("New Moderator", "new.moderator@rules.test", "MANILA",
                "Barangay DRRM", "MODERATOR", uid);
        assertTrue("Nobody makes themselves staff", db.set("lgu_staff/" + uid, approvedStaff, token).denied());
        Map<String, Object> approvedRequest = new java.util.HashMap<>(request);
        approvedRequest.put("status", "APPROVED");
        assertTrue("A request cannot start approved", db.set("staffRequests/" + uid, approvedRequest, token).denied());
        assertTrue(db.set("staffRequests/" + uid, request, token).ok());
        assertTrue("Only the person and their city's LGU read it", db.get("staffRequests/" + uid, tokenB).denied());
        assertTrue(db.get("staffRequests/" + uid, tokenManila).ok());

        assertTrue("A moderator cannot approve staff", approve(uid, request, modManila, tokenModManila, "MODERATOR", OTHER_CENTER).denied());
        assertTrue("Another city's LGU cannot approve", approve(uid, request, lguPasig, tokenPasig, "MODERATOR", OTHER_CENTER).denied());
        assertTrue("The role cannot be upgraded on approval", approve(uid, request, lguManila, tokenManila, "LGU", "").denied());
        assertTrue("A moderator needs a center", approve(uid, request, lguManila, tokenManila, "MODERATOR", "").denied());
        assertTrue("...of the same city", approve(uid, request, lguManila, tokenManila, "MODERATOR", PASIG_CENTER).denied());
        assertTrue(approve(uid, request, lguManila, tokenManila, "MODERATOR", OTHER_CENTER).ok());
        assertTrue("The new moderator can read their staff record", db.get("lgu_staff/" + uid, token).ok());
    }

    /** The LGU approves: staff record + decided request + audit event, in one batch (like the app). */
    private static EmulatorClient.Response approve(String uid, Map<String, Object> request, String lguUid, String lguToken,
                                                   String role, String centerId) throws IOException {
        String auditId = UUID.randomUUID().toString();
        Map<String, Object> decided = new java.util.HashMap<>(request);
        decided.put("status", "APPROVED");
        decided.put("decidedBy", lguUid);
        decided.put("decisionReason", "Approved");
        decided.put("decidedAt", System.currentTimeMillis());
        decided.put("lastAuditId", auditId);
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        Map<String, Object> staff = Docs.staff((String) request.get("displayName"), (String) request.get("email"),
                (String) request.get("jurisdiction"), "Barangay DRRM", role, lguUid);
        if ("MODERATOR".equals(role)) {
            staff.put("centerId", centerId);
            staff.put("centerName", "Center " + centerId);
        }
        batch.put("lgu_staff/" + uid, staff);
        batch.put("staffRequests/" + uid, decided);
        batch.put("auditEvents/" + auditId, Docs.audit(lguUid, (String) request.get("jurisdiction"), "APPROVE",
                "staffRequest", uid, "Approved"));
        return db.commit(batch, lguToken);
    }

    private static EmulatorClient.Response commit(String centerId, Map<String, Object> center, String auditId,
                                                  String actor, String token, String action) throws IOException {
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("centers/" + centerId, center);
        batch.put("auditEvents/" + auditId, Docs.audit(actor, "MANILA", action, "center", centerId, action));
        return db.commit(batch, token);
    }

    private static EmulatorClient.Response commitAdvisory(String advisoryId, Map<String, Object> advisory, String auditId,
                                                         String actor, String token) throws IOException {
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("advisories/" + advisoryId, advisory);
        batch.put("auditEvents/" + auditId, Docs.audit(actor, "MANILA", "SAVE", "advisory", advisoryId, "test"));
        return db.commit(batch, token);
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
        assertTrue(db.get("ligtasApplications/" + appId, tokenModManila).ok());
        assertTrue(db.queryEquals("ligtasApplications", "city", "MANILA", tokenManila).ok());
        assertTrue(db.queryEquals("ligtasApplications", "city", "MANILA", tokenPasig).denied());
        assertTrue("A resident cannot submit for someone else",
                db.set("ligtasApplications/x-" + appId, Docs.application(residentB, "MANILA"), tokenA).denied());
    }

    @Test
    public void onlyTheSameCityModeratorDecidesWithAMessageAndAudit() throws IOException {
        String appId = "app-" + UUID.randomUUID();
        Map<String, Object> pending = Docs.application(residentA, "MANILA");
        assertTrue(db.set("ligtasApplications/" + appId, pending, tokenA).ok());

        assertTrue("The resident cannot approve their own application",
                db.set("ligtasApplications/" + appId, Docs.decided(pending, "APPROVED", residentA, "Self approval", null), tokenA).denied());
        assertTrue("The LGU no longer decides Ligtas", decide(appId, pending, "APPROVED", lguManila, tokenManila, "Proof is fine").denied());
        assertTrue("Another city's staff cannot decide", decide(appId, pending, "APPROVED", lguPasig, tokenPasig, "Looks fine").denied());
        assertTrue("A message is required", decide(appId, pending, "APPROVED", modManila, tokenModManila, "").denied());
        assertTrue("A decision without an audit event is rejected", db.set("ligtasApplications/" + appId,
                Docs.decided(pending, "APPROVED", modManila, "Proof matches category", "missing-audit"), tokenModManila).denied());

        Map<String, Object> approved = Docs.decided(pending, "APPROVED", modManila, "Synthetic proof matches the senior category", null);
        assertTrue(decide(appId, pending, "APPROVED", modManila, tokenModManila, "Synthetic proof matches the senior category").ok());

        // Approved by mistake: the moderator can change it and ask for documents.
        assertTrue(decide(appId, approved, "NEEDS_DOCUMENTS", modManila, tokenModManila, "Please attach the medical certificate").ok());
        Map<String, Object> needsDocs = Docs.decided(approved, "NEEDS_DOCUMENTS", modManila, "Please attach the medical certificate", null);
        needsDocs.remove("lastAuditId");

        Map<String, Object> resubmitted = new java.util.HashMap<>(needsDocs);
        resubmitted.put("status", "APPROVED");
        assertTrue("The resident cannot approve while resending", db.set("ligtasApplications/" + appId, resubmitted, tokenA).denied());
        resubmitted.put("status", "PENDING");
        resubmitted.put("proofRef", "inline:" + appId + "-3");
        assertTrue("The owner sends new documents", db.set("ligtasApplications/" + appId,
                withStoredAudit(appId, resubmitted, tokenA), tokenA).ok());
        assertTrue("Another resident cannot", db.set("ligtasApplications/" + appId, resubmitted, tokenB).denied());
    }

    /** A moderator decision (or change), written with its audit event like the app does. */
    private static EmulatorClient.Response decide(String appId, Map<String, Object> current, String status, String actor,
                                                  String token, String message) throws IOException {
        String auditId = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("ligtasApplications/" + appId, Docs.decided(current, status, actor, message, auditId));
        batch.put("auditEvents/" + auditId, Docs.audit(actor, actor.equals(lguPasig) ? "PASIG" : "MANILA", "APPROVE",
                "ligtasApplication", appId, message));
        return db.commit(batch, token);
    }

    /** The owner's update keeps the stored lastAuditId (they only change status, proof and history). */
    private static Map<String, Object> withStoredAudit(String appId, Map<String, Object> data, String token) throws IOException {
        Map<String, Object> copy = new java.util.HashMap<>(data);
        EmulatorClient.Response stored = db.get("ligtasApplications/" + appId, token);
        if (stored.ok() && stored.body.has("fields") && stored.body.getAsJsonObject("fields").has("lastAuditId")) {
            copy.put("lastAuditId", stored.body.getAsJsonObject("fields").getAsJsonObject("lastAuditId")
                    .get("stringValue").getAsString());
        }
        copy.put("reviewReason", stored.body.getAsJsonObject("fields").getAsJsonObject("reviewReason")
                .get("stringValue").getAsString());
        copy.put("decidedAt", Long.parseLong(stored.body.getAsJsonObject("fields").getAsJsonObject("decidedAt")
                .get("integerValue").getAsString()));
        return copy;
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

        String modAudit = UUID.randomUUID().toString();
        batch.clear();
        batch.put("structureStatus/w124", map("status", "CLOSED", "evidence", "Approach flooded (demo)",
                "updatedAt", System.currentTimeMillis(), "updatedBy", modManila, "jurisdiction", "MANILA", "lastAuditId", modAudit));
        batch.put("auditEvents/" + modAudit, Docs.audit(modManila, "MANILA", "SET_STRUCTURE_STATUS", "structure", "w124", "closed"));
        assertTrue("Bridge status is LGU only since 2026-10-22", db.commit(batch, tokenModManila).denied());
    }
}
