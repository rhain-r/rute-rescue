# Moderator + LGU roles, overview clean-up, chart report — design (approved 2026-10-20)

The user approved this design in chat on 2026-10-20 and asked to build it straight away
(the spec and plan are kept for the record; the user did not review them line by line).

## Decisions taken with the user

| Question | Answer |
|---|---|
| How are staff accounts created? | In-app **staff sign-up** (role + city). The account is locked until that city's LGU approves it; the first LGU of a city is approved in the Firebase console. |
| Who does what? | **Moderator works, LGU finalizes.** |
| How is a resident told about a Ligtas change? | **In-app + email draft** (free, Spark plan). |
| Where does the 30-day advisory limit apply? | **Everywhere** (staff and residents). Old advisories stay in the database. |
| Build now? | Yes; do **not** rebuild the release APK — test on the emulator first. |

## Part A — overview, report, centers (both staff roles)

1. **Overview**: the "Share a verified safety update…" composer card is removed; the two quick
   operations stay. "Your advisories" shows the 3 newest from the last 30 days and a
   **See all advisories** link to a full 30-day list.
2. **30-day window**: `Advisory.isRecent(now)` (published → `publishedAt`, draft → last update) and
   `AdvisoryService.recentOnly()`; every advisory list (resident Home + Notifications, staff lists)
   goes through it.
3. **View report** opens a chart screen drawn by the app (no new library):
   - activity per day, last 14 days (columns),
   - changes by kind, last 30 days (horizontal bars),
   - Ligtas applications by status (donut),
   - each center's occupancy % (bars, green < 70 %, amber 70–90 %, red > 90 %),
   - the 10 latest audit entries as text.
   Numbers come from `OperationsReport` (plain Java, unit-tested). Charts share an abstract
   `ChartView` with `ColumnChartView`, `BarListView` and `DonutChartView`.
4. **Finding a saved center**: the editor gets **Pick on map** (tap to drop the pin). After saving,
   the center's page opens with its state chip and **Show on map** (opens the Map tab on its pin).
   The centers list shows a state chip per row and lists centers that need action first.

## Part B — roles, staff sign-up, Ligtas changes

1. **Roles**: `Role.MODERATOR` added. Abstract `StaffProfile` (city-locked, extends `UserProfile`)
   with subclasses `ModeratorProfile` and `LguProfile`; each answers `canEditCenters()`,
   `canPublish()`, `canDecideLigtas()`, `canApproveStaff()` differently (polymorphism).
   Staff stay in `lgu_staff/{uid}` with a new `role` field (missing = LGU, so the existing Manila
   account keeps working).
2. **Staff sign-up**: `staffRequests/{uid}` {displayName, email, role, jurisdiction, position,
   status PENDING|APPROVED|REJECTED}. A pending account sees "Waiting for approval by the <city>
   LGU". The LGU approves in its Finalize tab (creates `lgu_staff/{uid}` + audit event).
3. **Split of work** (each only for its own city):

   | | Moderator | LGU |
   |---|---|---|
   | Centers | add / edit / pick on map / verify / headcount (live) / archive / import → "Waiting for LGU" | publish to residents or send back with a note |
   | Ligtas | approve / needs documents / reject, change later with a message | sees numbers in the report |
   | Announcements | drafts, "Send to LGU" | publishes its own or moderator drafts, or sends back |
   | Road closures | yes | yes |
   | Staff requests | — | approve / reject |

   Moderator tabs: Overview, Map, Reviews, Centers. LGU tabs: Overview, Map, Finalize, Centers.
4. **Ligtas**: new status `NEEDS_DOCUMENTS`. The moderator may change any decision later; every
   change needs a 5–300 character message to the resident, is added to the history and the audit
   trail. A withdrawn approval makes the QR show "not approved" at once. The resident sees the
   latest message, can attach a new proof and resubmit (back to PENDING), sees status changes at the
   top of Notifications, and gets a phone notification (immediately while the app runs, otherwise
   from the hourly background sync). The moderator gets **Email the resident** (opens the phone's
   email app pre-filled); new applications store the applicant's sign-in email for it.
5. **Rules** (`firebase/firestore.rules`) enforce role + city on every write; the user pastes them
   into the Firebase console after testing.

## Testing

Unit tests (report numbers, 30-day window, roles, Ligtas transitions), security-rule tests on the
Firebase Local Emulator Suite, then a debug build on the Pixel_7 emulator. No release APK.
