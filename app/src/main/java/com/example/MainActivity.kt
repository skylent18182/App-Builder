package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ApkBuilderEngine
import com.example.engine.PackageManagerEngine
import com.example.model.BuildResult
import com.example.ui.components.BuildSuccessDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PackagesScreen
import com.example.ui.screens.ProjectExplorerScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.CyberPrimaryContainer
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.OnSurfaceWhite
import com.example.viewmodel.ApkBuilderViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ApkBuilderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val packageManagerEngine = PackageManagerEngine(applicationContext)
        val apkBuilderEngine = ApkBuilderEngine(applicationContext, packageManagerEngine)
        viewModel = ApkBuilderViewModel(packageManagerEngine, apkBuilderEngine)

        setContent {
            MyApplicationTheme {
                ApkBuilderApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.checkInstallPermission(this)
        }
    }
}

private enum class NavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    HOME("Builder", Icons.Filled.Build, Icons.Outlined.Build, "nav_tab_home"),
    TERMINAL("Terminal", Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_tab_terminal"),
    FILES("Explorer", Icons.Filled.Code, Icons.Outlined.Code, "nav_tab_files"),
    PACKAGES("Packages", Icons.Filled.Layers, Icons.Outlined.Layers, "nav_tab_packages")
}

@Composable
fun ApkBuilderApp(viewModel: ApkBuilderViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val latestBuildResult by viewModel.latestBuildResult.collectAsState()
    var showSuccessDialog by remember { mutableStateOf(false) }
    var apkToExport by remember { mutableStateOf<File?>(null) }

    // SAF Document creator launcher for exporting APK
    val exportApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            apkToExport?.let { file ->
                viewModel.exportApkToUri(context, file, targetUri)
                Toast.makeText(context, "Exported APK successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Show dialog when a new successful build arrives
    LaunchedEffect(latestBuildResult) {
        if (latestBuildResult?.success == true && latestBuildResult?.apkFile != null) {
            showSuccessDialog = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CyberBg,
        bottomBar = {
            NavigationBar(
                containerColor = CyberSurface,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_navigation_bar"),
                tonalElevation = 8.dp
            ) {
                NavTab.values().forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberPrimary,
                            selectedTextColor = CyberPrimary,
                            indicatorColor = CyberPrimaryContainer,
                            unselectedIconColor = OnSurfaceDim,
                            unselectedTextColor = OnSurfaceDim
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToTerminal = { selectedTab = 1 },
                    onNavigateToExplorer = { selectedTab = 2 }
                )
                1 -> TerminalScreen(
                    viewModel = viewModel
                )
                2 -> ProjectExplorerScreen(
                    viewModel = viewModel
                )
                3 -> PackagesScreen(
                    viewModel = viewModel
                )
            }
        }

        // Build Success Dialog
        if (showSuccessDialog && latestBuildResult?.success == true && latestBuildResult?.apkFile != null) {
            BuildSuccessDialog(
                buildResult = latestBuildResult!!,
                onInstall = {
                    latestBuildResult?.apkFile?.let { viewModel.installApk(context, it) }
                },
                onShare = {
                    latestBuildResult?.apkFile?.let { viewModel.shareApk(context, it) }
                },
                onExport = {
                    val file = latestBuildResult?.apkFile
                    if (file != null) {
                        apkToExport = file
                        exportApkLauncher.launch(file.name)
                    }
                },
                onDismiss = {
                    showSuccessDialog = false
                }
            )
        }
    }
}
