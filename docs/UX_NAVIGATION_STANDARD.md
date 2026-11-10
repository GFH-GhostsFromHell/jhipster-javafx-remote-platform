# Electrical Workbench — task-first navigation standard

## Why

The product spans web, Android, iOS and JavaFX/Windows. Users should not have to understand
internal names such as **pack**, **suite**, **workbench**, **lab** or **KB** to find an action.

## Preferred customer-facing tool labels

| Task group | Label | Destination or surface |
| --- | --- | --- |
| Everyday electrical calculations | Electrical Calculators | CEC section-based workspace; native offline equivalents |
| Guided arithmetic | Step-by-Step Calculators | `/guided-calculators.html` |
| Advanced CEC worksheets | Advanced CEC Calculators | `/advanced-calculations.html` |
| Short-circuit and per-unit analysis | Power System Analysis | Native engineering app / power systems page |
| Conduit measurements | Conduit Bend Marks | `/bend-calcs.html` and native quick bender |
| Visual bend instructions | Conduit Bend Marks + 3D | `/bend-instruction-lab.html` |
| On-site installation workflows | Jobsite Tools | `/field-suite.html` and native Field area |
| Fault isolation | Equipment Troubleshooting | Native offline guide or `/troubleshooting-kb.html` |
| Cloud records | Saved Work | `/account-records.html` (cloud records; local projects have separate storage) |
| Protection, CT/PT and relay calculations | Protection & Relay Calculators | Native industrial field / relay tooling |
| Exam practice | Red Seal Daily Quiz | Native 25-question study mode |
| Regulations and bulletins | Code Updates | `/news.html` |

Use **Offline**, **Web**, **3D**, or **Guided** only when it distinguishes two genuinely different
tools. Do not casually rename route IDs, persisted record types, or API feature flags.

## Core usability principles

1. **Action first** — “Size a circuit” instead of “Open a workspace”.
2. **Three decisions maximum** to reach a common field calculation.
3. **Short form by default** — show only required dimensions; put optional settings under Advanced.
4. **Result first** — display the answer, marking points, units, assumptions and next action.
5. **Safe by design** — CEC/OESC table verification and field engineering checks remain explicit.
6. **Consistent state** — all versions clearly distinguish local records from cloud-synced records.
7. **Accessible on a phone** — visible navigation labels, 44px or larger touch targets, no overlap.
8. **Common vocabulary** — same task names on all four platforms.

## Follow-up priorities

- Create a shared, versioned navigation catalog so labels cannot drift between web, Android, iOS and desktop.
- Add favourite/recent tools and device-local search history (opt-in, not cross-account by default).
- Give each calculator a uniform **Input → Result → Field instructions → Save/Export** layout.
- Consolidate duplicate conduit bending entry points after parity and bend-mark tests.
- Expand task-level deep links beyond the initial Android Study, Field, Projects and Career shortcuts.
- Test small Android screens, tablet split-screen, large text, VoiceOver/TalkBack and keyboard navigation.
- Verify calculation safety, cloud authorization and persisted projects before any release.
- Add screenshots and task-completion usability checks to each platform's release checklist.


## Implementation on the draft usability branch

- **Web**: `/start.html` is a task-first launcher covering bending, calculations,
  power/protection, troubleshooting, projects, and code updates. The shared
  `tool-catalog.js` supplies task names, destinations, synonyms, and guided next steps.
  `tool-state.js` remembers only favourite/recent task IDs, scoped to the signed-in
  username in browser storage.
- **Web worksheets**: `worksheet-actions.js` adds explicit Input → Calculate →
  Verify → Save/Export instructions to guided, section-based and advanced calculators.
  Users must click **Save input draft**. The stored draft contains the selected
  worksheet's non-sensitive inputs and optional visible output, with account-specific
  local storage. It can be restored, exported as JSON, copied or deleted. Restoring
  values deliberately does **not** rerun a Code check automatically.
- **Android**: `MobileToolFinder.kt` provides searchable native/web destinations,
  category filters, favourites, recent tools, offline badges and direct links to
  Study/Field/Projects/Career. Shortcuts are device-local and account-scoped.
- **iOS**: the native Tools tab has a search field, categories, favourites,
  recent tools, offline/cloud distinctions and the existing entitlement gates.
- **Windows**: the Home page supports full-text task search, a favourite star
  per tile, favourite-only/recent-only filters and a scrollable left navigation.

### Privacy and safety constraints

Do not equate local draft storage or native favourites with cloud sync:
different storage backends and user-initiated export mechanisms remain in place.
Do not store passwords, access tokens, or account records in the shortcut catalog.
The Code compliance and CEC table review status are unchanged. A retrieved
worksheet is **not** independently revalidated until the user recalculates it.

### Release gates still required

CI compilation/validation for all four platforms; browser/mobile manual visual
testing at narrow and large text sizes; functional testing of search, clear, restore,
network failure and sign-out/sign-in account switching; and verification that
existing project sync remains intact. Keep the pull request in draft until
these checks are satisfactory.
