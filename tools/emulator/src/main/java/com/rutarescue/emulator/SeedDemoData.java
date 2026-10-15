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
 *
 * In a real Firebase project, create LGU accounts in the console (Authentication > Add user)
 * and then add lgu_staff/{uid} with fields displayName, email, jurisdiction, position.
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

        System.out.println("Demo data ready in the emulators (see SeedDemoData.java for the demo accounts).");
        System.out.println("Next: sign in as the Manila LGU account and import the legacy centers from Data > Upload.");
    }

    private static void check(EmulatorClient.Response response) throws IOException {
        if (!response.ok()) throw new IOException("Seeding failed: " + response);
    }
}
