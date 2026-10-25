package com.rutarescue.emulator;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds the LOCAL Firebase emulators with synthetic demo accounts for classroom demos.
 * These accounts exist only in the emulator on your computer - never use these passwords
 * for a real Firebase project.
 *
 *   Resident (Manila):  resident.demo@rutarescue.test   / DemoResident123
 *   LGU staff (Manila): lgu.manila@rutarescue.test      / DemoLguManila123
 *   LGU staff (Pasig):  lgu.pasig@rutarescue.test       / DemoLguPasig123
 *   Moderator (Manila): moderator.demo@rutarescue.test  / DemoModManila123  (runs Almario Elementary School)
 *
 * 2026-10-22 extras: two centers made by the Manila LGU, the Manila alert level, a pending advisory
 * from the moderator, and a senior-citizen priority ID for the resident (code DEMQ-2SEN-R3PY).
 *
 * In a real Firebase project, create the FIRST LGU account of a city in the console (Authentication >
 * Add user, then lgu_staff/{uid} with displayName, email, jurisdiction, position, role "LGU").
 * Everyone else uses Staff sign-up in the app and is approved by that LGU (Finalize tab).
 */
public final class SeedDemoData {

    private SeedDemoData() {
    }

    public static void main(String[] args) throws IOException {
        EmulatorClient db = EmulatorClient.local();
        if (!db.isRunning()) {
            System.err.println("Start the emulators first: cd firebase && firebase emulators:start --project demo-rutarescue");
            System.exit(1);
        }
        db.resetAll();

        String[] resident = db.signUp("resident.demo@rutarescue.test", "DemoResident123");
        String[] manila = db.signUp("lgu.manila@rutarescue.test", "DemoLguManila123");
        String[] pasig = db.signUp("lgu.pasig@rutarescue.test", "DemoLguPasig123");
        String[] moderator = db.signUp("moderator.demo@rutarescue.test", "DemoModManila123");

        check(db.set("users/" + resident[0], Docs.resident("Maria Demo", "resident.demo@rutarescue.test", "MANILA"), resident[1]));
        check(db.set("lgu_staff/" + manila[0], Docs.lguStaff("Manila DRRM Demo", "lgu.manila@rutarescue.test",
                "MANILA", "DRRM Officer"), EmulatorClient.OWNER));
        check(db.set("lgu_staff/" + pasig[0], Docs.lguStaff("Pasig DRRM Demo", "lgu.pasig@rutarescue.test",
                "PASIG", "DRRM Officer"), EmulatorClient.OWNER));

        // A published advisory written the same way the app does (with its audit event).
        String advisoryId = "demo-advisory-1";
        String auditId = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("advisories/" + advisoryId, Docs.advisory("MANILA", "Heavy rain: avoid low-lying streets",
                "Synthetic demo advisory. Prepared flood maps show HIGH hazard near the river; use higher roads toward "
                        + "the covered court.", auditId));
        batch.put("auditEvents/" + auditId, Docs.audit(manila[0], "MANILA", "PUBLISH", "advisory", advisoryId,
                "Seeded demo advisory"));
        check(db.commit(batch, manila[1]));

        // 2026-10-20: the resident's family record and a PENDING Ligtas application for the moderator to
        // decide (with the sign-in email, so "Email the resident" can be tried).
        check(db.set("families/" + resident[0], Docs.family(resident[0], "MANILA"), resident[1]));
        Map<String, Object> application = Docs.application(resident[0], "MANILA");
        application.put("applicantName", "Maria Demo");
        application.put("applicantEmail", "resident.demo@rutarescue.test");
        check(db.set("ligtasApplications/demo-application-1", application, resident[1]));

        // 2026-10-22: centers are made by the LGU (with audit events), and the moderator runs one of them.
        String almario = "demo-center-almario";
        String delpan = "demo-center-delpan";
        Map<String, Object> almarioCenter = Docs.center("MANILA", true, true, "");
        almarioCenter.put("name", "Almario Elementary School");
        almarioCenter.put("capacity", 300);
        almarioCenter.put("occupancy", 210);
        Map<String, Object> delpanCenter = Docs.center("MANILA", true, true, "");
        delpanCenter.put("name", "Delpan Sports Complex");
        delpanCenter.put("capacity", 500);
        delpanCenter.put("occupancy", 120);
        lguWrite(db, "centers/" + almario, almarioCenter, manila, "VERIFY_AND_PUBLISH", "center", almario);
        lguWrite(db, "centers/" + delpan, delpanCenter, manila, "VERIFY_AND_PUBLISH", "center", delpan);
        check(db.set("lgu_staff/" + moderator[0], Docs.moderator("Manila Moderator Demo", "moderator.demo@rutarescue.test",
                "MANILA", manila[0], almario, "Almario Elementary School"), EmulatorClient.OWNER));
        lguWrite(db, "cityAlerts/MANILA", Docs.cityAlert("MANILA", "ALERT_1", ""), manila, "SET_ALERT_LEVEL", "cityAlert", "MANILA");

        // A pending advisory from the moderator for the LGU's Finalize tab.
        String pendingAudit = UUID.randomUUID().toString();
        Map<String, Map<String, Object>> pending = new LinkedHashMap<>();
        pending.put("advisories/demo-advisory-pending", Docs.moderatorAdvisory("MANILA", almario, moderator[0], pendingAudit));
        pending.put("auditEvents/" + pendingAudit, Docs.audit(moderator[0], "MANILA", "SUBMIT", "advisory",
                "demo-advisory-pending", "Seeded pending advisory"));
        check(db.commit(pending, moderator[1]));

        // The resident's priority ID (a moderator can type its code: DEMQ-2SEN-R3PY).
        Map<String, Object> card = Docs.priorityCard(resident[0], "MANILA", "SENIOR_CITIZEN");
        card.put("holderName", "Maria Demo");
        check(db.set("priorityIds/DEMQ2SENR3PY", card, resident[1]));

        System.out.println("Demo data ready in the emulators (see SeedDemoData.java for the demo accounts).");
        System.out.println("More moderators: Staff sign-up in the app, then approve them as the Manila LGU (Finalize tab).");
    }

    /** An LGU write with its audit event in the same batch, like the app does. */
    private static void lguWrite(EmulatorClient db, String path, Map<String, Object> data, String[] lgu, String action,
                                 String entityType, String recordId) throws IOException {
        String auditId = UUID.randomUUID().toString();
        data.put("lastAuditId", auditId);
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put(path, data);
        batch.put("auditEvents/" + auditId, Docs.audit(lgu[0], "MANILA", action, entityType, recordId, "Seeded demo data"));
        check(db.commit(batch, lgu[1]));
    }

    private static void check(EmulatorClient.Response response) throws IOException {
        if (!response.ok()) throw new IOException("Seeding failed: " + response);
    }
}
