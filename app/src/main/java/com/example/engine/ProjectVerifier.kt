package com.example.engine

import com.example.model.AndroidProject
import com.example.model.IntegrityCheckResult
import com.example.model.MissingPackage

data class IntegrityReport(
    val checks: List<IntegrityCheckResult>,
    val missingPackages: List<MissingPackage>,
    val isReadyToBuild: Boolean
)

object ProjectVerifier {

    fun verify(project: AndroidProject, localCachedPackageIds: Set<String>): IntegrityReport {
        val checks = mutableListOf<IntegrityCheckResult>()
        val missing = mutableListOf<MissingPackage>()

        // 1. AndroidManifest.xml check
        val manifestEntry = project.files.entries.find { it.key.endsWith("AndroidManifest.xml") }
        if (manifestEntry == null) {
            checks.add(
                IntegrityCheckResult(
                    title = "AndroidManifest.xml",
                    passed = false,
                    details = "FATAL: AndroidManifest.xml is missing from the project structure.",
                    isFatal = true
                )
            )
        } else {
            val manifest = manifestEntry.value
            val hasPackage = manifest.contains("package=") || project.packageName.isNotBlank()
            val hasApplication = manifest.contains("<application")
            val hasLauncher = manifest.contains("android.intent.action.MAIN")

            if (hasPackage && hasApplication && hasLauncher) {
                checks.add(
                    IntegrityCheckResult(
                        title = "AndroidManifest.xml",
                        passed = true,
                        details = "Valid Android Manifest. Package: ${project.packageName}, Launcher: ${project.launcherActivity}"
                    )
                )
            } else {
                val issues = mutableListOf<String>()
                if (!hasPackage) issues.add("Missing package attribute")
                if (!hasApplication) issues.add("Missing <application> block")
                if (!hasLauncher) issues.add("No launcher activity intent filter found")

                checks.add(
                    IntegrityCheckResult(
                        title = "AndroidManifest.xml Validation",
                        passed = false,
                        details = "Manifest warnings: ${issues.joinToString(", ")}",
                        isFatal = !hasApplication
                    )
                )
            }
        }

        // 2. Resource Directory & Strings check
        val stringsEntry = project.files.entries.find { it.key.endsWith("strings.xml") }
        val resFiles = project.files.keys.filter { it.contains("/res/") } + project.binaryFiles.keys.filter { it.contains("/res/") }
        if (resFiles.isEmpty()) {
            checks.add(
                IntegrityCheckResult(
                    title = "Resource Directory (res/)",
                    passed = false,
                    details = "Warning: No resources found in res/. Default resources will be generated.",
                    isFatal = false
                )
            )
        } else {
            checks.add(
                IntegrityCheckResult(
                    title = "Resource Directory (res/)",
                    passed = true,
                    details = "Found ${resFiles.size} resource files. strings.xml: ${if (stringsEntry != null) "Present" else "Missing (fallback used)"}"
                )
            )
        }

        // 3. Source Code Files (Java / Kotlin)
        val sourceFiles = project.files.keys.filter { it.endsWith(".kt") || it.endsWith(".java") }
        if (sourceFiles.isEmpty()) {
            checks.add(
                IntegrityCheckResult(
                    title = "Source Code (Java/Kotlin)",
                    passed = false,
                    details = "No source files detected in project archive.",
                    isFatal = true
                )
            )
        } else {
            checks.add(
                IntegrityCheckResult(
                    title = "Source Code (Java/Kotlin)",
                    passed = true,
                    details = "Found ${sourceFiles.size} source file(s) (${sourceFiles.take(3).joinToString { it.substringAfterLast('/') }})"
                )
            )
        }

        // 4. Build Configuration & Gradle
        val gradleFiles = project.files.keys.filter { it.contains("build.gradle") }
        if (gradleFiles.isEmpty()) {
            checks.add(
                IntegrityCheckResult(
                    title = "Gradle Configuration",
                    passed = false,
                    details = "No build.gradle found. Generic Android SDK 34 toolchain defaults applied.",
                    isFatal = false
                )
            )
        } else {
            checks.add(
                IntegrityCheckResult(
                    title = "Gradle Configuration",
                    passed = true,
                    details = "Found build script: ${gradleFiles.first().substringAfterLast('/')}"
                )
            )
        }

        // 5. Dependency & Toolchain resolution
        // Check core Android SDK platform jar
        val sdkPackageId = "android_sdk_platform_34"
        if (!localCachedPackageIds.contains(sdkPackageId)) {
            missing.add(
                MissingPackage(
                    id = sdkPackageId,
                    name = "Android SDK Platform 34 (android.jar)",
                    mavenCoordinate = "com.google.android:android-sdk:34",
                    version = "34.0.0",
                    category = "SDK Toolchain",
                    downloadUrl = "https://dl.google.com/android/repository/platform-34_r02.zip",
                    estimatedSizeBytes = 35_400_000L
                )
            )
        }

        // Check required libraries from project dependencies
        for (dep in project.dependencies) {
            val depId = dep.replace(":", "_").replace(".", "_")
            if (!localCachedPackageIds.contains(depId)) {
                val parts = dep.split(":")
                val group = parts.getOrNull(0) ?: "unknown"
                val name = parts.getOrNull(1) ?: "library"
                val version = parts.getOrNull(2) ?: "1.0.0"

                missing.add(
                    MissingPackage(
                        id = depId,
                        name = "$name-$version",
                        mavenCoordinate = dep,
                        version = version,
                        category = "AndroidX Library",
                        downloadUrl = "https://maven.google.com/${group.replace('.', '/')}/$name/$version/$name-$version.aar",
                        estimatedSizeBytes = estimatePackageSize(name)
                    )
                )
            }
        }

        // If no dependencies listed, provide default core packages if needed
        if (project.dependencies.isEmpty()) {
            val coreKtxId = "androidx_core_core_ktx_1_12_0"
            if (!localCachedPackageIds.contains(coreKtxId)) {
                missing.add(
                    MissingPackage(
                        id = coreKtxId,
                        name = "androidx.core:core-ktx:1.12.0",
                        mavenCoordinate = "androidx.core:core-ktx:1.12.0",
                        version = "1.12.0",
                        category = "Core Runtime",
                        downloadUrl = "https://maven.google.com/androidx/core/core-ktx/1.12.0/core-ktx-1.12.0.aar",
                        estimatedSizeBytes = 184_000L
                    )
                )
            }
        }

        val hasFatalCheck = checks.any { it.isFatal && !it.passed }
        val allPackagesPresent = missing.isEmpty()

        return IntegrityReport(
            checks = checks,
            missingPackages = missing,
            isReadyToBuild = !hasFatalCheck && allPackagesPresent
        )
    }

    private fun estimatePackageSize(name: String): Long {
        return when {
            name.contains("material") -> 2_450_000L
            name.contains("appcompat") -> 1_200_000L
            name.contains("webkit") -> 450_000L
            name.contains("compose") -> 3_800_000L
            else -> 650_000L
        }
    }
}
