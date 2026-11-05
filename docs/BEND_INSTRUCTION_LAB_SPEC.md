# Bend Instruction Mode — experimental developer/UI package

Status: implemented as an **opt-in** web experiment at \`/bend-instruction-lab.html\`. The existing \`/bend-calcs.html\`, native bender tools and stored measurements are intentionally retained. This is a trial for field usability, not a replacement for checked, tool-specific manufacturing instructions.

## User experience contract

The user can open the normal bending screen or select **TRY · 3D Bend Instruction Lab**. The experiment presents five areas in order:

1. **Choose the bend** — select a single or multiple bend preset, measurement units, known bender reference, and field dimensions.
2. **MARK HERE — straight conduit** — show the left-hand tape datum and only supported mark positions. Each mark card includes A/B/C, actual numerical value, and applicable shoe reference.
3. **Put the bender on the mark** — one numbered card at a time with four prominent fields: **MARK / DISTANCE**, **ARROW / STAR / NOTCH**, **HOOK DIRECTION**, **FLIP / PIPE ROTATION**.
4. **Finished conduit — rotate it in 3D** — drag-to-orbit, mouse wheel or buttons for zoom, top/front/side/isometric views, complete conduit or through-selected-bend.
5. **Adjustable multi-bend preview** — up to 12 operations with straight length before each bend, signed bend angle, and local plane rotation.

Common action labels: **Use saved measured deduct**, **Update marks + 3D**, **Previous**, **Next**, **Show finished conduit**, **Through selected bend**, **Restore sample geometry**, **Copy instruction plan**, **Return to existing calculations**.

Keep this distinction extremely obvious: **mathematical arc-entry points are not actual ARROW, STAR or saddle-centre bender-head marks**.

## Core field scripts

### Single stub-up 90°
- Measured datum: stub end.
- Physical A = desired stub height − confirmed shoe deduct (never assume a universal deduct).
- Tool mark: ARROW on A when using documented Klein-style method.
- Hook: faces short free/stub end for that method.
- Tool handling: one bend; do not roll the conduit accidentally.
- Verification: resting 90°, height, springback, no flattening.

### Two-bend offset
- Measured datum: chosen tape zero; user selects near- or far-critical convention.
- True rise H, angle θ: theoretical spacing S = H/sinθ and shrink T = H tan(θ/2). Manufacturer field constants can differ.
- Near-critical A = D + T; B = A + S. Far-critical B = D + T; A = B − S.
- Klein-style arrow at A/B; first hook faces away from other mark.
- Second opposite bend: show **FLIP ASSEMBLY / MAINTAIN SAME BEND PLANE**, with explanation that the documented floor-to-air method also rotates the tube in the cradle. Do not mistake this for an additional out-of-plane roll.
- Verification: parallel end sections, intended rise, correct critical datum, no dogleg.

### Rolling offset
- True rise H = sqrt(vertical² + horizontal²), plane angle = atan2(horizontal, vertical).
- Rotate to this plane **before first bend**; maintain same tilted plane for return bend.
- Use offset spacing derived from H and θ. Do not direct the user to randomly rotate an additional 180° before bend 2.
- Verification: both vertical and lateral offsets and final parallel run.

### Back-to-back 90s
- First 90 uses ARROW at measured (stub height − deduct).
- **Second STAR mark is measured from the back of the completed first 90, not calculated as a straight-stock origin from a guessed radius.**
- On documented Klein method, hook faces the free end opposite the original bend.
- Same plane for both completed 90s. For close/tight U layouts, follow model-specific alternate manufacturer procedures.

### Three-point saddle
- Centre bend first; for verified Klein-style 45° centre example, side bends 22.5°.
- Manufacturer chart for 45° centre: each inch of rise → 2.5 inches to outer marks, 3/16 inch total shrink. For 60° centre: 2 inches spacing and 1/4 inch shrink per inch rise.
- Centre C = distance to obstacle centre + total shrink; outer marks A/C/B.
- Tool references: manufacturer-designated centre rim notch for centre, outer ARROW for returns.
- Maintain one saddle plane and clearance. Other tools/angles must be verified independently.

### Kicked 90 + additional 90
- Purpose: show a rotatable **three-bend** finished shape.
- First physical ARROW mark may be calculated from calibrated stub deduct.
- Kick and additional 90 physical mark positions **must not be guessed from the 3D preview**. They require a defined field datum, finished dimensions, shoe geometry and verified bend method.
- Explicit labels: **Manual kick mark needed**, **Further 90 requires separate field layout**, **Verify shoe clearance**, **Choose plane before bending**.
- Do not claim that a rotation/facing sequence is universal.

### Four-point saddle, segmented 90 and custom bends
- Provide the conceptual 3D shape and numbered step context.
- Mark cards must say **GEOMETRY ONLY — NOT VERIFIED SHOE MARKS** unless a manufacturer-specific method has actually been validated.
- No guaranteed cut list or shoe-access plan from these previews.

## 3D geometry model

- A straight leg advances in the current tangent direction.
- Each bend is sampled as a circular centreline arc with user-entered ideal radius R.
- Local bend direction is derived from the bend-plane rotation relative to the tangent and transported normal.
- Arc equations: Δp = R(sinθ ⋅ tangent + (1−cosθ) ⋅ bendDirection). Update tangent and normal at end of each arc.
- Drag projects X/Y/Z through yaw and pitch onto a 2D canvas; pan/orbit is interactive, not a downloaded static image.
- The 3D viewer shows bend numbers and a smoothed centerline tube. No claim to solid wall thickness, calibrated elongation, exact interference, manufactured radius, cut length or physical tool placement.
- Step-by-step "through selected bend" truncates the preview to the selected finished arc.
- Sample combination contains three bends (90°, kick, 90°) so the user can rotate the result before editing it.

## Safety and accuracy requirements

- Never use "always flip", "always rotate 180°", "always use star" without identifying a suitable method and model.
- Suppress manufacturer-specific tool cues when **Other / unverified shoe** is selected.
- Keep existing calculators as the authoritative known-working tool; experiment must be reversible by simply leaving the screen.
- Reject negative dimensions, missing necessary deduct, marks before datum, impossible angle data and invalid radius.
- Unit conversion must not silently reinterpret millimetres as inches.
- Do not assume that "head orientation" equals "bend direction"; physical hook orientation depends on the actual bender and work position.
- Show source attribution to the Klein Tools **Conduit Bender Guide**, while not reproducing illustrations or full copyright-protected text.
- Visually and textually label prototype limitations; require field check of bending radius, cumulative angle, manufacturer shoe markings, conductor pull constraints, springback, bender head clearance and actual finished dimensions.

## Acceptance tests

- Existing \`/bend-calcs.html\` continues working and contains only an additive experiment link.
- Experimental page loads from web dashboard, Android Workbench and Windows cloud-view menu.
- Valid measured stub: 500 mm − 127 mm = 373 mm first arrow tape mark.
- Example two-bend offset 150 mm at 30°: theoretical mark spacing = 300 mm; geometric shrink ≈ 40.2 mm. Test near/far reference branches.
- Example 45° three-point saddle: 2 in rise and 20 in centre → centre = 20 3/8 in, returns at 15 3/8 and 25 3/8 in.
- 90° + kick + another 90: 3 clearly distinguished 3D curves; orbit, zoom, top/front/side/isometric, partial vs finished selection.
- Other/unverified manufacturer mode does not issue definitive hook/arrow alignment instructions.
- Invalid or missing deduct results in an explicit warning, not a fabricated physical mark.
- Browser script parses, all referenced controls exist, no external JavaScript/CDN required, no API/server-side code changes.
- Static offline cache can include the new page and script without caching personal account/API requests.

## Deferred enhancements (not implemented in this trial)

Solid tube mesh / actual 3D CAD export, verified body/obstruction collisions, bender-shoe access sweeps, manufacturer-specific radius and arrow mapping libraries, saving custom sequences to cloud account, native offline 3D view parity, and validated fabrication cut lists.
