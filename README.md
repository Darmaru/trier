<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/31703-trier">
    <img src="docs/assets/trier.svg" alt="Trier logo" width="120">
  </a>
</p>

<h1 align="center">Trier</h1>

<p align="center">
  Sort Tailwind CSS classes in JetBrains IDEs without full Prettier formatting.
</p>

<!-- Plugin description -->
Trier is a JetBrains IDE plugin for sorting Tailwind CSS classes with the Tailwind Labs sorter from `prettier-plugin-tailwindcss`, without running full Prettier code formatting.
<!-- Plugin description end -->

Trier focuses only on class ordering. It does not reformat the rest of the file.

## Features

- Sort Tailwind classes in the current editor.
- Sort only the selected class list or selected code range.
- Sort classes on Save.
- Sort classes after Code Reformat.
- Sort a selected file from the Project View context menu.
- Sort a folder from `Tools` or the Project View context menu.
- Preview file or folder changes with dry-run reports before writing anything.
- Open dry-run results in the JetBrains diff viewer.
- Use the IDE JavaScript Runtime selector for Node.js.
- Bundle `prettier` and `prettier-plugin-tailwindcss` inside the plugin.
- Validate Trier runtime from settings with a real sorter round-trip.

## Supported Files and Patterns

- HTML and XML `class="..."`
- JSX and TSX `className`, string literals, template literals, and quoted fragments inside expressions
- CSS and SCSS `@apply`
- Vue single-file component `<template>` class attributes
- Vue dynamic `:class` quoted fragments
- Vue `<script setup>` custom function calls such as `cn("...")`
- Vue `<style>` `@apply`

## Actions

Use `Tools | Sort Tailwind Classes` to sort the current editor or selected fragment.

Use `Tools | Sort Tailwind Classes in Folder` to sort a folder with a glob pattern.

Use the Project View context menu:

- On a file: `Sort Tailwind Classes`
- On a directory: `Sort Tailwind Classes in Folder`

The folder dialog supports `Dry run`. In this mode Trier scans and reports how many files would change, but does not write anything.
For one changed file, Trier can open the result directly as a diff. For multiple changed files, Trier shows a report dialog with the changed file list and actions to open individual diffs or all diffs.

Example glob:

```text
**/*.{html,js,jsx,ts,tsx,vue,css,scss}
```

## Settings

Settings are available under `Settings | Tools | Trier`.

Runtime:

- `Node interpreter` uses the same JavaScript Runtime selector as the IDE.
- `Test Trier runtime` checks Node.js, bundled runtime extraction, helper script startup, and a sample sorter round-trip.

Triggers:

- `Sort on Save`
- `Sort on Code Reformat`

Tailwind CSS:

- `Stylesheet`, equivalent to `tailwindStylesheet`
- `Config`, equivalent to `tailwindConfig`
- `Preserve whitespace`, equivalent to `tailwindPreserveWhitespace`
- `Preserve duplicates`, equivalent to `tailwindPreserveDuplicates`
- `Attributes`, equivalent to `tailwindAttributes`
- `Functions`, equivalent to `tailwindFunctions`

The `Stylesheet` and `Config` file choosers open in the project root when no valid path is already selected.

## Notes about framework coverage

- Vue support is enabled through the optional bundled dependency `org.jetbrains.plugins.vue`.
- Trier currently has real platform tests for `.vue` files.
- Astro and Svelte are not yet part of the verified support matrix in this project, because the current IntelliJ test runtime used here does not include dedicated bundled Astro or Svelte plugins.
- Until that changes, Astro and Svelte should be treated as best-effort only, not as officially supported file types.

## Runtime Notes

Trier bundles the Node-side sorter dependencies used by the plugin. User projects do not need to install `prettier` or `prettier-plugin-tailwindcss` for Trier to run.

A local Node.js runtime is still required. The bundled Tailwind sorter currently requires Node.js `20.19` or newer. Remote Node interpreters are not supported yet.

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
git tag v0.1.0
git push origin v0.1.0
```

The workflow runs `check`, builds the plugin artifact, uploads the ZIP as a GitHub Actions artifact, signs the plugin when signing secrets are present, and publishes it to JetBrains Marketplace with `publishPlugin`.
