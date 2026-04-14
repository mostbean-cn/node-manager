import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.serialization)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Ktor HTTP Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // Kotlinx Serialization
    implementation(libs.serialization.json)

    // Test
    testImplementation(libs.junit)

    // IntelliJ Platform
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))

        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description = """
            <h2>Node Manager</h2>
            <p>Manage Node.js versions directly within IntelliJ Platform-based IDEs.</p>

            <h3>Features</h3>
            <ul>
                <li>View, install, switch, and uninstall Node.js versions</li>
                <li>Support for <b>nvm</b> and <b>fnm</b> (auto-detection)</li>
                <li>Manager dashboard with status monitoring and conflict detection</li>
                <li>Status bar widget showing the active Node.js version</li>
                <li>Searchable version list from the official Node.js registry</li>
                <li>Configurable mirror sources for faster downloads</li>
            </ul>

            <h3>Requirements</h3>
            <p>At least one version manager must be installed on your system:</p>
            <ul>
                <li><a href="https://github.com/nvm-sh/nvm">nvm</a> (MIT License) — Mac / Linux</li>
                <li><a href="https://github.com/coreybutler/nvm-windows">nvm-windows</a> (MIT License) — Windows</li>
                <li><a href="https://github.com/Schniz/fnm">fnm</a> (GPL-3.0 License) — Cross-platform</li>
            </ul>

            <h3>Disclaimer</h3>
            <p>This plugin does not bundle, include, or redistribute any third-party software.
               It integrates with version managers already installed on the user's system.
               "Node.js" is a trademark of the OpenJS Foundation.
               All other trademarks are the property of their respective owners.</p>

            <h3>Privacy</h3>
            <p>This plugin does not collect any user data or telemetry.
               The only network request is fetching the public Node.js version list
               from <code>nodejs.org</code> or the configured mirror.</p>
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
