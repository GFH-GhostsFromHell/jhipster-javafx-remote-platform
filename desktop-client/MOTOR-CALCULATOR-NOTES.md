# Motor calculator updates

Section 28 now includes:

- Per-motor FLC, motor/starting type, branch OCPD type, duty class, and duty rating for multiple-motor calculations.
- Splitter supply conductor sizing under Rule 28-108.
- Feeder OCPD calculation under Rule 28-204.
- A dedicated splitter-to-motor tap worksheet with 2 m, 5 m, and custom-length paths under Rule 28-110(2).
- Copper and aluminum 75 C raceway/cable conductor lookups for these motor conductor worksheets.
- Multiple refrigeration / hermetic motor-compressor sizing with individual RLA/LRA, failed-start 50%/65% protection, overload type, optional equipment MCA/MOCP overrides, other simultaneous loads, group conductor sizing, and group OCPD results.
- The single refrigeration/nameplate worksheet now uses the same LRA-based protection flow: 50% LRA normally, up to 65% LRA when the normal device will not permit starting, with equipment MCA/MOCP taking priority when marked.

Guided worksheet usability upgrade:

- Guided Worksheets is now the default native JavaFX calculator tab, with the detailed CEC section calculators retained beside it.
- Guided/Advanced input modes keep ordinary exam questions simple while exposing study factors when needed.
- A dedicated Conductor Ampacity worksheet now separates required load ampacity, Table 5A ambient correction, Table 5C grouping correction, terminal-temperature limitations and branch-circuit/OCPD restrictions.
- The conductor worksheet applies the normal small-copper Rule 14-104 study limits (#14 Cu 15 A, #12 Cu 20 A, #10 Cu 30 A) and includes presets for dwelling lighting, 15 A/20 A general-use receptacles, the 30 A dwelling dryer path, plus a motor/HVAC/nameplate special-rule path.
- Combined conductor + voltage drop, D3/K, grounding/bonding, transformer, motor tap, refrigeration, fire-pump, neutral, fire-alarm and service-demand worksheets are included.
- Each worksheet produces numbered steps, a rule/logic path, standard-size rounding guidance and a Why-this-answer explanation.
- Quick presets cover recurring worksheet patterns such as conductor derating, 2 m/5 m motor taps, 75 kVA transformers, failed-start compressors, hospital/apartment services and 24 V EOL circuits.
- Worked solutions can be copied, saved to the local project workspace or converted into practice-question drafts.
- Daily 25 multiple-choice options are rendered as full-width high-contrast JavaFX answer cards so A-D choices remain visible.
