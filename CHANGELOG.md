# Changelog

All notable changes to Trier are documented in this file.

## [0.1.0] - 2026-05-11

### Added

- Initial JetBrains IDE plugin for sorting Tailwind CSS classes without running full Prettier formatting.
- Tailwind sorting powered by the `prettier-plugin-tailwindcss/sorter` API.
- Bundled Node-side sorting runtime with `prettier` and `prettier-plugin-tailwindcss`.
- IDE settings UI for Node runtime, Tailwind config, Tailwind stylesheet, preserve whitespace, preserve duplicates, custom attributes, and custom functions.
- Sorting triggers for Save and Reformat Code.
- Manual sorting for the current editor, selected editor range, selected Project View file, and selected Project View directory.
- Folder sorting with configurable glob patterns.
- Dry-run reports with JetBrains diff viewer integration for one or multiple changed files.
- PSI-based processing for HTML, XML, JSX, TSX, CSS, SCSS, and Vue files.
- Platform tests for core editor, selection, folder, dry-run, JSX/TSX, and Vue behavior.

### Notes

- Trier requires a local Node.js 20.19 or newer runtime.
- User projects do not need to install `prettier` or `prettier-plugin-tailwindcss`.
- Astro and Svelte are best-effort only in this version.
