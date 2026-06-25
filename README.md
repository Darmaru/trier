<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/31703-trier">
    <img src="docs/assets/trier.svg" alt="Trier logo" width="120">
  </a>
</p>

<h1 align="center">Trier</h1>

<p align="center">
  Tailwind CSS class sorting for JetBrains IDEs.
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
Trier keeps Tailwind CSS classes in a consistent order directly inside JetBrains IDEs. It uses the official Tailwind Labs sorter, preserves your existing formatting, and gives you safe editor, Project View, folder, dry-run, diff, and selective apply workflows without handing whole files to Prettier.
<!-- Plugin description end -->

Trier is for projects where Tailwind class lists live everywhere: templates, components, helper calls, style blocks, backend views, and framework-specific bindings. It focuses on one job: sort class lists reliably while leaving the rest of your code alone.

## Why Trier

- **Focused class sorting**: no broad formatting churn, no quote changes, no semicolon changes, no unrelated rewrites.
- **Official Tailwind order**: class ordering comes from Tailwind Labs' `prettier-plugin-tailwindcss` sorter.
- **IDE-native review flow**: run from the editor, Project View, save, reformat, or folder scan.
- **Safe bulk cleanup**: preview changed files in a dry-run tree, inspect diffs, then apply selected files or everything.
- **Framework coverage**: HTML, JSX, TSX, Vue, Svelte, Astro, Angular, Blade/PHP, CSS, and SCSS.
- **Project-aware settings**: runtime checks and Tailwind path detection use the project whose settings are open.

## What It Sorts

Trier handles common Tailwind class locations:

- HTML/XML `class`
- JSX/TSX `className`, string expressions, ternaries, arrays, object keys, and configured helper calls
- Vue template bindings, `<script setup>` helper calls, and `<style>` `@apply`
- Svelte classes, component props, helper calls, and style blocks
- Astro `class`, `className`, `class:list`, frontmatter helpers, and style blocks
- Angular `class`, `ngClass`, `[class]`, `[ngClass]`, and inline component templates
- Laravel Blade / PHP static classes, Blade component attributes, and Blade `@class(...)`
- CSS/SCSS `@apply`
- Custom attributes and helper functions such as `data-classes`, `cn`, `clsx`, or `tw`

Unsupported or ambiguous framework syntax is preserved as no-op instead of being rewritten aggressively. See [Framework Support](docs/framework-support.md) for the current support contract.

## Safe Dry Runs

Folder and Project View sorting can run as a dry run before anything is written.

You can:

- scan a file, folder, or project subtree;
- review changed files as a tree or flat list;
- open all diffs from one review window;
- apply selected files or selected directories;
- apply one file directly from the diff window;
- copy the dry-run report;
- keep stale, failed, skipped, or cancelled files visible in the report.

Applied files are removed from the review list, so large cleanups can be handled in batches.

## Quick Start

1. Install Trier from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31703-trier).
2. Open `Settings | Tools | Trier`.
3. Choose a Node.js runtime or use the IDE JavaScript Runtime selector.
4. Leave Tailwind `Stylesheet` and `Config` blank for auto-detection, or set them explicitly.
5. Run `Test Trier runtime`.
6. Use `Sort Tailwind Classes` from the editor, Project View, save/reformat hooks, or folder action.

Node.js `20.19` or newer is required because the Tailwind sorter runs in Node.js. Trier bundles the Node-side sorter dependencies, so your project does not need to install `prettier` or `prettier-plugin-tailwindcss`.

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

## Settings

Trier lives in `Settings | Tools | Trier`.

- `Node interpreter`: local or Docker/remote Node.js runtime from the IDE JavaScript Runtime selector.
- `Test Trier runtime`: validates the selected runtime, bundled helper startup, Tailwind path detection, and a real sample sort.
- `Sort on Save`: sorts supported class candidates when files are saved.
- `Sort on Code Reformat`: runs after the IDE reformat action.
- `Stylesheet` / `Config`: manual Tailwind paths. Blank values are auto-detected per project.
- `Preserve whitespace` / `Preserve duplicates`: passed to the Tailwind sorter.
- `Attributes`: extra attribute names or regex patterns.
- `Functions`: helper names or regex patterns, for example `cn`, `clsx`, `tw`, or `/.*Class.*/`.

Default attribute targets:

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

## Development

Build the plugin:

```bash
./gradlew buildPlugin
```

Run tests:

```bash
./gradlew test
```

Verify plugin compatibility against the current target IDE:

```bash
./gradlew verifyPlugin
```

Verify locally against JetBrains recommended IDE versions:

```bash
./gradlew verifyPluginRecommended
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
git tag v0.4.5
git push origin v0.4.5
```

The workflow runs `check`, builds the plugin artifact, uploads the ZIP as a GitHub Actions artifact, signs the plugin when signing secrets are present, and publishes it to JetBrains Marketplace with `publishPlugin`.
