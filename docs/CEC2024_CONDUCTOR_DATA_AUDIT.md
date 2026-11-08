# Electrical Workbench — CEC 2024 conductor data audit

**Audit state: AUTHORIZED_SOURCE_NOT_AVAILABLE (8 November 2026)**

## Two separate verification questions

**Regulatory adoption (confirmed):** Ontario Regulation 164/99, as amended
by O. Reg. 449/24, adopted CSA C22.1:24 with the Ontario amendments
referenced in the regulation, effective **May 1, 2025**. Official sources:
[Ontario e-Laws](https://www.ontario.ca/laws/regulation/990164) and
[Electrical Safety Authority](https://esasafe.com/role/oesc/).

**Numerical source tables (NOT verified):** confirming which Code edition is
legally adopted does not verify any embedded ampacity, correction factor,
breaker rating, or occupancy demand factor. The public metadata endpoint
`GET /api/calculators/references` reports `OESC_ON_2024` and
`AUTHORIZED_TABLE_VERIFICATION_REQUIRED`, with per-table review status.
The client-facing reference-status page is `/cec-reference-status.html`.
The endpoint intentionally **contains no licensed table rows**.

### Release gate before marking any numerical dataset verified

- A qualified reviewer compares each row required by the calculator with an
  authorized current CSA C22.1:24/OESC edition and applicable amendments.
- Each review entry identifies the rule/table, conductor material, installation
  method, temperature column, temperature/grouping assumptions and exceptions.
- Store reviewer, verification date, data revision and permitted redistribution
  rights independently from the application deployment version.
- Verify numeric cases (including rejected invalid cases) across backend,
  Windows, Android and iOS before changing a dataset status.
- Do **not** use a successful code build, cross-platform parity, or the confirmed
  legal adoption date as grounds to mark numeric tables verified.

The reference registry `ElectricalCodeReferences` is **metadata only**. Its
provisional status must not be changed in order to bypass the licensed-source
review process.

This repository does **not** contain a confirmed licensed source-of-truth copy of
CSA C22.1:24 / Ontario Electrical Safety Code for all required conductor tables.
User-owned practice worksheets and public technical explanations are not a
substitute for the official, amendment-applicable reference. Do not mark embedded
ampacity/correction presets as **CEC-verified** or **approved for installation**.

## What is checked automatically

| Scope | Status | Automated evidence |
|---|---|---|
| Web vs Windows copper raceway 75°C example ampacities | Cross-platform parity checked, **not licensed-source verified** | `scripts/validate-conductor-data-audit.cjs` compares all matching size keys and values |
| Windows ambient correction factors (Table 5A-style, 30–80°C sample rows) | Regression checked; **primary-table check pending** | `ConductorCorrectionFactorsTest` and a single helper shared by the two Windows worksheets |
| Windows current-carrying conductor group correction (Table 5C-style) | Boundary regression checked; **primary-table check pending** | JUnit tests for 3/4, 6/7, 24/25, 42/43 conductor boundaries |
| 60°C and 75°C termination column under 90°C insulation | Arithmetic checked; not code approved | JUnit and Swift/Kotlin native tests; GUI uses equipment termination-temperature basis |
| Android transformer currents and native arithmetic guards | Unit tested | `:app:testDebugUnitTest` runs during Android build |
| iOS transformer currents and conductor correction review | Native Swift self-tests | Compiled/run with `swiftc` before iOS simulator build |
| Copper free-air, aluminum, underground, Table D-series, Table 9 conduit areas and 2024 provincial amendments | **Not independently verified** | Requires authorized source and configuration-specific audit |

## Reference worksheet scenarios — arithmetic, not table certification

- Three-phase 75 kVA at 600 V: `75,000 / (sqrt(3) × 600) ≈ 72.17 A`.
- 90°C-insulated conductor **on 60°C terminals**: an entered 60°C-column 55 A ampacity,
  with independently supplied 0.58 ambient and 0.80 grouping factors,
  gives `55 × 0.58 × 0.80 = 25.52 A`.
- At 75°C termination with the same illustrative base of 55 A,
  0.75 ambient and 0.80 grouping gives `33.00 A`.
- The tested Table 5C-style grouping boundaries are 1–3, 4–6, 7–24,
  25–42 and 43+; the circumstances requiring that table must be established separately.

Illustrative numbers are not automatically usable at a site. The ampacity table
chosen, insulation, actual equipment markings, 2024 Ontario requirements,
wiring method, neutrals, raceway occupancy, protection and voltage drop still govern.

## External supporting guidance (not a licensed replacement)

- IAEI Magazine: [Application of Rule 4-006](https://iaeimagazine.org/columns/canadian/application-of-rule-4-006-of-the-canadian-electrical-code/)
- IAEI Magazine: [Ampere rating of a circuit, feeder, or service](https://iaeimagazine.org/columns/certification-insights/ampere-rating-of-a-circuit-feeder-or-service-what-is-it-based-on/)
- Saskatchewan Technical Safety Authority: [CEC interpretation guidance](https://api.tsask.ca/wp-content/uploads/2025/02/2021_CEC_Saskatchewan_Interpretations.pdf) — **2021 Saskatchewan interpretation**; explanatory only, not the 2024 Ontario rule text.

## What would complete a licensed verification

1. Obtain authorized CSA C22.1:24 and the effective Ontario amendments.
2. Check every required ampacity data point against the appropriate current table
   for material, insulation, free air vs cable/raceway, and conductor size.
3. Independently confirm every Table 5A/5C correction value and the applicability
   rules and exceptions; flag out-of-range inputs and note unsuitable conditions.
4. Confirm whether other permitted provisions or special installation methods
   alter the termination-temperature/ampacity calculation.
5. Record the edition, table/rule, verified data revision, reviewer and date.
6. Run all platform test suites and then a physical-device or simulator UI smoke test.

**Do not import and redistribute CSA table text in a public repository without
appropriate distribution rights.** Tests should use only necessary individual
worked-example numeric facts and references.

This audit intentionally does not assert that the app's pre-existing ampacity
tables are accurate or complete just because cross-platform copies match.
