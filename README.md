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
Trier is a JetBrains IDE plugin that keeps Tailwind CSS class lists consistently ordered while leaving the rest of your code untouched. It uses the Tailwind Labs sorting engine from `prettier-plugin-tailwindcss`, bundles the Node-side runtime inside the plugin, and integrates directly with editor actions, save hooks, reformat actions, Project View context menus, folder scans, dry-run reports, and the JetBrains diff viewer.
<!-- Plugin description end -->

Trier is built for teams that want reliable Tailwind ordering without handing every file to Prettier. It sorts class lists, `@apply` rules, JSX/TSX expressions, Vue bindings, and configured class helper functions while preserving the surrounding framework syntax and formatting.

## Why Trier

- **Focused formatting**: sort Tailwind classes without changing indentation, quotes, semicolons, wrapping, or unrelated code style.
- **Official Tailwind ordering**: class order comes from `prettier-plugin-tailwindcss/sorter`.
- **No project dependency required**: Trier bundles `prettier` and `prettier-plugin-tailwindcss` for its own runtime.
- **IDE-native workflow**: run from the editor, selection, save, reformat, Project View file actions, or folder actions.
- **Safe bulk cleanup**: scan folders with glob patterns and preview every change with dry-run reports and JetBrains diffs.
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

- Sort a selected file from the Project View context menu.
- Sort a selected folder from the Project View context menu.
- Sort any folder from `Tools | Sort Tailwind Classes in Folder`.
- Use configurable glob patterns for folder scans.
- Run folder sorting in a cancellable background task.

### Dry Runs and Diffs

Folder sorting supports `Dry run`, which scans and reports changes without writing files.

- See scanned, matched, changed, unchanged, skipped, failed, and cancelled counts.
- Inspect a single changed file directly in the JetBrains diff viewer.
- Inspect multiple changed files through a diff list or full diff chain.
- Copy the dry-run report.
- Keep original files untouched until you run without `Dry run`.

## Supported Code Patterns

Trier handles the common places where Tailwind class lists appear:

- HTML/XML `class="..."`
- JSX/TSX `className="..."`
- JSX/TSX `className={"..."}`
- JSX/TSX ternaries, arrays, and object keys containing quoted class fragments
- Vue `<template>` static `class`
- Vue dynamic `:class` quoted fragments
- Vue `<script setup>` custom class helper calls
- CSS/SCSS `@apply`
- Vue `<style>` `@apply`
- Custom attributes such as `data-classes`
- Custom functions such as `cn("...")`, `clsx("...")`, or configured tagged templates such as `tw`

Default attribute targets:

```text
class
className
:class
[ngClass]
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
3. Review the report and diffs.
4. Run again without `Dry run` when the preview looks correct.

## Settings

Open `Settings | Tools | Trier`.

### Runtime

- `Node interpreter`: uses the IDE JavaScript Runtime selector.
- `Test Trier runtime`: validates Node.js resolution, bundled runtime extraction, helper script startup, and a real sample sort.

### Triggers

- `Sort on Save`
- `Sort on Code Reformat`

### Tailwind CSS

- `Stylesheet`: passed to the Tailwind sorter as `tailwindStylesheet`.
- `Config`: passed as `tailwindConfig`.
- `Preserve whitespace`: passed as `tailwindPreserveWhitespace`.
- `Preserve duplicates`: passed as `tailwindPreserveDuplicates`.
- `Attributes`: additional `tailwindAttributes`, one per line or comma-separated.
- `Functions`: `tailwindFunctions`, one per line or comma-separated.

The stylesheet and config file choosers open in the project root when no valid path is already selected.

## Runtime Notes

Trier bundles the Node-side sorter dependencies used by the plugin. Your project does not need to install `prettier` or `prettier-plugin-tailwindcss`.

A local Node.js runtime is still required because the Tailwind sorter runs in Node.js. The bundled Tailwind sorter currently requires Node.js `20.19` or newer. Remote Node interpreters are not supported yet.

## Framework Coverage

Trier has PSI-backed processing and tests for HTML, XML-style attributes, JSX, TSX, CSS, SCSS `@apply`, and Vue single-file components.

Vue support is enabled through the optional bundled dependency `org.jetbrains.plugins.vue`.

Astro, Svelte, and PHP files can be included in folder globs and may work through the fallback text processor, but they are best-effort in the current test matrix.

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
git tag v0.2.0
git push origin v0.2.0
```

The workflow runs `check`, builds the plugin artifact, uploads the ZIP as a GitHub Actions artifact, signs the plugin when signing secrets are present, and publishes it to JetBrains Marketplace with `publishPlugin`.
