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

val useRecommendedPluginVerifier =
    providers.gradleProperty("pluginVerifierRecommended").isPresent ||
        gradle.startParameter.taskNames.any { taskName ->
            taskName == "verifyPluginRecommended" || taskName.endsWith(":verifyPluginRecommended")
        }

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        description =
            """
            <p>
                Trier keeps Tailwind CSS classes in a consistent order directly inside JetBrains IDEs. It uses the
                official Tailwind Labs sorter, preserves your existing formatting, and gives you safe editor, Project
                View, folder, dry-run, diff, and selective apply workflows without handing whole files to Prettier.
            </p>
            <p><strong>Why Trier</strong></p>
            <ul>
                <li>Sort Tailwind class lists without changing indentation, quotes, semicolons, wrapping, or unrelated code style.</li>
                <li>Use the official Tailwind ordering from <code>prettier-plugin-tailwindcss</code>.</li>
                <li>Review folder-wide changes before writing files with dry-run reports and JetBrains diffs.</li>
                <li>Apply one file, selected files, selected directories, or all remaining dry-run changes.</li>
                <li>Run manually, from Project View, on Save, or after the IDE Reformat Code action.</li>
            </ul>
            <p><strong>Framework coverage</strong></p>
            <ul>
                <li>HTML, XML, JSX, TSX, CSS, and SCSS.</li>
                <li>Vue, Svelte, Astro, Angular, and Laravel Blade / PHP.</li>
                <li>Static classes, dynamic class bindings, helper calls, component class props, and <code>@apply</code>.</li>
                <li>Custom attributes and helper functions such as <code>data-classes</code>, <code>cn</code>, <code>clsx</code>, or <code>tw</code>.</li>
            </ul>
            <p><strong>Safe by default</strong></p>
            <ul>
                <li>Unsupported or ambiguous framework syntax is preserved as no-op instead of being rewritten aggressively.</li>
                <li>Manual Tailwind stylesheet/config paths always win, while blank settings are auto-detected per project.</li>
                <li>The bundled sorter runtime means your project does not need local <code>prettier</code> dependencies.</li>
            </ul>
            <p>
                Trier is designed for projects where Tailwind class lists live everywhere: templates, components,
                helper calls, style blocks, backend views, and framework-specific bindings.
            </p>
            """.trimIndent()
        changeNotes =
            """
            <p><strong>Angular and Blade/PHP support promotion.</strong></p>
            <ul>
                <li>Promotes Angular support to Supported for static classes, <code>ngClass</code>, <code>[class]</code>, <code>[ngClass]</code>, and inline component templates.</li>
                <li>Promotes Laravel Blade / PHP support to Supported for static classes, Blade component attributes, and Blade <code>@class(...)</code> quoted fragments.</li>
                <li>Keeps documented unsupported Angular and Blade/PHP syntax as no-op instead of attempting risky rewrites.</li>
                <li>Makes Trier settings project-aware so runtime tests and Tailwind path auto-detection use the active project context in multi-project IDE windows.</li>
                <li>Refreshes the Marketplace, README, and framework support documentation around Trier's current supported workflows.</li>
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
            if (useRecommendedPluginVerifier) {
                recommended()
            } else {
                current()
            }
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
    register("verifyPluginRecommended") {
        group = "verification"
        description = "Runs plugin verifier against JetBrains recommended IDE versions."
        dependsOn("verifyPlugin")
    }

    withType<BuildSearchableOptionsTask>().configureEach {
        // The headless IDE can initialize Settings pages outside this plugin and attempt Marketplace requests.
        // Searchable options are not required for Trier runtime or publishing, so skip this flaky UI indexing task.
        enabled = false
        // The headless IDE uses PathClassLoader, which makes HotSpot print a CDS warning by default.
        jvmArgs("-Xshare:off")
    }
    named("prepareJarSearchableOptions") {
        enabled = false
    }
    named("jarSearchableOptions") {
        enabled = false
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
