package com.example.model

enum class BuildStep(val stepNumber: Int, val title: String) {
    IDLE(0, "Ready"),
    INSPECT_ZIP(1, "Inspecting ZIP Archive"),
    VERIFY_INTEGRITY(2, "Verifying Project Integrity"),
    DOWNLOAD_PACKAGES(3, "Resolving & Downloading Packages"),
    COMPILE_RESOURCES(4, "Compiling Binary Resources (AXML)"),
    BUILD_DEX(5, "Generating Dalvik Executable (DEX)"),
    SIGN_APK(6, "Signing APK Package (v1/v2 Scheme)"),
    FINISHED(7, "Build Finished"),
    FAILED(-1, "Build Failed")
}

data class MissingPackage(
    val id: String,
    val name: String,
    val mavenCoordinate: String,
    val version: String,
    val category: String,
    val downloadUrl: String,
    val estimatedSizeBytes: Long,
    val isDownloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val localFilePath: String? = null
)

data class IntegrityCheckResult(
    val title: String,
    val passed: Boolean,
    val details: String,
    val isFatal: Boolean = false
)

data class AndroidProject(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val packageName: String,
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val minSdk: Int = 24,
    val targetSdk: Int = 34,
    val appLabel: String,
    val launcherActivity: String = "MainActivity",
    val files: Map<String, String> = emptyMap(),
    val binaryFiles: Map<String, ByteArray> = emptyMap(),
    val permissions: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val isTemplate: Boolean = false
) {
    fun getFileTree(): List<String> = (files.keys + binaryFiles.keys).sorted()
}

data class BuildResult(
    val success: Boolean,
    val apkFile: java.io.File? = null,
    val apkSizeBytes: Long = 0,
    val durationMs: Long = 0,
    val buildStepsCompleted: Int = 0,
    val errorMessage: String? = null,
    val githubReleaseUrl: String? = null
)

data class GitHubConfig(
    val owner: String = "",
    val repo: String = "",
    val token: String = "",
    val tagName: String = "v1.0.0",
    val releaseTitle: String = "APK Release",
    val releaseNotes: String = "Automated release build created by APK Builder.",
    val isDraft: Boolean = false,
    val isPrerelease: Boolean = false
)

data class GitHubReleaseResult(
    val success: Boolean,
    val htmlUrl: String? = null,
    val downloadUrl: String? = null,
    val releaseId: Long? = null,
    val tagName: String? = null,
    val errorMessage: String? = null
)
