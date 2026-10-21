package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AndroidProject
import com.example.model.BuildStep
import com.example.ui.components.InstallPermissionCard
import com.example.ui.components.StepProgressIndicator
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberError
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.CyberPrimaryContainer
import com.example.ui.theme.CyberSecondary
import com.example.ui.theme.CyberSecondaryContainer
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberWarning
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.OnSurfaceWhite
import com.example.ui.theme.TerminalDim
import com.example.viewmodel.ApkBuilderViewModel

@Composable
fun HomeScreen(
    viewModel: ApkBuilderViewModel,
    onNavigateToTerminal: () -> Unit,
    onNavigateToExplorer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentProject by viewModel.currentProject.collectAsState()
    val buildStep by viewModel.buildStep.collectAsState()
    val isBuilding by viewModel.isBuilding.collectAsState()
    val latestBuild by viewModel.latestBuildResult.collectAsState()
    val integrityReport by viewModel.integrityReport.collectAsState()
    val hasInstallPermission by viewModel.hasInstallPermission.collectAsState()

    // SAF Document picker for ZIP project files
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importProjectFromZip(context, it) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // App Banner Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = "APK Builder",
                            tint = CyberPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "APK BUILDER",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = OnSurfaceWhite,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "On-Device Compiler & Packager",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberPrimary
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.runIntegrityVerification() },
                    modifier = Modifier.testTag("refresh_integrity_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Verification",
                        tint = OnSurfaceDim
                    )
                }
            }
        }

        // Project Quick Templates & Import Action
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROJECT ARCHIVES & TEMPLATES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDim,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Import ZIP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberPrimary,
                        modifier = Modifier
                            .clickable {
                                zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                            }
                            .padding(4.dp)
                            .testTag("import_zip_top_btn")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Import Custom ZIP button
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberSurfaceVariant)
                                .border(1.dp, CyberPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("import_zip_card"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Load Project ZIP",
                                    color = CyberPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Built-in Templates
                    items(viewModel.availableTemplates) { template ->
                        val isSelected = currentProject.id == template.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) CyberPrimaryContainer else CyberSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) CyberPrimary else CyberCardBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.selectProject(template) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("template_card_${template.id}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) CyberPrimary else TerminalDim)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = template.name,
                                        color = if (isSelected) OnSurfaceWhite else OnSurfaceDim,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = template.packageName,
                                        color = if (isSelected) CyberPrimary else TerminalDim,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Uploaded Project Archive 1-Tap Load
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .testTag("uploaded_project_card"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "CodeDex Local AI",
                                    color = OnSurfaceWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Uploaded Project Archive (dev.codedex.localai)",
                                    color = CyberPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.loadUploadedCodeDexZip(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("load_codedex_zip_btn")
                        ) {
                            Text(
                                text = "Load ZIP",
                                color = CyberBg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Active Project Summary Card
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentProject.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceWhite
                                )
                                if (currentProject.isTemplate) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TEMPLATE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberPrimary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(CyberPrimaryContainer)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentProject.packageName,
                                fontSize = 12.sp,
                                color = CyberPrimary
                            )
                        }

                        // Project Files Button
                        OutlinedButton(
                            onClick = onNavigateToExplorer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("inspect_files_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Files", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Metadata row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoPill(label = "Version", value = "${currentProject.versionName} (${currentProject.versionCode})")
                        InfoPill(label = "SDK Range", value = "${currentProject.minSdk} - ${currentProject.targetSdk}")
                        InfoPill(label = "Files", value = "${currentProject.files.size + currentProject.binaryFiles.size}")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Integrity Status Box
                    integrityReport?.let { report ->
                        val hasMissing = report.missingPackages.isNotEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (hasMissing) CyberSurfaceVariant else CyberSecondaryContainer.copy(alpha = 0.5f))
                                .border(
                                    1.dp,
                                    if (hasMissing) CyberWarning.copy(alpha = 0.6f) else CyberSecondary.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasMissing) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (hasMissing) CyberWarning else CyberSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasMissing)
                                        "${report.missingPackages.size} Missing Package(s) Detected"
                                    else
                                        "Project Integrity Verified",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasMissing) CyberWarning else CyberSecondary
                                )
                                Text(
                                    text = if (hasMissing)
                                        "Terminal will download required packages prior to compilation."
                                    else
                                        "All manifests, assets, and toolchain dependencies ready.",
                                    fontSize = 11.sp,
                                    color = OnSurfaceDim
                                )
                            }

                            if (hasMissing) {
                                FilledTonalButton(
                                    onClick = { viewModel.downloadMissingPackages() },
                                    modifier = Modifier.testTag("download_missing_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Build Pipeline Indicator
        item {
            StepProgressIndicator(
                currentStep = buildStep,
                isBuilding = isBuilding
            )
        }

        // Unknown Apps Installation Permission Card
        item {
            InstallPermissionCard(
                hasPermission = hasInstallPermission,
                onCheckPermission = { viewModel.checkInstallPermission(context) }
            )
        }

        // Main Build Action Button
        item {
            Button(
                onClick = { viewModel.startBuild() },
                enabled = !isBuilding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberPrimary,
                    disabledContainerColor = CyberSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("build_apk_primary_btn")
            ) {
                if (isBuilding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = CyberPrimary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "BUILDING APK (${buildStep.title})...",
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceWhite,
                        fontSize = 14.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = CyberBg
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "BUILD INSTALLABLE APK",
                        fontWeight = FontWeight.Black,
                        color = CyberBg,
                        letterSpacing = 0.5.sp,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Latest Build Success Action Card
        latestBuild?.takeIf { it.success && it.apkFile != null }?.let { result ->
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberSecondary, RoundedCornerShape(12.dp))
                        .testTag("latest_build_card"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSecondaryContainer.copy(alpha = 0.25f))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CyberSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ready to Install",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberSecondary
                                )
                            }

                            Text(
                                text = "${result.apkSizeBytes / 1024} KB",
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceWhite,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = result.apkFile?.name ?: "app.apk",
                            fontSize = 12.sp,
                            color = OnSurfaceDim
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    result.apkFile?.let { viewModel.installApk(context, it) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("install_latest_apk_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = CyberBg
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "INSTALL APK",
                                    fontWeight = FontWeight.Bold,
                                    color = CyberBg,
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    result.apkFile?.let { viewModel.shareApk(context, it) }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_latest_apk_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Live Terminal Quick Peek Button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberSurfaceVariant)
                    .clickable { onNavigateToTerminal() }
                    .padding(14.dp)
                    .testTag("view_terminal_banner_btn")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = CyberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Open Live Build Terminal",
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceWhite,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Stream compiler logs, inspect missing packages, and run commands.",
                                fontSize = 11.sp,
                                color = OnSurfaceDim
                            )
                        }
                    }

                    Text(
                        text = "OPEN →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CyberSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = OnSurfaceDim
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceWhite
        )
    }
}
