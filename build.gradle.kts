import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.spotless)
    jacoco
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins").map {
                it.split(',').map(String::trim).filter(String::isNotEmpty)
            },
        )
        plugins(
            providers.gradleProperty("platformPlugins").map {
                it.split(',').map(String::trim).filter(String::isNotEmpty)
            },
        )
        bundledModules(
            providers.gradleProperty("platformBundledModules").map {
                it.split(',').map(String::trim).filter(String::isNotEmpty)
            },
        )
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
}

val bundledNodeDir = layout.projectDirectory.dir("bundled-node")
val bundledNodeModulesDir = bundledNodeDir.dir("node_modules")
val generatedResourcesDir = layout.buildDirectory.dir("generated-resources/main")

val prepareBundledNodeRuntime by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Installs the bundled Node runtime dependencies used by Trier."
    workingDir = bundledNodeDir.asFile
    commandLine("npm", "ci", "--omit=dev")
    inputs.file(bundledNodeDir.file("package.json"))
    inputs.file(bundledNodeDir.file("package-lock.json"))
    outputs.dir(bundledNodeModulesDir)
}

val stageBundledNodeRuntime by tasks.registering(Sync::class) {
    group = "build"
    description = "Stages the bundled Node runtime files before packaging."
    dependsOn(prepareBundledNodeRuntime)
    into(layout.buildDirectory.dir("bundled-node-stage"))
    from(bundledNodeDir) {
        include("package.json")
        include("package-lock.json")
        include("node_modules/**")
    }
}

val bundleNodeRuntime by tasks.registering(Zip::class) {
    group = "build"
    description = "Archives the bundled Node runtime into plugin resources."
    dependsOn(stageBundledNodeRuntime)
    from(layout.buildDirectory.dir("bundled-node-stage"))
    destinationDirectory.set(generatedResourcesDir)
    archiveFileName.set("node-runtime.zip")
}

sourceSets {
    main {
        resources.srcDir(generatedResourcesDir)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        description =
            """
            <p>
                Trier sorts Tailwind CSS classes in JetBrains IDEs without handing the whole file to Prettier.
                It uses the official Tailwind Labs sorter from <code>prettier-plugin-tailwindcss</code>, preserves
                surrounding code style, and fits into normal IDE workflows for editor work, save hooks, reformat hooks,
                Project View actions, folder scans, dry-run reviews, diffs, and selective apply.
            </p>
            <p><strong>Why Trier</strong></p>
            <ul>
                <li>Focused Tailwind class sorting without changing indentation, quotes, wrapping, semicolons, or
                unrelated code style.</li>
                <li>Official Tailwind class order from <code>prettier-plugin-tailwindcss/sorter</code>.</li>
                <li>Bundled Node-side sorting runtime, so your project does not need local <code>prettier</code>
                dependencies.</li>
                <li>IDE-native workflow for manual sorting, selected ranges, save hooks, reformat hooks, files, and
                folders.</li>
                <li>Safe bulk cleanup with dry-run reports, grouped changed-file review, JetBrains diffs, and apply-all
                or apply-selected actions.</li>
            </ul>
            <p><strong>Core features</strong></p>
            <ul>
                <li>Sort the current editor, a selected plain class list, or class candidates inside the selected
                range.</li>
                <li>Run automatically on Save or after the IDE's Reformat Code action.</li>
                <li>Preview selected files and folders from the Project View context menu before writing changes.</li>
                <li>Scan folders with configurable glob patterns in a cancellable background task.</li>
                <li>Review bulk changes with grouped or flat dry-run views, selected diffs, copied reports, and
                selective apply.</li>
            </ul>
            <p><strong>Supported patterns</strong></p>
            <ul>
                <li>HTML/XML <code>class</code> attributes and JSX/TSX <code>className</code>.</li>
                <li>JSX/TSX string literals, template literals, ternaries, arrays, and object keys containing class
                fragments.</li>
                <li>Vue SFC template classes, dynamic <code>:class</code> bindings,
                <code>&lt;script setup&gt;</code> helper calls, and <code>&lt;style&gt;</code>
                <code>@apply</code>.</li>
                <li>CSS/SCSS <code>@apply</code>.</li>
                <li>Custom attributes and custom class helper functions such as <code>cn</code>, <code>clsx</code>,
                or tagged template helpers when configured.</li>
            </ul>
            <p><strong>Configuration</strong></p>
            <ul>
                <li>Uses the IDE JavaScript Runtime selector for Node.js.</li>
                <li>Requires a local Node.js 20.19+ runtime.</li>
                <li>Supports Tailwind config and stylesheet paths.</li>
                <li>Supports preserve whitespace, preserve duplicates, custom attributes, and custom functions.</li>
                <li>Includes a runtime test button that validates Node.js, bundled runtime extraction, helper startup,
                and a real sample sort.</li>
            </ul>
            <p>
                Trier is designed for teams that want reliable Tailwind ordering across HTML, JSX, TSX, Vue, CSS, and
                related frontend files without giving up their existing IDE formatting workflow.
            </p>
            """.trimIndent()
        changeNotes =
            """
            <p><strong>Dry-run review, selective apply, and safer background sorting.</strong></p>
            <p>
                Trier 0.2.0 turns folder and Project View sorting into a safer preview-first workflow. You can inspect
                changed files, open selected diffs, apply everything, or apply only selected files and directories
                directly from the dry-run review dialog.
            </p>
            <ul>
                <li>Reworked Project View sorting into one preview-first <em>Sort Tailwind Classes</em> action for
                selected files and folders.</li>
                <li>Added grouped and flat dry-run review views with file icons, multi-selection, selected diffs,
                copied reports, apply-all, apply-selected, and individual diff apply.</li>
                <li>Added <em>Tools | Sort Tailwind Classes in Folder...</em> for configurable folder, glob-pattern,
                and dry-run workflows.</li>
                <li>Moved editor, save, and reformat sorting to background tasks and apply results only when the
                document still matches the processed snapshot.</li>
                <li>Hardened folder scans by skipping common vendor/build/cache directories, binary files, and large
                files.</li>
                <li>Fixed default <code>**/...</code> glob handling so files directly inside the selected folder are
                included in dry-run reports.</li>
                <li>Added Node.js 20.19+ validation, custom regex validation, worker timeouts, and worker restart
                handling.</li>
                <li>Added pull request and branch CI with checks, plugin verification, plugin builds, and JaCoCo
                coverage gates.</li>
            </ul>
            """.trimIndent()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            current()
        }
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

val jacocoClassExcludes =
    listOf(
        "**/TrierDryRunDiffDialog*.class",
        "**/TrierDryRunReportDialog.class",
        "**/TrierDryRunReportDialog\$*.class",
        "**/DiffListCellRenderer.class",
        "**/DiffTreeCellRenderer.class",
    )

tasks {
    withType<BuildSearchableOptionsTask>().configureEach {
        // The headless IDE uses PathClassLoader, which makes HotSpot print a CDS warning by default.
        jvmArgs("-Xshare:off")
    }

    test {
        useJUnit()
        // IntelliJ test framework uses PathClassLoader, which makes HotSpot print a CDS warning by default.
        jvmArgs("-Xshare:off")
        finalizedBy(jacocoTestReport)

        extensions.configure<JacocoTaskExtension> {
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
    }

    jacocoTestReport {
        dependsOn(test)

        classDirectories.setFrom(
            fileTree(layout.buildDirectory.dir("instrumented/instrumentCode")) {
                exclude(jacocoClassExcludes)
            },
        )

        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    jacocoTestCoverageVerification {
        dependsOn(test)

        classDirectories.setFrom(
            fileTree(layout.buildDirectory.dir("instrumented/instrumentCode")) {
                exclude(jacocoClassExcludes)
            },
        )

        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.70".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = "0.50".toBigDecimal()
                }
            }
        }
    }

    check {
        dependsOn(spotlessCheck)
        dependsOn(jacocoTestCoverageVerification)
    }

    processResources {
        dependsOn(bundleNodeRuntime)
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
