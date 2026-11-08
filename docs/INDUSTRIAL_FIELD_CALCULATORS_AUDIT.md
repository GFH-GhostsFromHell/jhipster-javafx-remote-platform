# Industrial field calculators — arithmetic verification and limitations

**Release classification:** offline field worksheet, **NOT CODE VERIFIED** and **not** a protection or electrical installation approval.

These are original, table-free mathematical worksheets available on Android, iOS,
Windows and the browser. They require field-entered nameplate/measurement values.

| Worksheet | Implemented arithmetic | Independent sample check | Limitations |
| --- | --- | --- | --- |
| CT current ratio | Secondary A = primary A × CT rated secondary / rated primary | 400:5 CT, 200 A primary → 2.5 A secondary | Saturation, burden, polarity, accuracy class, relay connection and metering safety not modeled |
| VT / PT voltage | Secondary V = measured primary × rated secondary / rated primary | 4160:120 VT, 4000 V primary → about 115.38 V | Fuses, grounding, burden, connection and instrument loading not modeled |
| Balanced three-phase power | kVA = √3 × VLL × IL / 1000; kW = kVA × PF | 600 V, 100 A, PF .85 → 103.92 kVA and 88.33 kW | Balanced sinusoidal current only; not harmonic/unbalance calculations |
| CT pickup translation | Secondary pickup = previously specified primary pickup × secondary / primary CT rating | 400:5 CT, preapproved 240 A primary → 3.0 A secondary | **Not** a relay protection setting, time delay, curve selection, fault study or coordination approval |
| Ideal transformer short circuit | FLA = kVA × 1000 /(√3 × VLL); ideal symmetrical secondary current = FLA × 100 / impedance% | 75 kVA, 600 V, 5% → 72.17 A FLA; ideal 1,443 A | Infinite source assumption; source impedance, motor contribution, upstream Z, X/R and arc flash excluded. **Not actual available fault current** |
| Linear 4–20 mA scaling | I = 4 mA + 16 mA × (engineering value - minimum)/(maximum - minimum) | 0–1800 rpm; 1200 rpm → 14.67 mA | Verify sensor/drive range, fail-safe and calibration. Do not use loaded motor speed instead of configured analog span |

All six worksheets reject nonfinite inputs, impossible ratings and out-of-range
power factors/impedance/analog values. No numerical CEC/OESC tables are
embedded or asserted verified. Current verified regulatory edition reference:
Ontario's CSA C22.1:24 adoption effective May 1, 2025, with Ontario amendments.

## Source-verification and regulatory sign-off still required

The licensed **CEC2024_TABLE_VERIFICATION_REGISTER.json** remains unchanged:
Tables 1–4, 5A, 5C, 13, and 29 all have
`PENDING_AUTHORIZED_COPY` with zero accepted numerical rows.

Before any **automatic** conductor/fuse selection can be labelled reliable:
1. Compare each used licensed table entry with the applicable current official Code and Ontario amendments.
2. Record verifier, date, exact conditions, licensing/redistribution rights and revision.
3. Review 60/75/90°C equipment termination limits, Table 5A/5C applications,
   motor/transformer exceptions, OCPD ratings and service demand rules.
4. Add failure/boundary cases and retest them across Android, iOS, Windows, and web.
5. Require field-device and professional sign-off before presenting equipment settings as approvals.

## Field usability

Circuit + Conductor Review has quick voltage/phase choices for 120 V 1φ,
208 V 3φ, 347 V 1φ and 600 V 3φ on all four platforms. These switches
**change only voltage and phases**. They intentionally do not choose an ampacity
table, AWG wire size, derating factor, protection type or circuit classification.
Inputs remain visible, outputs must be recalculated and old revisions are
preserved. The mobile input screens provide large tap targets and scrollable
controls to avoid cramped button rows.

GitHub Actions now runs independent JS, Kotlin, Swift and Java arithmetic
tests plus navigation and documentation checks. Production Android/iOS device
testing, relay/OEM review and authorized Code table verification are still required.
