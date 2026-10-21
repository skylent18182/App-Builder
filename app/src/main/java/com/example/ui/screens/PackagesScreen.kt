package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberError
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.CyberPrimaryContainer
import com.example.ui.theme.CyberSecondary
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberWarning
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.OnSurfaceWhite
import com.example.ui.theme.TerminalDim
import com.example.viewmodel.ApkBuilderViewModel

@Composable
fun PackagesScreen(
    viewModel: ApkBuilderViewModel,
    modifier: Modifier = Modifier
) {
    val cachedIds by viewModel.cachedPackageIds.collectAsState()
    val totalCacheBytes by viewModel.cachedTotalBytes.collectAsState()
    val integrityReport by viewModel.integrityReport.collectAsState()

    val knownPackages = listOf(
        PackageItemInfo(
            id = "android_sdk_platform_34",
            name = "Android SDK Platform 34",
            coord = "com.google.android:android-sdk:34",
            desc = "android.jar core framework and framework symbols",
            category = "SDK Toolchain"
        ),
        PackageItemInfo(
            id = "androidx_core_core_ktx_1_12_0",
            name = "AndroidX Core KTX 1.12.0",
            coord = "androidx.core:core-ktx:1.12.0",
            desc = "Kotlin extensions and backward compatibility layer",
            category = "Core Runtime"
        ),
        PackageItemInfo(
            id = "androidx_appcompat_appcompat_1_6_1",
            name = "AndroidX AppCompat 1.6.1",
            coord = "androidx.appcompat:appcompat:1.6.1",
            desc = "Material theme support and legacy ActionBar APIs",
            category = "UI Library"
        ),
        PackageItemInfo(
            id = "com_google_android_material_material_1_11_0",
            name = "Material Components 1.11.0",
            coord = "com.google.android.material:material:1.11.0",
            desc = "Google Material 3 design widgets and themes",
            category = "UI Library"
        ),
        PackageItemInfo(
            id = "androidx_webkit_webkit_1_9_0",
            name = "AndroidX WebKit 1.9.0",
            coord = "androidx.webkit:webkit:1.9.0",
            desc = "Safe web browser integration layer",
            category = "Web Library"
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage Cache Header Card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "TOOLCHAIN PACKAGE CACHE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberPrimary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${totalCacheBytes / (1024 * 1024)} MB (${cachedIds.size} packages)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceWhite
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.refreshCacheInfo() },
                            modifier = Modifier.testTag("refresh_cache_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = OnSurfaceDim
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.downloadMissingPackages() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_all_packages_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = CyberBg
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Download All",
                                color = CyberBg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.executeTerminalCommand("clean") },
                            modifier = Modifier.testTag("purge_cache_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = CyberError
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", color = CyberError, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "REQUIRED BUILD PACKAGES & LIBRARIES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDim,
                letterSpacing = 1.sp
            )
        }

        // Package List
        items(knownPackages) { pkg ->
            val isCached = cachedIds.contains(pkg.id)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberSurface)
                    .border(
                        1.dp,
                        if (isCached) CyberSecondary.copy(alpha = 0.4f) else CyberCardBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isCached) CyberSecondary else CyberWarning)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pkg.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = OnSurfaceWhite
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pkg.category,
                                fontSize = 9.sp,
                                color = CyberPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberPrimaryContainer)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = pkg.coord,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = CyberPrimary
                        )
                        Text(
                            text = pkg.desc,
                            fontSize = 11.sp,
                            color = OnSurfaceDim
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isCached) "CACHED" else "MISSING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCached) CyberSecondary else CyberWarning
                    )
                }
            }
        }
    }
}

private data class PackageItemInfo(
    val id: String,
    val name: String,
    val coord: String,
    val desc: String,
    val category: String
)
