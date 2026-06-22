# Changelog

All notable changes to Trier are documented in this file.

## [0.4.4] - 2026-06-22

### Changed

- Hardened fallback-after-PSI detection so Blade fallback only runs for real unescaped `@class(...)` directives.

### Added

- Added Angular and Blade/PHP real-smoke fixture coverage for the 0.4.x promotion track.
- Added folder dry-run integration coverage for Angular and Blade/PHP real-smoke fixtures through the default frontend glob.
- Documented the conservative Angular and Blade/PHP support contract ahead of the 0.4.5 promotion target.

## [0.4.3] - 2026-06-14

### Added

- Added a `verifyPluginRecommended` Gradle task for local compatibility checks against JetBrains recommended IDE versions while keeping CI on the current target IDE.
- Added nested CSS at-rule fixture coverage for `@apply` sorting.
- Added Angular complex expression no-op fixture coverage.
- Added Blade escaped component attribute and `$attributes->class(...)` / `$attributes->merge(...)` no-op fixture coverage.

### Changed

- Raised Gradle daemon JVM memory settings for local and CI IntelliJ Platform test and verifier runs.

### Fixed

- Kept HTML comments and block comments as no-op fallback ranges while still sorting supported sibling candidates.
- Kept comments inside Blade `@class(...)` as no-op ranges while sorting supported sibling class fragments.
- Sorted Blade `@class(...)` directives in files that also contain PSI-backed `class="..."` attributes.

## [0.4.2] - 2026-06-11

### Added

- Added Angular `[class]` fallback support for quoted class fragments.
- Added Angular inline component template fixture coverage.
- Added folder dry-run integration coverage for Angular inline templates through the default frontend glob.

### Fixed

- Kept Angular `[attr.class]`, method-call class bindings, and pipe-based nested expressions as no-op fallback candidates.
- Kept Blade comments, `@verbatim` blocks, and PHP heredoc/nowdoc template strings as no-op fallback ranges.

## [0.4.1] - 2026-06-09

### Added

- Added mixed Blade/PHP template and Angular formatting fixture coverage for partial framework support.

### Changed

- Aligned XML/HTML PSI attribute processing with fallback no-op guards for interpolated class attributes and Angular pipes.

### Fixed

- Kept escaped Blade `@@class(...)` directives as no-op fallback candidates.
- Kept Angular `[ngClass]` expressions that use custom pipes as no-op fallback candidates.
- Kept static class attributes with Blade/Angular/PHP interpolation as no-op fallback candidates.

## [0.4.0] - 2026-06-07

### Added

- Added Angular fallback fixture coverage for static `class`, `ngClass`, `[ngClass]` ternaries, arrays, object keys, malformed no-op behavior, and unsupported `[class.foo]` no-op behavior.
- Added Blade/PHP fallback fixture coverage for static classes, Blade component attributes, Blade `@class(...)`, interpolated Blade/PHP no-op behavior, generic PHP array no-op behavior, and malformed `@class` no-op behavior.
- Added file-apply integration coverage for Angular `[ngClass]` and Blade `@class(...)`.

### Changed

- Promoted Angular and Laravel Blade / PHP from Best effort to Partial in the framework roadmap.
- Included Blade `@class(...)` changes in folder dry-run reports instead of treating the directive as unsupported.
- Moved processing fixture resources from `fixtures/sorting` to `fixtures/processing` to match their test package ownership.

## [0.3.4] - 2026-06-06

### Added

- Added Svelte and Astro real-smoke fixture coverage that combines the documented supported patterns and no-op boundaries in representative component files.

### Changed

- Promoted Svelte and Astro support to Supported after fixture hardening and manual smoke verification.
- Updated README, Marketplace description, and framework roadmap to describe Svelte and Astro as conservative fallback-backed supported frameworks.

## [0.3.3] - 2026-06-05

### Added

- Added fallback support for static template literals in configured helper calls.
- Added SvelteKit-style component props and Astro layout/frontmatter variant fixtures for promotion prep.
- Added Svelte script-helper and Astro frontmatter-helper fixtures for nested helper arguments.
- Added Svelte script-helper and Astro frontmatter-helper fixtures for static template literals and interpolated no-op cases.

### Changed

- Clarified that Svelte and Astro helper-call sorting requires adding helpers such as `cn` to Trier's configured `Functions`.

## [0.3.2] - 2026-06-03

### Added

- Added Astro `class:list` fallback support for quoted class fragments in arrays and object keys.
- Added Astro component attribute fixtures for `class` and `class:list`.
- Added malformed Astro `class:list` no-op coverage.
- Added Svelte fallback fixtures for array/object class values and component class props.

### Changed

- Updated README, Marketplace description, and framework roadmap to reflect the expanded Svelte and Astro fallback coverage.

## [0.3.1] - 2026-06-01

### Added

- Added Svelte and Astro fallback support and fixture baselines for braced class expressions, configured helper calls, style `@apply`, and unsupported framework-specific no-op syntax.
- Added no-op coverage for interpolated template literals in braced fallback class attributes.

## [0.3.0] - 2026-05-30

### Added

- Promoted Vue single-file component support to Supported after adding dedicated fixture coverage and completing a manual smoke pass.
- Added a dedicated Vue fixture suite covering template class bindings, comment preservation, `<script setup>` helpers, `<style>` `@apply`, and malformed no-op behavior.
- Added a shared processing fixture harness using paired `input.<ext>` and `expected.<ext>` files so future framework regression suites can use the same layout.
- Added HTML and CSS sorting fixtures for standard class attributes, custom attributes, `@apply`, and malformed no-op cases.
- Added JSX and TSX sorting fixtures for `className` expressions, template literals, ternaries, arrays/object keys, and configured helper calls.

### Fixed

- Kept dynamic class bindings as no-ops when an earlier sortable quoted fragment is followed by an unterminated string literal.

## [0.2.7] - 2026-05-27

### Changed

- Improved Tailwind sorter failure diagnostics with the current file, resolved stylesheet/config paths, and guidance for manual path configuration or CDN-only projects.
- Added manual/auto-detected/not-found source labels to Tailwind context diagnostics and runtime reports.
- Added bounded Tailwind CDN detection for diagnostics, covering both the legacy `cdn.tailwindcss.com` URL and the Tailwind v4 `@tailwindcss/browser` CDN URL.
- Kept full Tailwind sorter diagnostics in folder dry-run failure reports instead of truncating them to the first error line.

## [0.2.6] - 2026-05-20

### Changed

- Improved `Test Trier runtime` so it reports the actual Node.js version selected by the IDE runtime, including Docker and remote runtimes.
- Documented Node.js 22 as the recommended Docker/remote runtime while keeping Node.js 20.19+ as the minimum supported version.
- Improved configured JS/TS helper handling for quoted object keys such as `cn({ "...": active })`.
- Updated the framework support roadmap to match the current regression coverage and near-term stabilization plan.

### Fixed

- Avoid surfacing a JetBrains Tailwind CSS LSP upload-root error as a Trier save failure when the document was already saved successfully.

### Tests

- Added regression coverage for nested JSX helper expressions and nested Vue dynamic class bindings.

## [0.2.5] - 2026-05-19

### Added

- Added support for Docker and remote Node.js interpreters configured through the IDE JavaScript Runtime selector.

### Changed

- Kept local Node.js runtime behavior unchanged while allowing Trier to run the bundled sorter in Docker and remote runtimes.
- Improved `Test Trier runtime` so slow runtime checks run in the background and successful results are easier to read.

### Fixed

- Show runtime test failures from invalid Docker images or missing Node.js executables in an error dialog.

## [0.2.4] - 2026-05-16

### Added

- Added Vue hardening coverage for `v-bind:class`, object class bindings, mixed array/object bindings, and multiline bindings with comments.
- Added no-op regression coverage for unsupported Svelte, Astro, Angular, and Blade/PHP class binding shapes.
- Added malformed/no-op regression coverage for broken HTML class attributes, CSS `@apply`, and Vue dynamic class bindings.
- Added custom attribute/function regression coverage across HTML, JSX/TSX, Vue, and fallback processing.
- Added folder dry-run coverage for framework extension globs including Vue, Svelte, Astro, and PHP files.

### Changed

- Made fallback dynamic attribute processing more conservative by sorting quoted class fragments inside expression values instead of rewriting the whole attribute expression.
- Rebalanced the dry-run review footer around selected-file actions on the left and global actions on the right, with report copying moved to the header controls.

### Fixed

- Ignored quoted strings inside JavaScript line and block comments while collecting dynamic class binding fragments.
- Kept malformed dynamic class bindings as no-ops when no valid quoted class fragment can be extracted.

## [0.2.3] - 2026-05-15

### Added

- Added a framework support roadmap that documents current support levels, known gaps, and stabilization priorities without duplicating JetBrains Tailwind CSS plugin language features.

### Changed

- Reworked dry-run diff review around Trier-owned diff windows with previous/next navigation, an inline `Apply` action, and predictable advancement through remaining files.
- Moved dry-run review state, diff chain selection, diff-window navigation, and batch apply results into test-covered models.

### Fixed

- Fixed dry-run bulk apply cancellation so already applied files are preserved in the partial result and removed from the remaining review list.

## [0.2.2] - 2026-05-14

### Added

- Added automatic Tailwind config and stylesheet detection when the corresponding settings fields are left blank, including Tailwind v4 CSS entrypoints that import `tailwindcss/...` modules.

### Changed

- Renamed the Marketplace display name to `Trier - Tailwind CSS Class Sorter` for clearer Tailwind CSS search relevance while keeping the in-IDE settings page short as `Trier`.
- Improved internal verification around Project View dry-run dispatch and background document sorting guard cleanup.

### Fixed

- Hardened background document sorting tests around no-op, failure, cancellation, and throwable paths so future regressions cannot leave a document stuck in Trier's execution guard.
- Reduced false-positive "document changed while Trier was sorting it" warnings after Code Reformat by delaying the reformat-triggered sort and retrying once on the latest document content.

## [0.2.1] - 2026-05-13

### Fixed

- Fixed bulk dry-run `Apply` and `Apply Selected` so large apply batches run through a background task with progress instead of repeatedly rebuilding the review UI on the EDT.
- Fixed a write-safety error when applying dry-run changes while the dry-run review dialog is open.
- Cached dry-run file icons to avoid expensive file type lookups during large review list updates.

## [0.2.0] - 2026-05-13

### Highlights

- Dry-run review is now a full apply workflow: preview changed files, open selected diffs, apply everything, or apply only selected files and directories.
- Editor, save, and reformat sorting now run in background tasks and apply results only when the document still matches the processed snapshot.
- Folder sorting is safer for real projects, with vendor/build/cache skips, binary and large-file skips, better glob handling, and cancellable scans.
- Runtime, settings, CI, release, and plugin verification checks were tightened for the 0.2.x release line.

### Added

- Added background execution for editor, save, and reformat sorting.
- Added document snapshot checks before applying background sort results.
- Added Project View dry-run previews for selected files and folders.
- Added a grouped dry-run review dialog with file type icons, flat/grouped views, selected-file diff navigation, and copied reports that track remaining changes.
- Added dry-run apply actions for all remaining changes, selected files/directories, and individual diff entries.
- Added `Tools | Sort Tailwind Classes in Folder...` for configurable folder, glob pattern, and dry-run workflows.
- Added Node worker response timeouts and restart handling for stuck helper processes.
- Added local Node.js version validation for the required Node.js 20.19+ runtime.
- Added settings validation for custom attribute and function regex patterns.
- Added pull request and main-branch CI for `check`, `verifyPlugin`, and `buildPlugin`.
- Added IntelliJ Plugin Verifier and JaCoCo coverage gates to the standard verification flow.

### Changed

- Merged the Project View file and folder actions into one `Sort Tailwind Classes` action that always previews changes before writing.
- Reworked the dry-run review button layout so `Apply` is the primary action, with `Close`, `Copy Report`, `Open Diff`, and `Apply Selected` available from the dialog footer.
- Enabled multi-selection in the dry-run review list and grouped tree, including selecting directories to operate on all changed files inside them.
- Improved dry-run diff ownership, cleanup, and apply flow so applied files disappear from the review list and diff review advances to the next remaining candidate.
- Hardened folder sorting by skipping common vendor, build, cache, binary, and large-file candidates.
- Updated the IntelliJ Platform Gradle Plugin to 2.16.0 while keeping the target platform line on 2025.2.
- Updated release documentation for the 0.2.0 release flow.
- Suppressed a harmless JVM CDS warning in Gradle test runs from the IntelliJ test framework classloader.

### Fixed

- Fixed folder glob matching for default `**/...` patterns so files directly inside the selected folder are included.
- Fixed dry-run reports for selected nested folders so they match the changes shown when sorting the same file directly.

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
