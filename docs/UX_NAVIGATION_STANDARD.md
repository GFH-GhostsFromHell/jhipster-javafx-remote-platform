# Electrical Workbench — task-first navigation standard

## Why

The product spans web, Android, iOS and JavaFX/Windows. Users should not have to understand
internal names such as **pack**, **suite**, **workbench**, **lab** or **KB** to find an action.

## Preferred customer-facing tool labels

| Task group | Label | Destination or surface |
| --- | --- | --- |
| Everyday electrical calculations | Electrical Calculators | CEC section-based workspace; native offline equivalents |
| Guided arithmetic | Step-by-Step Calculators | `/guided-calculators.html` |
| Advanced power engineering | Engineering Calculators | `/advanced-calculations.html` / native engineering app |
| Conduit measurements | Conduit Bend Marks | `/bend-calcs.html` and native quick bender |
| Visual bend instructions | Conduit Bend Marks + 3D | `/bend-instruction-lab.html` |
| On-site installation workflows | Jobsite Tools | `/field-suite.html` and native Field area |
| Fault isolation | Equipment Troubleshooting | Native offline guide or `/troubleshooting-kb.html` |
| Cloud records | Saved Projects | `/account-records.html` and native Projects |
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
- Deep-link home tiles to specific native tabs (not just a generic **More** hub).
- Test small Android screens, tablet split-screen, large text, VoiceOver/TalkBack and keyboard navigation.
- Verify calculation safety, cloud authorization and persisted projects before any release.
- Add screenshots and task-completion usability checks to each platform's release checklist.
