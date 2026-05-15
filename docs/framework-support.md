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
| Supported | Covered by PSI or focused parsing, regression tests, folder dry-run, and apply safety. |
| Partial | Some common patterns work and have tests, but important framework syntax is missing. |
| Best effort | May work through generic text processing or custom patterns, but not part of the compatibility contract. |
| Planned | Not implemented or not verified yet. |

## Current Matrix

| Area | Current Level | Current Coverage | Gaps |
| --- | --- | --- | --- |
| HTML/XML | Supported | `class="..."`, custom attributes, editor/folder/dry-run workflows | More malformed/partial markup no-op tests. |
| JSX/TSX | Supported | `className`, string expressions, template literals, ternaries, arrays, object keys, helper calls | More multiline helper call and nested expression tests. |
| CSS/SCSS | Supported | `@apply` in CSS/SCSS, selection and folder flows | More nested at-rule and malformed declaration tests. |
| Vue SFC | Partial | Static template classes, `:class` / `v-bind:class` quoted fragments, object keys, mixed arrays/objects, `<script setup>` helper calls, `<style>` `@apply` | Broader nested expressions and formatting preservation cases before promotion to Supported. |
| Svelte | Best effort | Folder globs include `.svelte`; fallback may sort simple static strings | `class:active`, `class={...}`, reactive expressions, script helper calls, style blocks. |
| Astro | Best effort | Folder globs include `.astro`; fallback may sort simple static strings | `class:list`, JSX-like expressions, frontmatter helper calls. |
| Angular | Best effort | Default attributes include `[ngClass]`; fallback may sort simple quoted fragments | `[class.foo]`, `[ngClass]` arrays/objects, template expressions, custom pipes. |
| Laravel Blade / PHP | Best effort | Folder globs include `.php`; fallback may sort simple static strings | `@class`, PHP arrays, component attributes, escaped directives. |
| Other template engines | Planned | None | Needs demand-driven investigation. |

## Stabilization Track

These items should come before expanding framework coverage heavily.

### Sorter and Candidate Extraction

- Add fixture-based regression tests for every supported syntax shape.
- Separate candidate detection tests from end-to-end service tests.
- Add explicit no-op tests for unsupported or ambiguous syntax.
- Keep sorting stable for partial editor selections.
- Verify custom attributes/functions across HTML, JSX/TSX, Vue, and fallback text processing.

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
- [x] `<script setup>` helper calls such as `cn(...)`, `clsx(...)`, and configured helpers.
- [x] `<style>` `@apply`.
- [x] Formatting preservation around comments and multiline bindings.
- [x] Basic malformed/no-op coverage for dynamic bindings.
- [ ] Broader nested expressions and advanced malformed binding no-op cases.

### Svelte

Target level: Partial, then Supported if PSI support is stable enough.

Investigate:

- Static `class="..."`.
- `class={"..."}`.
- Template literals inside attributes.
- `class:active={condition}` no-op behavior.
- Helper calls in `<script>`.
- `<style>` `@apply`.
- File-level folder dry-run and apply safety.

### Astro

Target level: Partial.

Investigate:

- Static `class="..."`.
- JSX-like `className` / `class` expressions.
- `class:list`.
- Helper calls in frontmatter.
- Component attributes.
- Folder dry-run and apply safety.

### Angular

Target level: Partial.

Investigate:

- Static `class`.
- `[ngClass]="'...'"`.
- `[ngClass]="condition ? '...' : '...'"`.
- `[ngClass]="['...', condition && '...']"`.
- `[ngClass]="{ '...': condition }"`.
- `[class.foo]` no-op behavior.
- Formatting preservation in Angular templates.

### Blade / PHP

Target level: Partial.

Investigate:

- Static `class`.
- Blade component attributes.
- `@class(['...' => condition])`.
- PHP arrays that contain class strings.
- Escaped directives and mixed PHP/HTML no-op behavior.

## Near-Term Milestones

### 0.2.x Stabilization

- Keep CI deterministic and verifier stable.
- Extract dry-run apply logic into testable services where useful.
- Add a dry-run apply result model for UI summaries.
- Add no-op regression tests for unsupported syntax.
- Complete Vue hardening tests before promising broader support.

### 0.3.0 Framework Coverage

- Promote Vue to Supported if gaps are closed.
- Add Svelte and Astro fixture suites.
- Document Svelte/Astro as Partial only after tests exist.
- Keep folder globs broad but support claims narrow.

### 0.4.0 Template Frameworks

- Add Angular and Blade fixture suites.
- Decide whether PHP/Blade needs a dedicated parser path or remains fallback-only.
- Add project-level reports for unsupported files and skipped candidates.

## Future Tooling Ideas

These should wait until sorter/dry-run/apply and framework coverage are stable.

- Smart class-list wrapping and unwrapping.
- Folding for long class attributes.
- Intentions for sort, wrap, unwrap, dedupe, and extract.
- Conservative duplicate and conflict inspections.
- Project reports for longest class lists and repeated class clusters.
