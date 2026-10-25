# 2026-10-22 — Role workflows: LGU approves, moderators run one center, priority ID

Request: the 2026-10-22 list (General, LGU, Moderator, Citizen, Documentation). Builds on
`2026-10-20-moderator-lgu-roles-design.md`. Where this list reverses an earlier decision, the new
list wins (noted below as "changed").

## Decisions made while building (can be changed later)

| # | Topic | Decision | Why |
|---|---|---|---|
| D1 | Settings | One **Settings** screen for every account, opened from a gear in the header of every tab: account, change password (reset e-mail), priority ID (residents), phone notifications, sync now, about, sign out. | One place for account things; the header is on every tab. |
| D2 | Alert level | A **city alert level** (`cityAlerts/{CITY}`) that only the city's LGU sets (Overview → alert strip → Change). Every dashboard of that city shows it at the top. The advisory editor has no level picker any more; a published advisory carries the city's level at the moment it was published. | "Only the LGU sets the alert level for its area." A per-advisory level let an approved center update lower the city's level by accident. |
| D3 | Moderator advisories | A moderator's advisory is about **their own center** and goes **Pending → Approved (published)** or **Declined** (with the LGU's comment). A pending one can still be fixed. A declined one is locked for good (rules too); "Write a new advisory" starts a new one with the old text. | Matches the request; "a declined advisory can't be published as is". |
| D4 | LGU queue | The **Finalize** tab lists pending advisories with **Accept** and **Decline** buttons on each card. Decline asks for a comment (5–300 characters) that the moderator reads. | The tab already holds the LGU's approvals. |
| D5 | Closures (changed) | Road closures and bridge/tunnel status are **LGU only** (app and rules). Safe Route re-plans by itself when the LGU posts or lifts a closure while routes are on screen or during guidance. | "Only the LGU can post road closures"; "routes update automatically". |
| D6 | Centers (changed) | Only the **LGU** adds, imports, edits, publishes and archives centers. Each moderator is **assigned to one center** by the LGU (when approving the sign-up, or later under Finalize → Moderators). A moderator updates the headcount, status and facilities of **their own center only** (rules too). | "Only the LGU creates centers"; "advisories about their own center". |
| D7 | Moderator tabs (changed) | **Overview** (advisories on top, then *My center* = Manage Center), **Centers** (the city's centers with capacity, replaces the map), **Reviews** (Ligtas + priority ID scan). The moderator no longer has the Map or Data tab. | Requested; the Data tab only held center creation. |
| D8 | Priority ID | Registration has an optional **Priority group** (Senior citizen, Person with disability, Pregnant). Choosing one issues a priority ID (`priorityIds/{random id}`) and opens the ID screen: card image with QR, **Save to phone** (Pictures/RutaRescue), also under My Record and Settings. The QR holds only `RUTARESCUE:PRIORITY:<id>`. | Requested; the opaque id keeps personal data out of the QR. |
| D9 | QR check | Any approved moderator scans the ID in Reviews (or types the code). They see the holder's name, group, city, and the Ligtas application of their own city, then a checklist (common items + the group's documents), mark each **OK** or **Missing**, add a note, and save (with an audit event). The resident sees the result on the ID screen. | Evacuees may reach a center in another city; only what the ID needs is readable. |
| D10 | Documentation | `docs/CHANGES_2026-10-22_role-workflows.md` (change log + traceability to the DOCX), `docs/ARCHITECTURE_roles_oop_mvc.md` (requirements, use cases, diagrams, OOP, MVC), `docs/WALKTHROUGH.md` (plain language, with a part for each team member), README update. | Requested. |

Not in scope: version bump, APK rebuild, Git commit/push (only when asked).

## Data (Firestore)

| Path | New / changed fields | Who writes |
|---|---|---|
| `advisories/{id}` | `review` (`""`, `PENDING`, `APPROVED`, `DECLINED`), `centerId`, `centerName`, `requestedByUid` | LGU any (never publishes a declined one); moderator creates `PENDING` for own center with level `ADVISORY`, edits only while pending |
| `cityAlerts/{CITY}` | `city`, `level`, `note`, `setByName`, `setAt`, `lastAuditId` | LGU of the city |
| `lgu_staff/{uid}` | `centerId`, `centerName`, `assignedBy`, `assignedAt` | LGU (approval, or later assignment of its moderators) |
| `centers/{id}` | — | LGU creates / edits / publishes; moderator of that center changes headcount, status and facilities only |
| `closures`, `structureStatus` | — | LGU only |
| `priorityIds/{id}` | `ownerUid`, `holderName`, `group`, `city`, `issuedAt`, `checks` (map), `missingCount`, `flagNote`, `checkedBy`, `checkedByName`, `checkedAt`, `checkedAtCenter` | Owner creates (unchecked); any moderator reads one by id (no listing) and records a check |

## Model (OOP)

- `StaffProfile` permissions (polymorphism): `canCreateCenters`, `canManageCenter(center)`, `canDecideLigtas`,
  `canPublishToResidents`, `canApproveStaff`, `canSetAlertLevel`, `canPostRoadNotices`, `canSubmitAdvisories`,
  `canVerifyPriorityIds`. `ModeratorProfile` adds the assigned center.
- `Advisory`: `AdvisoryReview` state, `submitForApproval`, `approve`, `decline`, `startResubmission`.
- `CityAlert` (new, `AuditableEntity`), `PriorityGroup` (enum with its documents), `PriorityCard` (new,
  `AuditableEntity`), `ChecklistItem`, `CheckResult`.
