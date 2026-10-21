package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GitHubConfig
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
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDim
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalText
import com.example.viewmodel.ApkBuilderViewModel

@Composable
fun GitHubScreen(
    viewModel: ApkBuilderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestBuild by viewModel.latestBuildResult.collectAsState()
    val gitHubConfig by viewModel.gitHubConfig.collectAsState()
    val isUploading by viewModel.isUploadingToGitHub.collectAsState()
    val releaseResult by viewModel.gitHubReleaseResult.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()

    var owner by remember(gitHubConfig) { mutableStateOf(gitHubConfig.owner) }
    var repo by remember(gitHubConfig) { mutableStateOf(gitHubConfig.repo) }
    var token by remember(gitHubConfig) { mutableStateOf(gitHubConfig.token) }
    var tag by remember(gitHubConfig) { mutableStateOf(gitHubConfig.tagName) }
    var title by remember(gitHubConfig) { mutableStateOf(gitHubConfig.releaseTitle) }
    var notes by remember(gitHubConfig) { mutableStateOf(gitHubConfig.releaseNotes) }
    var showToken by remember { mutableStateOf(false) }
    var showWorkflowDialog by remember { mutableStateOf(false) }

    // SAF Document creator launcher for exporting GitHub Workflow YAML
    val exportWorkflowLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/yaml")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportGitHubWorkflowFile(context, it) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // GitHub Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = CyberPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "GITHUB RELEASE PUBLISHER",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceWhite,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Publish built APKs directly to GitHub Releases & CI/CD",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberPrimary
                    )
                }
            }
        }

        // Active APK Payload Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CURRENT APK PAYLOAD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDim,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val apkFile = latestBuild?.apkFile
                    if (apkFile != null && apkFile.exists()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CyberSecondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = apkFile.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = OnSurfaceWhite
                                )
                                Text(
                                    text = "${apkFile.length() / 1024} KB • Ready for GitHub Release",
                                    fontSize = 11.sp,
                                    color = CyberSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                viewModel.updateGitHubConfig(
                                    GitHubConfig(
                                        owner = owner,
                                        repo = repo,
                                        token = token,
                                        tagName = tag,
                                        releaseTitle = title,
                                        releaseNotes = notes
                                    )
                                )
                                viewModel.publishApkToGitHub(apkFile)
                            },
                            enabled = !isUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("upload_to_github_main_btn")
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = CyberBg,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("UPLOADING TO GITHUB...", color = CyberBg, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = CyberBg,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "UPLOAD APK TO GITHUB RELEASE",
                                    color = CyberBg,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberSurfaceVariant)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CyberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "No compiled APK found. Build an APK in the 'Builder' tab first, then return here to publish it to GitHub.",
                                fontSize = 12.sp,
                                color = OnSurfaceDim
                            )
                        }
                    }
                }
            }
        }

        // Live Release Result Banner
        releaseResult?.let { res ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (res.success) CyberSecondaryContainer.copy(alpha = 0.4f) else CyberSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (res.success) CyberSecondary else CyberError,
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (res.success) CyberSecondary else CyberError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (res.success) "GitHub Release Published!" else "Upload Error",
                                fontWeight = FontWeight.Bold,
                                color = if (res.success) CyberSecondary else CyberError,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (res.success && res.htmlUrl != null) {
                            Text(
                                text = res.htmlUrl,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = CyberPrimary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(res.htmlUrl))
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("open_github_release_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        tint = CyberBg,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open on GitHub", color = CyberBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Release URL", res.htmlUrl))
                                        Toast.makeText(context, "Copied URL to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("copy_github_release_url_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy URL", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text(
                                text = res.errorMessage ?: "Unknown error occurred.",
                                fontSize = 12.sp,
                                color = CyberError
                            )
                        }
                    }
                }
            }
        }

        // Repository & Release Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GITHUB REPOSITORY CONFIGURATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceDim,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Test Connection",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberPrimary,
                            modifier = Modifier
                                .clickable {
                                    viewModel.updateGitHubConfig(
                                        GitHubConfig(
                                            owner = owner,
                                            repo = repo,
                                            token = token,
                                            tagName = tag,
                                            releaseTitle = title,
                                            releaseNotes = notes
                                        )
                                    )
                                    viewModel.testGitHubConnection()
                                }
                                .padding(4.dp)
                                .testTag("test_github_connection_btn")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Repo Owner & Name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = owner,
                            onValueChange = {
                                owner = it
                                viewModel.updateGitHubConfig(gitHubConfig.copy(owner = it))
                            },
                            label = { Text("Owner / Org", fontSize = 11.sp) },
                            placeholder = { Text("e.g. kyers1818", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_owner_input")
                        )

                        OutlinedTextField(
                            value = repo,
                            onValueChange = {
                                repo = it
                                viewModel.updateGitHubConfig(gitHubConfig.copy(repo = it))
                            },
                            label = { Text("Repository", fontSize = 11.sp) },
                            placeholder = { Text("e.g. my-app", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_repo_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Personal Access Token
                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it
                            viewModel.updateGitHubConfig(gitHubConfig.copy(token = it))
                        },
                        label = { Text("Personal Access Token (PAT)", fontSize = 11.sp) },
                        placeholder = { Text("ghp_... (scope: repo)", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                text = if (showToken) "HIDE" else "SHOW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberPrimary,
                                modifier = Modifier
                                    .clickable { showToken = !showToken }
                                    .padding(8.dp)
                            )
                        },
                        supportingText = {
                            Text(
                                text = "Create at github.com/settings/tokens with 'repo' scope. Leave empty for demo mode.",
                                fontSize = 10.sp,
                                color = OnSurfaceDim
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_token_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Release Tag & Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tag,
                            onValueChange = {
                                tag = it
                                viewModel.updateGitHubConfig(gitHubConfig.copy(tagName = it))
                            },
                            label = { Text("Release Tag", fontSize = 11.sp) },
                            placeholder = { Text("v1.0.0", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_tag_input")
                        )

                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                viewModel.updateGitHubConfig(gitHubConfig.copy(releaseTitle = it))
                            },
                            label = { Text("Release Title", fontSize = 11.sp) },
                            placeholder = { Text("Release v1.0.0", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("github_title_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Release Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            viewModel.updateGitHubConfig(gitHubConfig.copy(releaseNotes = it))
                        },
                        label = { Text("Release Notes / Changelog", fontSize = 11.sp) },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_notes_input")
                    )
                }
            }
        }

        // CI/CD GitHub Actions Generator Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = CyberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AUTOMATED GITHUB ACTIONS CI/CD",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceWhite
                            )
                            Text(
                                text = "Build & attach APKs on GitHub automatically on every push",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                exportWorkflowLauncher.launch("build-apk.yml")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_github_workflow_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = CyberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Workflow YAML", color = OnSurfaceWhite, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val yaml = GitHubReleaseManager(context).generateGitHubWorkflowYaml(currentProject)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("workflow.yml", yaml))
                                Toast.makeText(context, "Copied GitHub Actions YAML to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_workflow_yaml_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy YAML", fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
