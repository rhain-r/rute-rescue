# 2026-10-22 — Role workflows: LGU approves, moderators run one center, priority ID

Design and decisions: `docs/superpowers/specs/2026-10-22-role-workflows-design.md`.
Architecture, requirements, use cases and diagrams: `docs/ARCHITECTURE_roles_oop_mvc.md`.
Plain-language walkthrough for the team: `docs/WALKTHROUGH.md`.
Released as **APK 1.4** (see the follow-ups below). Nothing committed or pushed.

## What changed, by role

### Everyone

| Change | Where |
|---|---|
| **Settings** screen from a gear in every tab's header: account, change password (reset e-mail), priority ID (residents), phone notifications, Sync now, About, Sign out. | `ui/settings/SettingsActivity`, `view_app_header.xml` |
| **Advisories at the very top** of the resident Home, the moderator Overview and the LGU Overview, led by the city's **alert level**. | `view_alert_strip.xml`, `ui/common/AlertStrip`, the three dashboards |

### LGU

| Change | Where |
|---|---|
| **City alert level**: only the LGU sets it ("Change" on the alert strip, with an optional note). Advisories carry the level in force when they are published; the editor has no level picker any more. | `model/advisory/CityAlert`, `CityAlertService`, `cityAlerts/{CITY}` |
| **Approval queue** in Finalize: each moderator advisory has **Accept** (published at once) and **Decline** (comment for the moderator). | `ui/lgu/finalize/FinalizeFragment`, `item_advisory_review.xml`, `AdvisoryService.approve/decline` |
| **Only the LGU** posts road closures and bridge/tunnel status (app and rules). | `DatasetService.mayPostRoadNotices`, `firestore.rules` |
| **Only the LGU** adds, imports, edits, verifies, publishes and archives centers ("Verify and publish"). | `CenterEditorActivity`, `CenterService.verifyAndPublish`, `DatasetService.publishCenters` |
| **Moderators and their centers**: a moderator is approved together with the center they will run; "tap to change" reassigns one. | `StaffRequest.approve(lgu, center…)`, `StaffService.assignCenter`, `lgu_staff/{uid}.centerId` |

### Moderator

| Change | Where |
|---|---|
| Three tabs: **Overview**, **Centers**, **Reviews** (no map, no Data tab). | `MainShellActivity`, `BottomNav` |
| Overview: advisories on top, then **My center** (Manage Center) with the headcount and "Manage center"; quick operations "Check a priority ID" and "Review Ligtas applications". | `ui/moderator/ModeratorOverviewFragment` |
| **Centers** tab instead of the map: the city's centers with their capacity, fullest first, "Your center" marked. | `ui/moderator/ModeratorCentersFragment`, `item_center_load.xml` |
| Can no longer add centers; edits only the headcount, status and facilities of **their own** center (rules too). | `ModeratorProfile.canManageCenter`, `CenterRepository.saveCapacity` |
| Advisories about their center: **Pending → Approved / Declined**. Pending ones can be fixed or withdrawn; a declined one is locked and shows the LGU's comment with **Write a new advisory**. | `Advisory.forApproval / editPending / startResubmission`, `AdvisoryEditorActivity` |
| **Priority ID check**: scan the resident's QR (or type its code) → record, linked Ligtas application, checklist (OK / Missing), note → Save check. | `ui/moderator/PriorityCheckActivity`, `QrCheck`, `PriorityIdService.recordCheck` |

### Citizen (resident)

| Change | Where |
|---|---|
| Registration has an optional **Priority group** (Senior citizen, Person with disability, Pregnant). Choosing one issues a **priority ID** and opens it straight after sign-up. | `RegisterActivity`, `activity_register.xml`, `PriorityIdService.issue` |
| **My priority ID**: card picture with QR (code only, no name), **Save to phone** (Pictures/RutaRescue), what to bring, and the result of the last check at a center. Also from My Record and Settings. | `ui/resident/priority/PriorityIdActivity`, `PriorityIdCardRenderer` |
| Safe Route **re-plans by itself** when the LGU posts or lifts a closure (routes on screen, or the guided route). | `SafeRouteFragment.onClosures / rerouteAroundClosure` |

## Relation to the project documentation (DOCX "RutaRescue Product and Technical Design")

The DOCX now has **section 16 "Role workflow update (2026-10-22)"** (roles, requirements, use cases,
acceptance journeys, OOP, MVC, diagrams summary, privacy) and a "16." line in its contents. Earlier
sections were left as they were; section 16 says which of them it updates:

| DOCX section | What this change adds or replaces |
|---|---|
| 3 Users and authorization | Three roles now: Resident, **Moderator** (one center), **LGU** (announces, approves). Requirements FR-G1…FR-C6 in the architecture doc. |
| 4 Resident experience — Home | Advisories and the city alert level are the first block of Home (FR-C1). |
| 4 Resident experience — Ligtas Priority | The **priority ID** at registration (FR-C2…C4) sits beside the Ligtas application; the ID screen links to "Apply for Ligtas Priority", and the moderator's check screen links to the application. |
| 5 LGU experience | Split into the LGU (alert level, closures, centers, approval queue, staff and center assignment) and the moderator (center, headcount, priority checks, Ligtas). |
| 6 Runtime architecture | New controllers, services and repositories listed in the MVC map (architecture doc §5). |
| 8 Route assessment | Closures were already avoided when routes are assessed; routes now refresh when the LGU's closures change (FR-L4). |
| 10 OOP Java alignment | `StaffProfile` permissions, `ModeratorProfile` center, `Advisory` review states, `CityAlert`, `PriorityCard` / `PriorityGroup` (architecture doc §4). |
| 12 Privacy and security | QR holds only a random code; moderators open one card by code and cannot list cards; every staff change has an audit event; new rules in `firebase/firestore.rules`. |
| 13 Acceptance journeys | New journeys AJ-14…AJ-19 (architecture doc §3.3). |

## Follow-ups after the user tried it, and release 1.4

| Change | Where |
|---|---|
| LGU: tapping a moderator under Finalize > Moderators and their centers opens a **dashboard** of that moderator and center: a capacity ring (people inside / free places / over capacity), the headcount and its last update, facilities, the city alert level, the NOAH flood hazard at the center's pin for the 5-, 25- and 100-year scenarios, the center's advisories (pending / approved / declined), the moderator's changes per day (14 days), their work by kind (30 days), and Change center / Open the center page / Show on map. | `ui/lgu/finalize/ModeratorDashboardActivity`, `activity_moderator_dashboard.xml`, `HazardQueryService.inspectAll` |
| LGU Overview: the big "Operations overview" title became a short description, e.g. "2 centers open to residents, 1 item waiting for you and no road closures.", with the flood-map version under it. | `fragment_lgu_overview.xml`, `LguOverviewFragment.updateHeroBody` |
| The logo in the header is only a picture now (it used to open About, which had a second Sign out). About and Sign out are in Settings, for every account. | `view_app_header.xml`, the tab fragments, `AboutActivity` |
| Moderator Reviews: the extra QR button next to the tabs is gone; "Check a priority ID" at the top scans both priority IDs and Ligtas QRs. | `fragment_lgu_reviews.xml`, `LguReviewsFragment` |
| Sign-in screen: the link is now just "Staff sign-up". | `strings.xml` |
| **Release 1.4** (versionCode 5) at `apk/RutaRescue-1.4.apk`, signed with the same key as 1.3; the release build has no local-network permission (debug only). | `app/build.gradle.kts` |

The new Firestore rules were published in the real Firebase console by the user on 2026-10-22.

### 2026-10-25 (after 1.4, not in an APK yet)

| Change | Where |
|---|---|
| Resident Home shows only **one** announcement, the newest (of the resident's city when there is one); the alert level stays its own line above it; Notifications keep the rest (30 days). | `HomeFragment.showAdvisoryList` |
| Resident maps (Route and Centers) show the resident's position as a **blue dot** with a soft halo as soon as the phone knows it, before any center or destination is chosen. | `MapController.showUserLocation`, `SafeRouteFragment`, `CentersFragment` |
| Ligtas Priority: "Family member needing priority help" always lists **Myself** first, then the family record's members, and it must be chosen before submitting. A resident without a family record can apply for themself (the account's city is used). | `LigtasApplyActivity`, `LigtasService.submitForSelf` |
| **One-week rule** for every account: a city alert level is shown for 7 days after the LGU set it, then the strip says "No alert level set by the <City> LGU yet" again (and new announcements carry "Advisory"). Home and the LGU Overview show only the newest published announcement of the last 7 days; Notifications keep 30 days. | `CityAlert.isCurrent/currentLevel`, `Advisory.isThisWeek`, `AdvisoryService.latestThisWeek`, `AlertStrip`, `HomeFragment`, `LguOverviewFragment`, `FinalizeFragment`, `AdvisoryEditorActivity` |
| LGU Overview shows **one** announcement (was 3). "See all advisories" (LGU, moderator) and "See all" (resident Home) were removed: the header bell opens Notifications. | `fragment_lgu_overview.xml`, `fragment_moderator_overview.xml`, `fragment_home.xml` |
| The LGU's **Datasets** list moved from the Data tab to **Settings** (LGU only); importing centers stays under Data > Manage. The bridges and tunnels row shows its real version. | `SettingsActivity`, `DatasetRows`, `DatasetService.loadPreparedDatasets`, `LguDataFragment` |
| Data tab: the normal gap between the Data Health card and "Evacuation centers" (the card's shadow ran into the title). | `fragment_lgu_data.xml` |
| LGU Overview tile **"Ligtas Priority: applications pending review"** replaced "Updates" (a count of saved changes with no clear use): the city's applications still waiting for a moderator's decision, 0 when there are none. The change history stays in View report. | `LguOverviewFragment.onLigtas` |
| Info tooltips open above the "i" when there is no room below. | `InfoTooltip.show` |

## Tests

- **237 unit tests pass** (new: `AdvisoryApprovalTest`, `PriorityCardTest`, `CityAlertTest`; updated: `StaffRolesTest`, `StaffRequestTest`, `LguHandOffTest`). After the 2026-10-25 changes: **240** (new: the one-week checks in `CityAlertTest` and `AdvisoryRecencyTest`).
- **20 security-rule tests pass** on the Firebase Local Emulator Suite (new: only the LGU creates/publishes centers, moderator headcount on own center only, pending → approved, declined never published, alert level, closures, priority IDs, center assignment, moderator approval needs a center).
- Walked through on the Pixel_7 emulator with the separate "RR Local Test" copy against the local test server: moderator Overview, priority ID check (typed code, validation, save), center update sent, own-center headcount saved, other center read-only, Settings; LGU alert level change, Accept, Decline, change a moderator's center, add and publish a center; resident registration with a priority group → ID screen → Save to phone (file written to Pictures/RutaRescue) → Home with advisories on top; moderator sees the declined advisory and writes a new one. Checked again at 720 x 1280 with text at 130%.
- Found and fixed on the device: the Settings gear did not respond on the first tab after sign-in; the read-only center page still showed +1/+5/+10; long row descriptions were cut with "…" on small phones.
- **Not tried on the device:** automatic re-routing after a new closure (the emulator has no internet for live routes) and the camera QR scan (typed codes were used). Both are covered by code review only.

## To do in the real Firebase project

1. ~~Paste `firebase/firestore.rules` into Firestore > Rules and Publish~~ - done by the user on 2026-10-22.
2. As the Manila LGU: add the evacuation centers (Data > Manage > Add a center, then Verify and publish).
3. Approve each moderator sign-up in Finalize and choose their center. Moderators approved before today have no center: assign one under Finalize > Moderators.
4. Set the city's alert level from the LGU Overview.
