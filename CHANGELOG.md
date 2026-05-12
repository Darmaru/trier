# Changelog

All notable changes to Trier are documented in this file.

## [0.2.0] - 2026-05-12

### Added

- Added background editor, save, and reformat sorting so Tailwind processing no longer blocks the IDE UI path.
- Added document snapshot checks before applying background sort results, with save-triggered sorting saving the document again after applying changes.
- Added a quick Project View dry-run action for selected files and folders, with file selections opening a diff directly and folder selections opening a changed-file review list.
- Added a grouped dry-run review dialog with file type icons, a flat/grouped toggle, per-file diff chain navigation, and per-file apply support.
- Added an `Apply` action to dry-run diffs so individual previewed file changes can be written without rerunning the full folder sort.
- Added a Tools menu `Sort Tailwind Classes in Folder...` action for configurable folder, glob pattern, and dry-run workflows.
- Added Node worker response timeouts and restart handling for stuck helper processes.
- Added local Node.js version validation requiring Node.js 20.19 or newer.
- Added custom attribute and function regex validation in settings before values are saved.
- Added pull request and main-branch CI for `check`, `verifyPlugin`, and `buildPlugin`.
- Added IntelliJ Plugin Verifier configuration for the current target IDE.
- Added JaCoCo coverage thresholds to fail `check` on coverage regressions.

### Changed

- Merged the Project View file and folder actions into one `Sort Tailwind Classes` action that always previews changes before writing.
- Improved dry-run diff window ownership so opened diff windows stay in front of the dry-run review dialog.
- Updated dry-run apply behavior to remove applied files from the review list and advance the diff chain to the next remaining candidate.
- Hardened folder sorting by skipping common vendor, build, and cache directories during scans.
- Skipped binary files and files larger than 2 MiB during folder and Project View file sorting.
- Updated the IntelliJ Platform Gradle Plugin to 2.16.0 and enabled Gradle toolchain provisioning.
- Updated release documentation for the 0.2.0 release flow.

## [0.1.2] - 2026-05-11

### Added

- Added JaCoCo test coverage reporting with HTML and XML reports.
- Added additional platform and service tests for Project View selection, dry-run reports, Node helper extraction, Node worker execution, folder glob handling, file dry-runs, and settings propagation.
- Added README badges for JetBrains Marketplace version, downloads, publishing status, license, changelog, and Node.js runtime requirement.

### Changed

- Reworked the README to better explain Trier's editor workflows, folder sorting, dry-run diffs, supported code patterns, settings, runtime requirements, and framework coverage.
- Updated the JetBrains Marketplace plugin description to match the expanded README messaging.

## [0.1.1] - 2026-05-11

### Added

- Automated JetBrains Marketplace publishing via GitHub Actions.
- Plugin signing and Marketplace publishing configuration for CI releases.
- GitHub Actions plugin ZIP artifact upload for each tagged release.

### Changed

- Documented the release flow and required GitHub repository secrets.

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
