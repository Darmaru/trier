<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/31703-trier">
    <img src="docs/assets/trier.svg" alt="Trier logo" width="120">
  </a>
</p>

<h1 align="center">Trier</h1>

<p align="center">
  Tailwind CSS class sorting for JetBrains IDEs, powered by the official Tailwind Labs sorter.
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/31703-trier"><img src="https://img.shields.io/jetbrains/plugin/v/31703-trier?label=JetBrains%20Marketplace" alt="JetBrains Marketplace version"></a>
  <a href="https://plugins.jetbrains.com/plugin/31703-trier"><img src="https://img.shields.io/jetbrains/plugin/d/31703-trier?label=downloads" alt="JetBrains Marketplace downloads"></a>
  <a href="https://github.com/Darmaru/trier/actions/workflows/publish.yml"><img src="https://img.shields.io/github/actions/workflow/status/Darmaru/trier/publish.yml?label=publish" alt="Publish workflow status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Darmaru/trier" alt="License"></a>
  <img src="https://img.shields.io/badge/node-%3E%3D20.19-339933?logo=node.js&logoColor=white" alt="Node.js 20.19 or newer">
  <a href="CHANGELOG.md"><img src="https://img.shields.io/badge/changelog-0A66C2" alt="Changelog"></a>
</p>

<!-- Plugin description -->
Trier is a JetBrains IDE plugin for sorting Tailwind CSS classes without handing the whole file to Prettier. It uses the official Tailwind Labs sorter from `prettier-plugin-tailwindcss`, preserves surrounding code style, and gives you IDE-native workflows for editor sorting, save and reformat hooks, Project View actions, folder scans, dry-run reviews, diffs, and selective apply.
<!-- Plugin description end -->

Trier is built for teams that want consistent Tailwind ordering without broad formatting churn. It sorts class lists, `@apply` rules, JSX/TSX expressions, Vue bindings, Svelte/Astro fallback patterns, and configured class helper functions while keeping the surrounding framework syntax and formatting intact.

## Why Trier

- **Focused formatting**: sort Tailwind classes without changing indentation, quotes, semicolons, wrapping, or unrelated code style.
- **Official Tailwind ordering**: class order comes from `prettier-plugin-tailwindcss/sorter`.
- **No project dependency required**: Trier bundles `prettier` and `prettier-plugin-tailwindcss` for its own runtime.
- **Project-aware Tailwind options**: detects Tailwind config and stylesheet files when the settings fields are left blank.
- **IDE-native workflow**: run from the editor, selection, save, reformat, Project View actions, or folder actions.
- **Safe bulk cleanup**: scan folders with glob patterns, preview every change, and apply all or selected dry-run results from the review dialog.
- **Framework-aware processing**: uses PSI when available and falls back to text processing where needed.

## Core Features

### Editor Sorting

- Sort the current editor from `Tools | Sort Tailwind Classes`.
- Sort from the editor context menu.
- Sort only the selected plain class list.
- Sort only class candidates fully contained in the current selection.
- Keep unrelated parts of the file unchanged.

### Automation

- Sort on Save.
- Sort after the IDE `Reformat Code` action.
- Respect the active editor selection when reformat sorting is triggered.
- Guard against recursive sorting while a document is already being processed.

### Project View Actions

- Preview sorting for a selected file or folder from the Project View context menu.
- Review changed files before anything is written.
- Sort any folder from `Tools | Sort Tailwind Classes in Folder`.
- Use configurable glob patterns for folder scans.
- Run folder sorting in a cancellable background task.

### Dry Runs and Diffs

Folder sorting supports `Dry run`, which scans and reports changes before writing files.

- See scanned, matched, changed, unchanged, skipped, failed, and cancelled counts.
- Inspect a single changed file directly in the JetBrains diff viewer.
- Review multiple changed files in a grouped tree or flat list.
- Select one file, several files, or a directory of changed files.
- Open diffs for the selected files.
- Apply every remaining change with `Apply`.
- Apply only the selected files or directories with `Apply Selected`.
- Apply an individual change from the diff viewer.
- Copy the dry-run report.
- Applied files are removed from the review list and from the copied report.

## Supported Code Patterns

Trier handles the common places where Tailwind class lists appear:

- HTML/XML `class="..."`
- JSX/TSX `className="..."`
- JSX/TSX `className={"..."}`
- JSX/TSX ternaries, arrays, and object keys containing quoted class fragments
- Vue `<template>` static `class`
- Vue dynamic `:class` quoted fragments
- Vue `<script setup>` custom class helper calls
- Svelte static classes, `class={...}` arrays/object keys, component class props, nested helper calls, static template literal helpers, and `<style>` `@apply`
- Astro static classes, `class={...}` / `className={...}`, `class:list` arrays/object keys, component class attributes, nested frontmatter helper calls, static template literal helpers, and `<style>` `@apply`
- Angular static classes, `ngClass`, `[class]`, and `[ngClass]` quoted fragments in ternaries, arrays, object keys, and inline component templates
- Laravel Blade / PHP static classes, Blade component attributes, and Blade `@class(...)` quoted fragments
- CSS/SCSS `@apply`
- Vue `<style>` `@apply`
- Custom attributes such as `data-classes`
- Custom functions such as `cn("...")`, `clsx({ "...": active })`, or configured tagged templates such as `tw`

Default attribute targets:

```text
class
className
:class
v-bind:class
ngClass
[ngClass]
[class]
class:list
```

Custom attributes and functions can be exact names or regex patterns wrapped in `/.../`.

Examples:

```text
data-classes
/data-.+/
cn
clsx
tw
```

## Folder Sorting

The folder dialog starts with this broad frontend glob:

```text
**/*.{html,js,jsx,ts,tsx,vue,astro,svelte,css,scss,php}
```

You can narrow it for a safer pass:

```text
**/*.{html,jsx,tsx,vue,css,scss}
```

Recommended workflow for large projects:

1. Run `Sort Tailwind Classes in Folder`.
2. Enable `Dry run`.
3. Review the grouped file tree and selected diffs.
4. Apply selected directories or files in batches.
5. Use `Apply` when the remaining preview looks correct.

## Settings

Open `Settings | Tools | Trier`.

### Runtime

- `Node interpreter`: uses the IDE JavaScript Runtime selector, including local and Docker/remote runtimes supported by the IDE.
- `Test Trier runtime`: validates Node.js resolution, reports the actual Node.js version, checks bundled runtime extraction and helper startup, and runs a real sample sort.

### Triggers

- `Sort on Save`
- `Sort on Code Reformat`

### Tailwind CSS

- `Stylesheet`: passed to the Tailwind sorter as `tailwindStylesheet`; leave blank to auto-detect a Tailwind stylesheet such as `src/app.css` or `app/globals.css`.
- `Config`: passed as `tailwindConfig`; leave blank to auto-detect `tailwind.config.js`, `.cjs`, `.mjs`, `.ts`, `.cts`, or `.mts`.
- `Preserve whitespace`: passed as `tailwindPreserveWhitespace`.
- `Preserve duplicates`: passed as `tailwindPreserveDuplicates`.
- `Attributes`: additional `tailwindAttributes`, one per line or comma-separated.
- `Functions`: `tailwindFunctions`, one per line or comma-separated. Add helpers such as `cn`, `clsx`, or `twMerge` here before Trier sorts their string arguments.

Manual stylesheet and config paths always take precedence over auto-detected paths. The stylesheet detector looks for common Tailwind entrypoint names and CSS files containing Tailwind markers such as `@import "tailwindcss"`, `@import "tailwindcss/utilities.css"`, `@plugin "@tailwindcss/typography"`, `@tailwind utilities`, or `@config`, while skipping common vendor, build, and cache directories.

The stylesheet and config file choosers open in the project root when no valid path is already selected.

## Runtime Notes

Trier bundles the Node-side sorter dependencies used by the plugin. Your project does not need to install `prettier` or `prettier-plugin-tailwindcss`.

Node.js is still required because the Tailwind sorter runs in Node.js. The bundled Tailwind sorter currently requires Node.js `20.19` or newer. Trier supports local and Docker/remote Node.js runtimes configured through the IDE JavaScript Runtime selector. For Docker/remote runtimes, Node.js `22` is recommended.

## Framework Coverage

Trier has PSI-backed processing and tests for HTML, XML-style attributes, JSX, TSX, CSS, SCSS `@apply`, and Vue single-file components. Svelte and Astro are supported through the conservative fallback processor, dedicated fixtures, and manual smoke coverage.

Vue support is enabled through the optional bundled dependency `org.jetbrains.plugins.vue`.

Vue is covered by dedicated fixtures for static template classes, dynamic `:class` / `v-bind:class` bindings, nested arrays/objects, `<script setup>` helpers, `<style>` `@apply`, formatting preservation, and malformed no-op cases.

Svelte support includes static classes, `class={...}` quoted fragments, arrays/object keys, component class props, configured helper calls with nested args and static template literals, `<style>` `@apply`, and no-op coverage for `class:` directives and interpolated template literals. Helper calls require adding helpers such as `cn` to Trier's `Functions` setting.

Astro support includes static classes, `class={...}` / `className={...}`, `class:list` arrays/object keys, component class attributes, configured frontmatter helper calls with nested args and static template literals, `<style>` `@apply`, and no-op coverage for interpolated template literals. Helper calls require adding helpers such as `cn` to Trier's `Functions` setting.

Angular and Laravel Blade / PHP have partial fallback support. Angular coverage includes static `class`, `ngClass`, and quoted fragments inside `[class]` / `[ngClass]` expressions and inline component templates. Blade/PHP coverage includes static classes, Blade component attributes, and quoted fragments inside Blade `@class(...)`; Blade comments, `@verbatim` blocks, PHP heredoc/nowdoc strings, generic PHP arrays, and interpolated Blade/PHP strings stay no-op.

See [Framework Support Roadmap](docs/framework-support.md) for the working support matrix and planned stabilization steps.

## Examples

HTML:

```html
<button class="text-center p-4 flex bg-red-500 font-bold"></button>
```

becomes:

```html
<button class="flex bg-red-500 p-4 text-center font-bold"></button>
```

JSX/TSX:

```tsx
const view = <div className={active ? "text-center p-4 flex bg-red-500 font-bold" : "font-bold flex p-4"} />;
```

becomes:

```tsx
const view = <div className={active ? "flex bg-red-500 p-4 text-center font-bold" : "flex p-4 font-bold"} />;
```

Vue:

```vue
<template>
  <div :class="['text-center p-4 flex bg-red-500 font-bold', isActive && `font-bold flex p-4`]"></div>
</template>
```

becomes:

```vue
<template>
  <div :class="['flex bg-red-500 p-4 text-center font-bold', isActive && `flex p-4 font-bold`]"></div>
</template>
```

CSS:

```css
.button {
  @apply text-center p-4 flex bg-red-500 font-bold;
}
```

becomes:

```css
.button {
  @apply flex bg-red-500 p-4 text-center font-bold;
}
```

## Development

Build the plugin:

```bash
./gradlew buildPlugin
```

Run tests:

```bash
./gradlew test
```

Run the sandbox IDE:

```bash
./gradlew runIde
```

## Publishing

Publishing is automated with GitHub Actions when a version tag is pushed.

Required GitHub repository secrets:

- `PUBLISH_TOKEN`
- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`

Release flow:

```bash
git tag v0.2.6
git push origin v0.2.6
```

The workflow runs `check`, builds the plugin artifact, uploads the ZIP as a GitHub Actions artifact, signs the plugin when signing secrets are present, and publishes it to JetBrains Marketplace with `publishPlugin`.
