# Framework Support Roadmap

This document tracks how Trier should evolve from a reliable Tailwind class sorter into broader Tailwind class workflow tooling.

The goal is not to replace JetBrains Tailwind CSS completion, documentation, or color preview. Trier should focus on safe class-list maintenance: finding class candidates, sorting them, previewing changes, applying them safely, and later improving readability through wrapping, folding, and inspections.

## Principles

- Preserve surrounding code style and framework syntax.
- Prefer PSI-backed processing when the IDE exposes stable language support.
- Use fallback text processing only for conservative, syntax-agnostic replacements.
- Do not add framework support without regression tests.
- Keep dry-run/apply behavior consistent across editor, file, folder, and Project View workflows.
- Treat unsupported syntax as no-op, not as a best-effort rewrite.

## Support Levels

| Level | Meaning |
| --- | --- |
| Supported | Covered by PSI, focused parsing, or conservative fallback processing with regression tests, folder dry-run, and apply safety. |
| Partial | Some common patterns work and have tests, but important framework syntax is missing. |
| Best effort | May work through generic text processing or custom patterns, but not part of the compatibility contract. |
| Planned | Not implemented or not verified yet. |

## Current Matrix

| Area | Current Level | Current Coverage | Gaps |
| --- | --- | --- | --- |
| HTML/XML | Supported | `class="..."`, custom attributes, editor/folder/dry-run workflows, basic malformed no-op coverage | Broader fixture coverage for partial or broken markup. |
| JSX/TSX | Supported | `className`, string expressions, template literals, ternaries, arrays, object keys, multiline and nested helper calls, quoted helper object keys | Broader real-world helper composition fixtures. |
| CSS/SCSS | Supported | `@apply` in CSS/SCSS, selection and folder flows, basic malformed no-op coverage | More nested at-rule and malformed declaration tests. |
| Vue SFC | Supported | Static template classes, `:class` / `v-bind:class` quoted fragments, nested arrays/objects, `<script setup>` helper calls, `<style>` `@apply`, formatting/comment preservation, dedicated fixture coverage, advanced malformed binding no-op coverage, manual smoke pass | Broader real-world fixture coverage as new Vue patterns are reported. |
| Svelte | Supported | Folder globs include `.svelte`; conservative fallback sorts static `class`, quoted fragments in `class={...}` arrays and object keys, component class props, SvelteKit-style `$props()` class composition, configured script helper calls with nested args and static template literals after helpers are added to Trier's `Functions`, style `@apply`; unsupported `class:` directives, interpolated template literals, and malformed expressions have no-op fixture coverage; folder dry-run, file apply, real-smoke fixture, and manual smoke coverage exists | Demand-driven real-world edge cases; PSI-backed precision only if the IDE API becomes worth the complexity. |
| Astro | Supported | Folder globs include `.astro`; conservative fallback sorts static `class`, quoted fragments in `class={...}` / `className={...}`, `class:list` arrays/object keys/nested arrays, component class attributes, layout/frontmatter variants, configured frontmatter helper calls with nested args and static template literals after helpers are added to Trier's `Functions`, style `@apply`; interpolated template literals have no-op fixture coverage; folder dry-run, file apply, real-smoke fixture, and manual smoke coverage exists | Demand-driven real-world edge cases; PSI-backed precision only if the IDE API becomes worth the complexity. |
| Angular | Partial | Default attributes include `ngClass` and `[ngClass]`; fallback sorts static `class`, `ngClass`, and quoted fragments in `[ngClass]` ternaries, arrays, and object keys; static interpolated class attributes, unsupported `[class.foo]`, custom-pipe `[ngClass]`, and malformed `[ngClass]` have no-op coverage; file apply coverage exists through `.html` files | Deeper Angular template expression edge cases, additional formatting preservation smoke. |
| Laravel Blade / PHP | Partial | Folder globs include `.php`; fallback sorts static `class`, Blade component attributes, and quoted fragments in Blade `@class(...)`; escaped `@@class(...)`, static interpolated class attributes, interpolated Blade/PHP strings, generic PHP arrays, malformed `@class`, and mixed-template fixtures have no-op coverage; file apply coverage exists | Dedicated PHP parser path only if demand justifies it; broader Blade component syntax. |
| Other template engines | Planned | None | Needs demand-driven investigation. |

## Stabilization Track

These items should come before expanding framework coverage heavily.

### Sorter and Candidate Extraction

- Add fixture-based regression tests for every supported syntax shape.
- Keep candidate detection tests separate from end-to-end service tests where practical.
- Continue adding explicit no-op tests for unsupported or ambiguous syntax.
- Keep sorting stable for partial editor selections.
- Maintain custom attribute/function coverage across HTML, JSX/TSX, Vue, and fallback text processing.

### Dry Run and Apply

- Move remaining dry-run apply business logic out of Swing dialogs where practical.
- Keep apply behavior based on original snapshots:
  - apply only if the file still matches the dry-run snapshot;
  - report stale files clearly;
  - report read-only and missing files clearly;
  - never silently partially write a file.
- Preserve the remaining-changes report after every successful apply.
- Keep diff Apply advancing to the next candidate.
- Add result summary data that can be shown in UI and tested without UI.

### Folder and Project View Workflows

- Keep Project View file sorting as single-file dry run.
- Keep Project View directory sorting as folder dry run with the default frontend glob.
- Keep Tools folder sorting for custom root/glob/dry-run choices.
- Keep folder scans cancellable and safe around vendor/build/cache directories.
- Expand tests for skipped, failed, cancelled, stale, read-only, and binary/large files.

## Framework Expansion Track

Each framework should follow the same workflow.

1. Add a fixture test file with representative syntax.
2. Add candidate extraction tests.
3. Add processing tests for sorted output.
4. Add no-op tests for unsupported syntax.
5. Add folder dry-run tests for file extension coverage.
6. Update the matrix and README when behavior becomes supported.

### Vue Hardening

Target level: Supported.

Test and support:

- [x] Static `class`.
- [x] `:class="'...'"`.
- [x] `v-bind:class="'...'"`.
- [x] `:class="\`...\`"`.
- [x] `:class="condition ? '...' : '...'"`.
- [x] `:class="['...', condition && '...']"`.
- [x] `:class="{ '...': condition }"`.
- [x] Mixed arrays and objects.
- [x] Nested arrays and objects with quoted fragments.
- [x] `<script setup>` helper calls such as `cn(...)`, `clsx(...)`, and configured helpers.
- [x] `<style>` `@apply`.
- [x] Formatting preservation around comments and multiline bindings.
- [x] Basic malformed/no-op coverage for dynamic bindings.
- [x] Dedicated fixture suite for covered Vue syntax.
- [x] Advanced malformed binding no-op cases.
- [x] Final manual smoke pass before promotion to Supported.

### Svelte

Target level: Supported.

Investigate:

- [x] Static `class="..."`.
- [x] `class={"..."}`.
- [x] Array/object class values.
- [x] Component class props.
- [x] Static template literals inside attributes.
- [x] `class:active={condition}` no-op behavior.
- [x] Helper calls in `<script>`.
- [x] Static template literals in configured helper calls.
- [x] Manual smoke coverage for helper calls with `cn` configured in `Functions`.
- [x] `<style>` `@apply`.
- [x] Folder dry-run coverage.
- [x] File-level apply coverage.
- [x] Real-smoke fixture coverage.
- [x] Manual smoke pass before promotion to Supported.

### Astro

Target level: Supported.

Investigate:

- [x] Static `class="..."`.
- [x] JSX-like `className` / `class` expressions.
- [x] `class:list`.
- [x] Helper calls in frontmatter.
- [x] Static template literals in configured helper calls.
- [x] Manual smoke coverage for helper calls with `cn` configured in `Functions`.
- [x] Component attributes.
- [x] Folder dry-run coverage.
- [x] File-level apply coverage.
- [x] Real-smoke fixture coverage.
- [x] Manual smoke pass before promotion to Supported.

### Angular

Target level: Partial.

Investigate:

- [x] Static `class`.
- [x] `ngClass="..."`.
- [x] `[ngClass]="'...'"`.
- [x] `[ngClass]="condition ? '...' : '...'"`.
- [x] `[ngClass]="['...', condition && '...']"`.
- [x] `[ngClass]="{ '...': condition }"`.
- [x] `[class.foo]` no-op behavior.
- [x] Malformed `[ngClass]` no-op behavior.
- [x] Custom pipe `[ngClass]` no-op behavior.
- [x] Static class interpolation no-op behavior.
- [x] File-level apply coverage through `.html` files.
- [x] Basic formatting preservation in Angular templates.
- [ ] Complex Angular expression no-op boundaries.

### Blade / PHP

Target level: Partial.

Investigate:

- [x] Static `class`.
- [x] Blade component attributes.
- [x] `@class(['...' => condition])`.
- [x] Interpolated Blade/PHP string no-op behavior.
- [x] Generic PHP arrays that contain class strings stay no-op.
- [x] Malformed `@class` no-op behavior.
- [x] Escaped `@@class(...)` no-op behavior.
- [x] Static class interpolation no-op behavior.
- [x] Mixed PHP/HTML fixture coverage.
- [x] File-level apply coverage.
- [ ] Dedicated PHP arrays only if a safe parser-backed path is added.

## Near-Term Milestones

### 0.2.x Stabilization

- Keep CI deterministic and verifier stable.
- Keep Docker/remote runtime diagnostics clear after the 0.2.5 runtime expansion.
- Improve Tailwind stylesheet/config diagnostics for sorter initialization failures.
- Keep this framework roadmap aligned with the tested support matrix.
- Extract dry-run apply logic into testable services where useful.
- Add a dry-run apply result model for UI summaries.
- Continue no-op regression tests for unsupported syntax.
- Complete a Vue fixture suite before promoting Vue to Supported.

### 0.3.x Svelte and Astro Coverage

- [x] Promote Vue to Supported in 0.3.0.
- [x] Add Svelte and Astro fixture suites in 0.3.1.
- [x] Add Astro `class:list` array/object/nested-array coverage.
- [x] Add Svelte array/object/component class prop coverage.
- [x] Add Svelte/Astro folder dry-run and file apply integration coverage.
- [x] Add SvelteKit-style `$props()` and Astro layout/frontmatter variant fixtures.
- [x] Add Svelte/Astro real-smoke fixtures for representative supported and no-op boundaries.
- [x] Promote Svelte/Astro to Supported after smoke coverage confirmed the documented no-op boundaries.

Manual smoke checklist completed before promotion:

- Svelte file: static `class`, `class={...}` ternary, array/object class values, static template literal class, and `class:` directive no-op.
- Svelte component: `$props()` class alias, configured `cn(...)` helper with nested args, `cn(\`...\`)`, interpolated helper no-op, and `<style>` `@apply`.
- Astro file: static `class`, `class={...}`, `className={...}`, `class:list` arrays/object keys/nested arrays, and interpolated `class:list` no-op.
- Astro component/layout: frontmatter `cn(...)` variants, `cn(\`...\`)`, component `class:list`, interpolated helper no-op, and `<style>` `@apply`.
- Workflow: Project View file dry-run opens diff directly, folder dry-run lists `.svelte` and `.astro`, Apply Selected and diff Apply remove applied files.

### 0.4.0 Template Frameworks

- [x] Add Angular and Blade fixture suites.
- [x] Add conservative Blade `@class(...)` fallback support.
- [x] Keep generic PHP arrays no-op until a safe parser-backed path exists.
- Keep Angular and Blade/PHP support fallback-only in 0.4.x; do not add Angular/PHP/Blade PSI dependencies until demand justifies the compatibility and verifier cost.
- Keep shared attribute no-op guards aligned between XML/HTML PSI processing and fallback text processing.
- Add project-level reports for unsupported files and skipped candidates.

## Future Tooling Ideas

These should wait until sorter/dry-run/apply and framework coverage are stable.

- Smart class-list wrapping and unwrapping.
- Folding for long class attributes.
- Intentions for sort, wrap, unwrap, dedupe, and extract.
- Conservative duplicate and conflict inspections.
- Project reports for longest class lists and repeated class clusters.
