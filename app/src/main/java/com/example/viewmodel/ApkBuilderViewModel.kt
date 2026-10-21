package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.ApkBuilderEngine
import com.example.engine.IntegrityReport
import com.example.engine.PackageManagerEngine
import com.example.engine.ProjectVerifier
import com.example.engine.ProjectZipManager
import com.example.model.AndroidProject
import com.example.model.BuildResult
import com.example.model.BuildStep
import com.example.model.LogLevel
import com.example.model.TerminalLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ApkBuilderViewModel(
    private val packageManagerEngine: PackageManagerEngine,
    private val apkBuilderEngine: ApkBuilderEngine
) : ViewModel() {

    private val templatesList = ProjectZipManager.getBuiltInTemplates()
    val availableTemplates: List<AndroidProject> = templatesList

    private val _currentProject = MutableStateFlow<AndroidProject>(templatesList.first())
    val currentProject: StateFlow<AndroidProject> = _currentProject.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<TerminalLog>>(emptyList())
    val terminalLogs: StateFlow<List<TerminalLog>> = _terminalLogs.asStateFlow()

    private val _buildStep = MutableStateFlow<BuildStep>(BuildStep.IDLE)
    val buildStep: StateFlow<BuildStep> = _buildStep.asStateFlow()

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    private val _latestBuildResult = MutableStateFlow<BuildResult?>(null)
    val latestBuildResult: StateFlow<BuildResult?> = _latestBuildResult.asStateFlow()

    private val _integrityReport = MutableStateFlow<IntegrityReport?>(null)
    val integrityReport: StateFlow<IntegrityReport?> = _integrityReport.asStateFlow()

    private val _cachedPackageIds = MutableStateFlow<Set<String>>(emptySet())
    val cachedPackageIds: StateFlow<Set<String>> = _cachedPackageIds.asStateFlow()

    private val _cachedTotalBytes = MutableStateFlow<Long>(0L)
    val cachedTotalBytes: StateFlow<Long> = _cachedTotalBytes.asStateFlow()

    private val _hasInstallPermission = MutableStateFlow(false)
    val hasInstallPermission: StateFlow<Boolean> = _hasInstallPermission.asStateFlow()

    init {
        refreshCacheInfo()
        addLog("APK Forge Engine v2.4 initialized.", LogLevel.SYSTEM, "INIT")
        addLog("Loaded default template: ${_currentProject.value.name}")
        addLog("Type 'help' in terminal or tap 'Build APK' to compile.")
        runIntegrityVerification()
    }

    fun refreshCacheInfo() {
        _cachedPackageIds.value = packageManagerEngine.getCachedPackageIds()
        _cachedTotalBytes.value = packageManagerEngine.getTotalCacheSizeBytes()
    }

    fun checkInstallPermission(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
        _hasInstallPermission.value = granted
        return granted
    }

    fun requestInstallPermissionIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            null
        }
    }

    fun selectProject(project: AndroidProject) {
        _currentProject.value = project
        _latestBuildResult.value = null
        addLog("Switched active project: ${project.name} (${project.packageName})", LogLevel.INFO, "PROJECT")
        runIntegrityVerification()
    }

    fun loadUploadedCodeDexZip(context: Context) {
        viewModelScope.launch {
            try {
                addLog("Loading attached project archive 'CodeDexLocalAI-Android-Projekt-v2.zip'...", LogLevel.COMMAND, "IMPORT")
                val proj = ProjectZipManager.loadProjectFromAsset(context, "projects/CodeDexLocalAI.zip", "CodeDex Local AI")
                if (proj != null) {
                    _currentProject.value = proj
                    _latestBuildResult.value = null
                    addLog("✓ Successfully unpacked ZIP: ${proj.name} (${proj.packageName})", LogLevel.SUCCESS, "IMPORT")
                    addLog("Detected ${proj.files.size} files, launcher: ${proj.launcherActivity}, version: ${proj.versionName}")
                    runIntegrityVerification()
                } else {
                    addLog("Could not open bundled asset projects/CodeDexLocalAI.zip", LogLevel.ERROR, "IMPORT")
                }
            } catch (e: Exception) {
                addLog("Error loading project: ${e.message}", LogLevel.ERROR, "IMPORT")
            }
        }
    }

    fun importProjectFromZip(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inferredName = getFileNameFromUri(context, uri)
                    ?.removeSuffix(".zip")
                    ?.removeSuffix(".ZIP")
                    ?: "ImportedProject"

                addLog("Importing project archive ($inferredName) from: $uri...", LogLevel.COMMAND, "IMPORT")
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val proj = ProjectZipManager.extractProjectFromZip(stream, inferredName)
                    _currentProject.value = proj
                    _latestBuildResult.value = null
                    addLog("✓ Successfully unpacked ZIP: ${proj.name} (${proj.packageName})", LogLevel.SUCCESS, "IMPORT")
                    addLog("Found ${proj.files.size} project files, launcher: ${proj.launcherActivity}, label: ${proj.appLabel}")
                    runIntegrityVerification()
                } ?: run {
                    addLog("Failed to open input stream for selected ZIP.", LogLevel.ERROR, "IMPORT")
                }
            } catch (e: Exception) {
                addLog("Error importing ZIP: ${e.message}", LogLevel.ERROR, "IMPORT")
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            var name: String? = null
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) name = it.getString(nameIndex)
                    }
                }
            }
            name ?: uri.lastPathSegment
        } catch (_: Exception) {
            null
        }
    }

    fun runIntegrityVerification() {
        viewModelScope.launch {
            val proj = _currentProject.value
            addLog("Verifying integrity of '${proj.name}'...", LogLevel.COMMAND, "VERIFY")
            refreshCacheInfo()
            val report = ProjectVerifier.verify(proj, _cachedPackageIds.value)
            _integrityReport.value = report

            for (check in report.checks) {
                val level = if (check.passed) LogLevel.SUCCESS else if (check.isFatal) LogLevel.ERROR else LogLevel.WARNING
                addLog("  [${if (check.passed) "OK" else if (check.isFatal) "FAIL" else "WARN"}] ${check.title}: ${check.details}", level, "VERIFY")
            }

            if (report.missingPackages.isNotEmpty()) {
                addLog("⚠ ${report.missingPackages.size} required packages missing from local toolchain cache:", LogLevel.WARNING, "VERIFY")
                for (pkg in report.missingPackages) {
                    addLog("  - ${pkg.name} (${pkg.category})", LogLevel.WARNING, "PKG")
                }
            } else {
                addLog("✓ Project integrity verified. All required packages ready.", LogLevel.SUCCESS, "VERIFY")
            }
        }
    }

    fun downloadMissingPackages() {
        viewModelScope.launch {
            val report = _integrityReport.value ?: return@launch
            if (report.missingPackages.isEmpty()) {
                addLog("All packages are already up-to-date and cached.", LogLevel.SUCCESS, "PKG")
                return@launch
            }
            addLog("Downloading missing toolchain dependencies...", LogLevel.COMMAND, "PKG")
            for (pkg in report.missingPackages) {
                packageManagerEngine.downloadPackage(pkg) { prog, msg ->
                    addLog(msg, LogLevel.INFO, "DL", prog)
                }
            }
            refreshCacheInfo()
            runIntegrityVerification()
        }
    }

    fun startBuild() {
        if (_isBuilding.value) return
        viewModelScope.launch {
            _isBuilding.value = true
            _latestBuildResult.value = null

            val result = apkBuilderEngine.buildProject(
                project = _currentProject.value,
                onStepChanged = { _buildStep.value = it },
                onLog = { addLog(it.message, it.level, it.tag, it.progress) }
            )

            _latestBuildResult.value = result
            _isBuilding.value = false
            refreshCacheInfo()
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!checkInstallPermission(context)) {
                addLog("Permission to install unknown apps is required by Android OS.", LogLevel.WARNING, "INSTALL")
                return
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            addLog("Dispatched installation intent to Android Package Installer.", LogLevel.SUCCESS, "INSTALL")
        } catch (e: Exception) {
            addLog("Could not trigger installation: ${e.message}", LogLevel.ERROR, "INSTALL")
        }
    }

    fun shareApk(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Built APK: ${apkFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share APK via..."))
            addLog("Shared APK via system chooser.", LogLevel.INFO, "SHARE")
        } catch (e: Exception) {
            addLog("Share failed: ${e.message}", LogLevel.ERROR, "SHARE")
        }
    }

    fun exportApkToUri(context: Context, apkFile: File, targetUri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    apkFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                addLog("✓ Successfully exported APK to storage URI: $targetUri", LogLevel.SUCCESS, "EXPORT")
            } catch (e: Exception) {
                addLog("Export failed: ${e.message}", LogLevel.ERROR, "EXPORT")
            }
        }
    }

    fun executeTerminalCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        addLog("$ $trimmed", LogLevel.COMMAND, "USER")

        val parts = trimmed.split("\\s+".toRegex())
        val action = parts.first().lowercase()

        when (action) {
            "help" -> {
                addLog("Available commands:")
                addLog("  build          - Full build pipeline (verify, resolve, compile, sign)")
                addLog("  verify / check - Run project integrity & missing packages check")
                addLog("  download       - Fetch missing SDK packages and dependencies")
                addLog("  info           - Show current project details and metadata")
                addLog("  files          - List all files in current project")
                addLog("  cache          - Show cached toolchain packages & storage usage")
                addLog("  clean          - Clear package cache and previous APK builds")
                addLog("  clear          - Clear terminal logs")
            }
            "build" -> startBuild()
            "verify", "check" -> runIntegrityVerification()
            "download" -> downloadMissingPackages()
            "info" -> {
                val p = _currentProject.value
                addLog("Active Project: ${p.name}")
                addLog("Package: ${p.packageName}")
                addLog("Version: ${p.versionName} (Code: ${p.versionCode})")
                addLog("SDK Range: minSdk ${p.minSdk} -> targetSdk ${p.targetSdk}")
                addLog("Launcher Activity: ${p.launcherActivity}")
                addLog("Permissions: ${p.permissions.joinToString().ifEmpty { "None" }}")
                addLog("Dependencies: ${p.dependencies.joinToString().ifEmpty { "None" }}")
            }
            "files" -> {
                val p = _currentProject.value
                addLog("Project Files (${p.files.size + p.binaryFiles.size} total):")
                p.getFileTree().forEach { addLog("  • $it") }
            }
            "cache" -> {
                refreshCacheInfo()
                addLog("Cached packages: ${_cachedPackageIds.value.size}")
                _cachedPackageIds.value.forEach { addLog("  • $it") }
                addLog("Total cache size: ${_cachedTotalBytes.value / 1024} KB")
            }
            "clean" -> {
                packageManagerEngine.clearCache()
                refreshCacheInfo()
                addLog("Package cache cleared.", LogLevel.SUCCESS, "CLEAN")
                runIntegrityVerification()
            }
            "clear" -> clearTerminal()
            else -> {
                addLog("Command not recognized: '$trimmed'. Type 'help' for commands.", LogLevel.WARNING, "CLI")
            }
        }
    }

    fun clearTerminal() {
        _terminalLogs.value = emptyList()
    }

    private fun addLog(
        msg: String,
        level: LogLevel = LogLevel.INFO,
        tag: String = "BUILD",
        progress: Float? = null
    ) {
        val newLog = TerminalLog(
            level = level,
            tag = tag,
            message = msg,
            progress = progress
        )
        _terminalLogs.value = _terminalLogs.value + newLog
    }
}
