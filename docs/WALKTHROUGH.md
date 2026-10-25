# RutaRescue — how the app works now (plain-language walkthrough)

Updated 2026-10-22. This is for every team member, technical or not. It follows one flood day
from start to finish, then says what changed in each person's part of the project, and ends with
a short demo script.

---

## 1. Three kinds of accounts

- **Citizen (resident):** anyone who downloads the app and registers. Plans safer routes, keeps a
  family record, and can carry a **priority ID** if they are a senior citizen, a person with
  disability, or pregnant.
- **Moderator:** a city staff member stationed at **one evacuation center**. Keeps that center's
  headcount up to date, checks priority IDs at the door, decides Ligtas Priority applications,
  and sends updates about the center to the LGU.
- **LGU:** the city's disaster office. The only one that **announces**: sets the city's alert
  level, posts road closures, adds and publishes evacuation centers, accepts or declines the
  moderators' updates, and approves staff accounts.

Every account has a **Settings** gear at the top of the screen (account, password reset, phone
notifications, sync, about, sign out).

---

## 2. One flood day, start to finish

**Morning: the LGU gets ready.**
1. The Manila LGU officer opens the app. The first thing on the screen is **Advisories**, led by
   the line "Advisory • Manila".
2. The river is rising, so the officer taps **Change** on that line, picks **Alert level 2**, and
   writes a short note: "Pasig River rising. Prepare to evacuate."
3. Every Manila account sees "ALERT LEVEL 2 • MANILA" at the top of its first screen at once.
4. The officer adds a new evacuation center under **Data → Manage → Add a center**, types the
   name, address and pin, names who checked it ("Barangay field check, Oct 22"), and taps
   **Verify and publish**. Residents can see it right away.
5. In **Finalize**, the officer approves Ana's moderator sign-up and picks the center Ana will
   run (Almario Elementary School).

**Midday: the moderator runs the center.**
6. Ana signs in. The Overview shows the advisories first, then **My center** with a bar like
   "215 of 300 people · 72%" and a **Manage center** button.
7. As families arrive, Ana taps **+5** or types the exact count and taps **Save**. Only Ana's
   own center can be changed; other centers open read-only.
8. The **Centers** tab (where the map used to be) lists every Manila center with how full it
   is, fullest first, so Ana knows where to send people when Almario fills up.
9. Ana writes a **center update**: "Water and blankets available at the gate." It is sent to the
   LGU and marked **Pending approval**. Residents cannot see it yet.

**Afternoon: the LGU decides.**
10. In **Finalize → Advisories to approve**, the officer sees Ana's update with two buttons.
    **Accept** publishes it to residents at once, labelled with the city's alert level.
11. Another update is vague, so the officer taps **Decline** and writes "Please say how many
    people it can still take." That update is now **Declined** for good: it can never be
    published. Ana sees the comment and taps **Write a new advisory**, which starts from the old
    text, so only the missing detail has to be added.

**A resident with a priority ID.**
12. Liza registers, picks **Pregnant** under "Priority group", and finishes sign-up. Liza's
    **priority ID** opens straight away: a card with the name, the group, a code like
    XXX4-Z8HZ-YNBD and a QR code. Liza taps **Save to phone**; it goes to the phone's Gallery
    (Pictures › RutaRescue). The screen lists what to bring: a valid ID, a prenatal record, proof
    of address, an emergency contact, and the months of pregnancy.
13. At Almario, Ana taps **Check a priority ID**, scans the QR (or types the code), and sees
    Liza's record and Ligtas application. Ana marks each item **OK** or **Missing**, adds
    a note ("Please bring your prenatal record tomorrow."), and taps **Save check**.
14. Liza's ID screen now says "1 item missing" with the note.

**Evening: a road closes.**
15. The officer reports a flooded bridge as **closed** on the Map tab.
16. A resident comparing routes sees "Routes updated: the LGU posted or lifted a road closure."
    Routes through the closed bridge drop to the bottom, or the app looks for detours. Someone
    already following a route gets a new one from where they are.

---

## 3. What changed in each person's part

**Prince Aldrei Dimalanta — routes and traffic**
- `SafeRouteFragment` now listens to the LGU's closures. When the set changes it plans the
  routes again (or, during guidance, takes the best new route at once). See
  `onClosures` and `rerouteAroundClosure`.
- Only the LGU can post closures and bridge or tunnel status now (`DatasetService`, rules).
- Worth a demo with internet on: post a closure on a road that the top route uses.

**Amandha Nadine P. Ganado — user features, testing, documentation**
- New screens to know: Settings, My priority ID, the moderator Overview and Centers tab, the
  priority ID check, and the Finalize approval queue.
- Tests: 237 unit tests (`./gradlew :app:testDebugUnitTest`) and 20 database-rule tests
  (`./gradlew :tools:emulator:test` with the local Firebase test server running).
- Documents: this walkthrough, `docs/ARCHITECTURE_roles_oop_mvc.md` (requirements, use cases,
  diagrams, OOP, MVC) and `docs/CHANGES_2026-10-22_role-workflows.md` (change log).

**John Elric B. Mansujeto — lead and integration**
- New services are wired in `AppContainer`: `alerts()` (city alert level) and `priorityIds()`.
- `firebase/firestore.rules` changed a lot. **The new rules must be pasted into the Firebase
  console and published** before the real app can use these features.
- After publishing: the Manila LGU adds its centers, then approves moderators (with their
  center). Moderators approved before today need a center under Finalize → Moderators.

**Rhain Ann Rivera — flood and evacuation data**
- Centers are now created only by the LGU, with a named verification source; the import
  (Upload) also publishes directly.
- Each moderator updates only their own center's headcount; the database refuses other fields
  and other centers.
- The city alert level is one record per city (`cityAlerts/MANILA`), separate from the NOAH
  flood maps.

**Rhian Kim Santos — UI and UX**
- Advisories are the first block on all three dashboards, led by the alert-level strip
  (`view_alert_strip.xml`).
- The header has a Settings gear beside the bell; the moderator bottom bar has three tabs.
- All new screens were checked on a small phone with large text; long descriptions wrap instead
  of being cut off.

---

## 4. Demo script (local test server, about 10 minutes)

The demo accounts below exist **only** on the local test server on the team laptop (see
`tools/emulator/SeedDemoData.java`), never on the real Firebase.

1. Start the test server, seed it (`./gradlew :tools:emulator:seedEmulator`), and open the
   "RR Local Test" copy of the app.
2. Sign in as the **moderator** (moderator.demo@rutarescue.test): show the advisories on top,
   My center, the Centers tab, Manage center (+5, Save), and Check a priority ID with the code
   **DEMQ-2SEN-R3PY**.
3. Write a center update and send it to the LGU.
4. Sign in as the **LGU** (lgu.manila@rutarescue.test): change the alert level, open Finalize,
   Accept one update and Decline the other with a comment, and add a new center.
5. Register a **new resident** with a priority group: show the ID, Save to phone, then Home with
   the accepted update and the alert level at the top.
6. Sign back in as the moderator: open the declined update and tap Write a new advisory.
