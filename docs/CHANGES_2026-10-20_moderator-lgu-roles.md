# 2026-10-20 — Moderator + LGU roles, overview clean-up, chart report

Spec: `docs/superpowers/specs/2026-10-20-moderator-lgu-roles-design.md`. Not released as an APK yet
(the user asked to test first). Debug build tested on the Pixel_7 emulator.

## What changed

| Area | Change |
|---|---|
| Overview | The "Share a verified safety update…" box is gone; the two quick operations stay. Only the 3 newest advisories show, with **See all advisories**. |
| 30 days | Advisories older than 30 days are hidden everywhere (staff and residents) and stay in the database (`Advisory.isRecent`, `AdvisoryService.recentOnly`). |
| View report | A chart screen (`ReportActivity`): activity per day (14 days), changes by kind (30 days), Ligtas applications by status, center occupancy, latest changes in plain words. Numbers come from `OperationsReport`; the charts are drawn by `ui/common/chart`. |
| Saved centers | **Pick on map** in the center editor; after saving, the center's own page opens with its state (Draft, Needs verification, Waiting for LGU, Sent back, Published) and **Show on map**. The centers list shows a state chip and lists centers that need work first. |
| Roles | `StaffProfile` with `ModeratorProfile` and `LguProfile` (city-locked). Moderator: centers, headcounts, Ligtas decisions, announcement drafts. LGU: publishes centers and announcements, approves staff (new **Finalize** tab). |
| Staff sign-up | Login > **Staff sign-up (Moderator or LGU)** creates a locked request; that city's LGU approves it in Finalize. The first LGU of a city is still made in the Firebase console. |
| Ligtas | New status **Needs documents**. A moderator can change any decision later, always with a message for the resident. The resident sees the message, gets a phone notification, sees it in Notifications, and can **Send new documents**. **Email the resident** opens the moderator's email app pre-filled. |
| Staff map | The six layer pills became one summary chip (it opens Map layers). The card has a centred "–" handle, slides down when the map is moved or zoomed, and back up when a point is picked. |
| Rules | `firebase/firestore.rules` checks role and city on every write. 14 rule tests pass on the local emulator. |

## Follow-ups on 2026-10-21

| Area | Change |
|---|---|
| Staff map | The summary chip is gone (the Layers button is the one place to change layers). A slid-down card shows only its centred "–", resting on the bottom tabs (also the resident route card). A picked point slides the card up, then zooms in and sits on the "–" line. |
| Ligtas | A decided application shows its current status and message at the top with **Change status**; list cards say "Change status →"; status chips + **Save status** replace the three buttons. |
| Sign-in | Staff sign-up finishes an interrupted sign-up; clearer wrong-password message; **Forgot password?**; a stray space around the password is ignored; test builds show a "TEST COPY" banner. |
| Saved updates | **Save update** puts an update above all the others (newest save first) in **Notifications only**, for every account; Home and the staff Overview / See all stay "recent updates", newest first. Saved updates stay in Notifications after the 30 days. The "Saved: it now stays at the top" message shows only the first 3 times per person. |
| Fits every phone | `ScreenFit` measures the real header and bottom-tab heights (no fixed 74/94 dp gaps); map buttons and the offline banner stay below whatever is above them; text-holding fixed heights became minimum heights (quick-action tiles, nav items, chips, steppers, toolbars); long field labels moved their examples into help lines; the ring chart's key goes under the ring when narrow; more space above the "Information management only" note. |
| Keyboard | The box being typed in scrolls above the keyboard (`KeyboardScroll`); keyboard "Next" goes to the next box instead of the password eye icon; "Done" on the password signs in. |
| Release | APK **1.3** (versionCode 4), signed with the same key as 1.2, at `apk/RutaRescue-1.3.apk`. |

## Tests

- 219 unit tests pass (24 new: roles, staff requests, hand-off to the LGU, 30-day window, report numbers, Ligtas notices, saved updates).
- Every staff and resident screen checked on a small phone (720 x 1280) with text at 130%, and again at normal size.
- 14 security-rule tests pass on the Firebase Local Emulator Suite.
- Walked through on the emulator with a separate "RR Local Test" build against the local emulator: staff sign-up, locked sign-in,
  LGU approval, moderator approve → needs documents → email, center with Pick on map → LGU publish, moderator draft → LGU publish,
  resident phone notification → status screen → send new documents → Notifications, chart report, sliding map card.
- Found and fixed on the device: the announcement editor crashed for moderators (a view was used before it was looked up).

## To do in the real Firebase project

1. Paste `firebase/firestore.rules` into Firestore > Rules and Publish. Until then staff screens show "not allowed".
2. Existing `lgu_staff` accounts have no `role` field and keep working as **LGU**.
3. Create moderators with Staff sign-up in the app, then approve them as the city's LGU (Finalize tab).
