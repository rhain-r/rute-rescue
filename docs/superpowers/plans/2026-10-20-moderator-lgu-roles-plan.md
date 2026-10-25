# Build plan — Moderator + LGU roles (spec: ../specs/2026-10-20-moderator-lgu-roles-design.md)

Test first (TDD) for every plain-Java class; UI is checked on the Pixel_7 emulator.

## Part A
- [x] A1 `Advisory.isRecent`, `AdvisoryService.RECENT_DAYS` + `recentOnly`, applied in `listenPublished` / `listenForCity` (test: `AdvisoryRecencyTest`).
- [x] A2 Overview: remove composer card; 3 newest + "See all advisories" → `AdvisoryListActivity`.
- [x] A3 `OperationsReport` (daily counts 14 d, kinds 30 d, Ligtas by status, center occupancy) (test: `OperationsReportTest`).
- [x] A4 `ui/common/chart/ChartView` + `ColumnChartView`, `BarListView`, `DonutChartView`; `ReportActivity` + layout.
- [x] A5 `CenterState` (Draft / Waiting for LGU / Published / Sent back / Archived) + chips and sort in the centers list (test).
- [x] A6 `LocationPickerActivity`; editor "Pick on map"; after save open the center page; "Show on map" → Map tab focus.
- [x] A7 Unit tests + debug build on the emulator, screenshots.

## Part B
- [x] B1 `Role.MODERATOR`, `StaffProfile`, `ModeratorProfile`, `LguProfile` permissions (test).
- [x] B2 `StaffRequest` model, repository, `StaffRegisterActivity`, pending screen, LGU approval.
- [x] B3 Services take `StaffProfile`; moderator/LGU checks; center publication hand-off; advisory drafts → LGU.
- [x] B4 Shell tabs per role; LGU `FinalizeFragment` (centers, announcement drafts, staff requests).
- [x] B5 Ligtas `NEEDS_DOCUMENTS`, change decision, resident resubmit, `applicantEmail`, Email button.
- [x] B6 Resident notices: Notifications list, phone notification (`LigtasNotifier`, SyncWorker).
- [x] B7 `firestore.rules` + `SecurityRulesTest` cases per role; run on the local emulator.
- [x] B8 Device test on the local Firebase emulator (debug build), then real-Firebase build for the user.
- [x] B9 Change log `docs/CHANGES_2026-10-20_moderator-lgu-roles.md`.
