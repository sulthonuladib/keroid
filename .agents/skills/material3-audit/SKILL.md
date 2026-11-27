---
name: material3-audit
description: Audit an Android Jetpack Compose codebase for Material 3 conformance
  and fix what it finds — Material 2 leftovers, hardcoded colors and text sizes,
  bypassed typography and shape tokens, ad-hoc animations, missing interaction
  states, and accessibility gaps. Use when reviewing a screen or PR, checking
  theming quality, preparing an M3 migration, or asking "is this Material 3 done
  right?". Runs a deterministic script, then a manual design pass.
metadata:
  last-updated: '2026-10-28'
  keywords:
  - Jetpack Compose
  - Material 3
  - audit
  - code review
  - design system
  - accessibility
---

# Material 3 audit

Two passes, in this order:

1. **Mechanical** — `scripts/audit.sh` finds token bypasses and API drift.
2. **Design** — a human-judgement pass over decisions a grep cannot see.

The script gives consistent coverage. The manual pass gives judgement. Neither
replaces the other.

## Workflow

### 1. Scope

List the directories in scope and the resolved material3 version. Skip generated
code and build output; the script does that already. Do not audit a half-migrated
theme in isolation — fix the theme first.

Completion: a scoped file set, a recorded material3 version, and a baseline
count.

### 2. Run the script

Resolve `SKILL_DIR` to the directory containing this `SKILL.md`, then scan the
app repository (not the skill directory):

```bash
SKILL_DIR="${SKILL_DIR:-$HOME/.agents/skills/material3-audit}"
bash "$SKILL_DIR/scripts/audit.sh" --root . --theme-dir ui/theme
```

Options:

- `--strict` — treat warnings as failures.
- `--include-tests` — include `src/test` and `src/androidTest`.
- `--theme-dir ""` — apply token rules inside the theme too (rarely useful).
- `--list-rules` — print the catalog.

Output is `path:line:SEVERITY:RULE:message`. The summary goes to stderr.

Completion: every ERROR has a location, and you know the warning count.

### 3. Read the theme yourself

The script cannot judge whether a scheme is complete, whether dynamic color has a
fallback, or whether the motion scheme matches the product. Open the theme files
and check:

- Light and dark define the same roles; no partial scheme mixed with baseline M3.
- Dynamic color is API-guarded and has a brand fallback.
- `MotionScheme.standard()` or `expressive()` is chosen once, not per screen.
- Shape and typography override the scale, not individual call sites.

Completion: each item above has a yes/no with evidence.

### 4. Manual pass

Read `references/checklist.md` and apply every manual check (M01–M14). Sample
the screens rather than every file: the theme, one list screen, one detail
screen, one form, one overlay flow.

Completion: every check has a verdict; every failure has a file and a line.

### 5. Fix in order

1. Errors: M2 imports, dp text.
2. Tokens: color, type, shape, elevation.
3. Motion: replace ad-hoc timing.
4. Accessibility: labels, roles, targets.
5. States and polish.

Change the theme where the rule belongs to the theme. Change the call site only
when the exception is genuinely local.

Completion: each finding is fixed, explicitly deferred with a reason, or marked a
false positive with a suppression comment.

### 6. Verify

- Compile the module.
- Render light and dark previews.
- Set font scale to 200%.
- Run TalkBack on the touched screens.
- Re-run the script at the chosen threshold.

Completion: build green, script exits 0 (or only documented exceptions remain),
and the manual checks for touched areas pass.

## Output

Report findings as a table: rule, severity, location, evidence, fix, status.
Finish with counts, deferred items, and the verification performed. The exact
template is in `references/checklist.md`.

Rules:

- One row per finding. Do not collapse locations.
- Quote the offending line as evidence; paraphrase hides the problem.
- A heuristic hit is INFO until confirmed. Inspect before calling it a bug.
- Never suppress a finding to make the exit code green. Suppress only after
  inspecting it, and say why on the line.

## Fix ownership

This skill does not restate design guidance. For the fix:

- Tokens, color, type, shape, elevation, motion, state layers:
  `material3-foundation`.
- Component choice, variants, slots, semantics: `material3-components`.
- Adaptive layout, window size classes, keyboard: `adaptive`.
- Navigation structure and back stacks: `navigation-3` (or the project's
  navigation skill).

## References

| File | Read when |
| --- | --- |
| `references/checklist.md` | Running the manual pass, fixing a rule, or writing the report. |
| `scripts/audit.sh` | Running the mechanical pass; `--root` is the app repo. |
| `scripts/selftest.sh` | Changing the script; verifies the rules still fire. |
