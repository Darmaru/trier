import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.spotless)
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
                Trier keeps Tailwind CSS class lists consistently ordered inside JetBrains IDEs without running full Prettier formatting.
                It uses the Tailwind sorting logic from <code>prettier-plugin-tailwindcss</code>, but applies only class sorting so your existing code style stays intact.
            </p>
            <p><strong>Core features</strong></p>
            <ul>
                <li>Sort Tailwind classes in the current file or selected editor fragment.</li>
                <li>Run automatically on save or together with the IDE's Reformat Code action.</li>
                <li>Sort a selected file or a whole directory from the Project tool window.</li>
                <li>Process folders with configurable glob patterns.</li>
                <li>Preview folder-wide changes with dry-run reports and JetBrains diff viewer integration.</li>
            </ul>
            <p><strong>Configuration</strong></p>
            <ul>
                <li>Uses the IDE JavaScript runtime selection UI for Node.js.</li>
                <li>Requires a local Node.js 20.19+ runtime.</li>
                <li>Supports Tailwind config and stylesheet paths.</li>
                <li>Bundles the Node-side sorting runtime with the plugin, so project-local dependencies are not required.</li>
            </ul>
            <p>
                Trier is designed for teams that want reliable Tailwind class ordering while preserving framework-specific formatting in HTML, JSX, Vue, and related frontend files.
            </p>
            """.trimIndent()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
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

tasks {
    test {
        useJUnit()
    }

    check {
        dependsOn(spotlessCheck)
    }

    processResources {
        dependsOn(bundleNodeRuntime)
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
