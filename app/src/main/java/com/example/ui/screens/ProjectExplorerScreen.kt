package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.OnSurfaceWhite
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalDim
import com.example.ui.theme.TerminalText
import com.example.viewmodel.ApkBuilderViewModel

@Composable
fun ProjectExplorerScreen(
    viewModel: ApkBuilderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val project by viewModel.currentProject.collectAsState()
    val allFiles = remember(project) { project.getFileTree() }

    var selectedFilePath by remember(project) {
        mutableStateOf(
            allFiles.find { it.endsWith("AndroidManifest.xml") } ?: allFiles.firstOrNull() ?: ""
        )
    }

    val isBinary = project.binaryFiles.containsKey(selectedFilePath)
    val textContent = project.files[selectedFilePath] ?: ""

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
    ) {
        // Explorer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface)
                .border(1.dp, CyberCardBorder)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "PROJECT EXPLORER",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceWhite
                )
                Text(
                    text = "${allFiles.size} extracted files in '${project.name}'",
                    fontSize = 12.sp,
                    color = CyberPrimary
                )
            }
        }

        // Horizontal split: File list on top/collapsible, Code viewer below
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // File Selector List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(CyberSurfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(allFiles) { path ->
                    val isSelected = path == selectedFilePath
                    val isBin = project.binaryFiles.containsKey(path)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) CyberSurfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { selectedFilePath = path }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("file_item_$path"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isBin -> Icons.Default.Image
                                path.endsWith(".xml") -> Icons.Default.Code
                                path.endsWith(".kt") || path.endsWith(".java") -> Icons.Default.Description
                                else -> Icons.Default.Folder
                            },
                            contentDescription = null,
                            tint = if (isSelected) CyberPrimary else OnSurfaceDim,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = path,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) CyberPrimary else OnSurfaceWhite
                        )
                    }
                }
            }

            // File Viewer Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface)
                    .border(1.dp, CyberCardBorder)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedFilePath.substringAfterLast('/'),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBinary) "BINARY (${project.binaryFiles[selectedFilePath]?.size ?: 0} B)"
                        else "${textContent.lines().size} lines (${textContent.length} B)",
                        fontSize = 10.sp,
                        color = OnSurfaceDim
                    )
                }

                if (!isBinary) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(selectedFilePath, textContent))
                            Toast.makeText(context, "Copied code to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("copy_file_content_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy file",
                            tint = OnSurfaceDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // File Content Viewer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TerminalBackground)
                    .padding(8.dp)
            ) {
                if (isBinary) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Binary Asset (${project.binaryFiles[selectedFilePath]?.size ?: 0} bytes)\nPackaged directly into APK archive.",
                            color = OnSurfaceDim,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    val lines = remember(textContent) { textContent.lines() }
                    val hScroll = rememberScrollState()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(hScroll)
                    ) {
                        items(lines.size) { lineIndex ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "%3d | ".format(lineIndex + 1),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TerminalDim
                                )
                                Text(
                                    text = lines[lineIndex],
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TerminalText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
