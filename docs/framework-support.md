# Framework Support

Trier is intentionally conservative. It sorts Tailwind class fragments only when the surrounding syntax can be handled predictably. Unsupported or ambiguous syntax is preserved unchanged instead of being rewritten as a best-effort guess.

## Support Policy

- **Sort class fragments, not whole files.** Trier preserves existing formatting, indentation, quotes, semicolons, and framework syntax around supported candidates.
- **Use PSI when it is useful.** HTML, XML-style attributes, JSX/TSX, Vue, CSS, and SCSS use IDE structure where available.
- **Use fallback processing conservatively.** Svelte, Astro, Angular, and Blade/PHP are supported through focused text processing where stable PSI support would add more compatibility cost than value.
- **Treat uncertain syntax as no-op.** Interpolation, malformed expressions, comments, escaped framework syntax, and unsupported dynamic constructs stay unchanged.
- **Do not promote support without coverage.** Supported areas require regression fixtures, no-op fixtures for boundaries, dry-run or apply coverage, and manual smoke coverage where framework behavior is hard to model.

## Support Matrix

| Area | Status | Supported Coverage | No-Op Boundaries |
| --- | --- | --- | --- |
| HTML/XML | Supported | Static `class`, custom attributes, editor selection, file, folder, dry-run, and apply workflows | Broken or ambiguous markup where the IDE cannot expose a safe candidate |
| JSX/TSX | Supported | `className`, string expressions, template literals, ternaries, arrays, object keys, nested helper calls, multiline helper calls, quoted helper object keys | Interpolated template fragments that cannot be sorted as static class lists |
| CSS/SCSS | Supported | `@apply` rules, nested at-rules, style blocks in supported component files | Malformed `@apply` declarations |
| Vue SFC | Supported | Static template classes, `:class`, `v-bind:class`, arrays, objects, nested fragments, `<script setup>` helper calls, `<style>` `@apply` | Malformed bindings and unsupported dynamic/interpolated fragments |
| Svelte | Supported | Static classes, `class={...}` quoted fragments, array/object class values, component class props, SvelteKit-style `$props()` class composition, configured helper calls, static template literals, `<style>` `@apply` | `class:` directives, interpolated template literals, malformed class expressions |
| Astro | Supported | Static `class`, `className`, `class={...}`, `class:list`, arrays, object keys, nested arrays, component attributes, frontmatter helper calls, static template literals, `<style>` `@apply` | Interpolated template literals and malformed `class:list` expressions |
| Angular | Supported | Static `class`, `ngClass`, `[class]`, `[ngClass]`, ternaries, arrays, object keys, inline component templates | `[class.foo]`, `[attr.class]`, method calls, pipes, complex expressions, interpolation, malformed bindings |
| Laravel Blade / PHP | Supported | Static `class`, Blade component attributes, Blade `@class(...)` quoted fragments, mixed PHP/HTML templates | Escaped `@@class(...)`, escaped component `::class`, `$attributes->class(...)`, `$attributes->merge(...)`, generic PHP arrays, interpolation, comments, `@verbatim`, heredoc/nowdoc strings, malformed directives |

## Default Detection Targets

Trier sorts these attribute names by default:

```text
class
className
:class
v-bind:class
ngClass
[class]
[ngClass]
class:list
```

Additional attributes can be configured in `Settings | Tools | Trier | Attributes`. Exact names and regex patterns wrapped in `/.../` are supported.

Helper functions are opt-in through `Settings | Tools | Trier | Functions`. Add project helpers such as:

```text
cn
clsx
tw
twMerge
```

Configured helper names are used for JavaScript/TypeScript, Vue, Svelte, Astro, JSX, and TSX helper-call candidates where Trier can safely identify quoted class fragments.

## Tailwind Paths

Manual settings always win:

- `Stylesheet`
- `Config`

When those fields are blank, Trier auto-detects paths from the current project context. The detector looks for common Tailwind config names and stylesheet entrypoints, including CSS files with Tailwind markers such as:

- `@import "tailwindcss"`
- `@import "tailwindcss/utilities.css"`
- `@plugin "@tailwindcss/..."`
- `@tailwind utilities`
- `@config`

Common vendor, dependency, build, and cache directories are skipped during detection.

## Review And Apply Contract

Dry-run workflows are part of the supported behavior, not a debugging tool.

Trier should:

- scan folders in a cancellable background task;
- show changed files before writing;
- show changed files as a tree or a flat list;
- open file diffs from the review dialog;
- apply one diff, selected files/directories, or all remaining files;
- remove applied files from the remaining report;
- avoid applying stale changes when the original file changed after the dry run;
- report missing, read-only, stale, failed, skipped, and cancelled files clearly.

## Verification Standard

Supported framework behavior is expected to have:

- fixture coverage for supported syntax;
- no-op fixture coverage for unsupported boundaries;
- integration coverage for file or folder workflows where relevant;
- dry-run/apply coverage for user-facing write paths;
- manual sandbox smoke coverage for real framework project shapes.

The sandbox projects live outside this repository in `../trier-sandbox` during local development.

## Future Scope

Trier should continue to focus on safe Tailwind class-list maintenance.

Likely future areas:

- smart class-list wrapping and unwrapping;
- folding for long class attributes;
- intentions for sort, wrap, unwrap, dedupe, and extract;
- conservative duplicate and conflict inspections;
- project reports for long class lists and repeated class clusters;
- demand-driven support for additional template engines.
