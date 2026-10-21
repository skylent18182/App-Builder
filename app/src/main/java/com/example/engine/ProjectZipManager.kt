package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.model.AndroidProject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ProjectZipManager {

    /**
     * Extracts an Android project from an InputStream of a ZIP file.
     */
    fun extractProjectFromZip(inputStream: InputStream, defaultName: String = "ImportedProject"): AndroidProject {
        val rawEntries = mutableMapOf<String, ByteArray>()

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val buffer = ByteArrayOutputStream()
                    val data = ByteArray(4096)
                    var count: Int
                    while (zis.read(data).also { count = it } != -1) {
                        buffer.write(data, 0, count)
                    }
                    val cleanPath = entry.name.replace("\\", "/").trimStart('/')
                    rawEntries[cleanPath] = buffer.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Determine if all entries are wrapped inside a single root folder (e.g. CodeDexLocalAI/...)
        val rootDirs = rawEntries.keys.mapNotNull { path ->
            if (path.contains("/")) path.substringBefore("/") else null
        }.toSet()
        val commonRoot = if (rootDirs.size == 1) rootDirs.first() else null

        val textFiles = mutableMapOf<String, String>()
        val binaryFiles = mutableMapOf<String, ByteArray>()

        for ((rawPath, bytes) in rawEntries) {
            val normalizedPath = if (commonRoot != null && rawPath.startsWith("$commonRoot/")) {
                rawPath.removePrefix("$commonRoot/")
            } else {
                normalizePath(rawPath)
            }

            if (isTextFile(normalizedPath)) {
                try {
                    textFiles[normalizedPath] = String(bytes, Charsets.UTF_8)
                } catch (_: Exception) {
                    binaryFiles[normalizedPath] = bytes
                }
            } else {
                binaryFiles[normalizedPath] = bytes
            }
        }

        val fallback = if (defaultName != "ImportedApp" && defaultName != "ImportedProject" && defaultName.isNotBlank()) {
            defaultName
        } else {
            commonRoot ?: defaultName
        }

        return parseProjectFromFiles(textFiles, binaryFiles, fallback)
    }

    /**
     * Loads a project archive from an application asset.
     */
    fun loadProjectFromAsset(context: Context, assetPath: String, defaultName: String): AndroidProject? {
        return try {
            context.assets.open(assetPath).use { stream ->
                extractProjectFromZip(stream, defaultName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizePath(rawPath: String): String {
        var clean = rawPath.replace("\\", "/").trimStart('/')
        // Strip leading parent directory if zipped as a folder (e.g. MyProject/app/...)
        val parts = clean.split("/")
        if (parts.size > 1 && (clean.contains("/app/") || clean.contains("/src/"))) {
            val appIndex = clean.indexOf("app/")
            if (appIndex > 0) {
                clean = clean.substring(appIndex)
            } else {
                val srcIndex = clean.indexOf("src/")
                if (srcIndex > 0) {
                    clean = "app/" + clean.substring(srcIndex)
                }
            }
        }
        return clean
    }

    private fun isTextFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".xml") ||
                lower.endsWith(".gradle") ||
                lower.endsWith(".gradle.kts") ||
                lower.endsWith(".kt") ||
                lower.endsWith(".java") ||
                lower.endsWith(".properties") ||
                lower.endsWith(".json") ||
                lower.endsWith(".txt") ||
                lower.endsWith(".md") ||
                lower.endsWith(".pro")
    }

    fun parseProjectFromFiles(
        textFiles: Map<String, String>,
        binaryFiles: Map<String, ByteArray>,
        fallbackName: String
    ): AndroidProject {
        // Find AndroidManifest.xml
        val manifestEntry = textFiles.entries.find { it.key.endsWith("AndroidManifest.xml") }
        val manifestContent = manifestEntry?.value ?: ""

        val rootProjectName = extractRootProjectName(textFiles)
        val manifestLabel = extractManifestLabel(manifestContent, textFiles)

        val packageName = extractAttribute(manifestContent, "package")
            ?: extractGradleNamespace(textFiles)
            ?: "com.example.${fallbackName.lowercase().filter { it.isLetter() }}"

        // Determine best user-facing project name: manifestLabel > rootProjectName > fallbackName
        val effectiveName = when {
            !manifestLabel.isNullOrBlank() -> manifestLabel
            !rootProjectName.isNullOrBlank() -> rootProjectName
            fallbackName.isNotBlank() && fallbackName != "ImportedApp" && fallbackName != "ImportedProject" -> fallbackName
            else -> "Android App"
        }

        val appLabel = manifestLabel ?: effectiveName
        val versionCode = extractVersionCode(manifestContent, textFiles)
        val versionName = extractVersionName(manifestContent, textFiles)
        val launcherActivity = extractLauncherActivity(manifestContent) ?: "MainActivity"
        val permissions = extractPermissions(manifestContent)
        val dependencies = extractDependencies(textFiles)

        return AndroidProject(
            name = effectiveName,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            appLabel = appLabel,
            launcherActivity = launcherActivity,
            files = textFiles,
            binaryFiles = binaryFiles,
            permissions = permissions,
            dependencies = dependencies,
            isTemplate = false
        )
    }

    private fun extractRootProjectName(textFiles: Map<String, String>): String? {
        val settingsFile = textFiles.entries.find { it.key.contains("settings.gradle") }?.value ?: return null
        val regex = Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""")
        return regex.find(settingsFile)?.groupValues?.get(1)
    }

    private fun extractAttribute(xml: String, attrName: String): String? {
        val regex = Regex("""$attrName\s*=\s*["']([^"']+)["']""")
        return regex.find(xml)?.groupValues?.get(1)
    }

    private fun extractGradleNamespace(textFiles: Map<String, String>): String? {
        val gradleFiles = textFiles.filter { it.key.contains("build.gradle") }
        for ((_, content) in gradleFiles) {
            val ns = Regex("""namespace\s*=\s*["']([^"']+)["']""").find(content)?.groupValues?.get(1)
            if (ns != null) return ns
            val appId = Regex("""applicationId\s*=?\s*["']([^"']+)["']""").find(content)?.groupValues?.get(1)
            if (appId != null) return appId
        }
        return null
    }

    private fun extractManifestLabel(manifest: String, textFiles: Map<String, String>): String? {
        val labelAttr = extractAttribute(manifest, "android:label") ?: return null
        if (labelAttr.startsWith("@string/")) {
            val stringKey = labelAttr.removePrefix("@string/")
            val stringsFile = textFiles.entries.find { it.key.endsWith("strings.xml") }?.value
            if (stringsFile != null) {
                val stringRegex = Regex("""<string\s+name=["']$stringKey["']>([^<]+)</string>""")
                val found = stringRegex.find(stringsFile)?.groupValues?.get(1)
                if (!found.isNullOrBlank()) return found
            }
            return stringKey
        }
        return labelAttr
    }

    private fun extractVersionCode(manifest: String, textFiles: Map<String, String>): Int {
        val manifestCode = extractAttribute(manifest, "android:versionCode")?.toIntOrNull()
        if (manifestCode != null) return manifestCode
        for ((_, content) in textFiles.filter { it.key.contains("build.gradle") }) {
            val code = Regex("""versionCode\s*=?\s*(\d+)""").find(content)?.groupValues?.get(1)?.toIntOrNull()
            if (code != null) return code
        }
        return 1
    }

    private fun extractVersionName(manifest: String, textFiles: Map<String, String>): String {
        val manifestName = extractAttribute(manifest, "android:versionName")
        if (!manifestName.isNullOrBlank()) return manifestName
        for ((_, content) in textFiles.filter { it.key.contains("build.gradle") }) {
            val name = Regex("""versionName\s*=?\s*["']([^"']+)["']""").find(content)?.groupValues?.get(1)
            if (!name.isNullOrBlank()) return name
        }
        return "1.0.0"
    }

    private fun extractLauncherActivity(manifest: String): String? {
        val activityRegex = Regex("""<activity[^>]*android:name=["']([^"']+)["'][^>]*>([\s\S]*?)</activity>""")
        for (match in activityRegex.findAll(manifest)) {
            val activityName = match.groupValues[1]
            val activityBody = match.groupValues[2]
            if (activityBody.contains("android.intent.action.MAIN") &&
                activityBody.contains("android.intent.category.LAUNCHER")
            ) {
                return activityName.removePrefix(".")
            }
        }
        return null
    }

    private fun extractPermissions(manifest: String): List<String> {
        val regex = Regex("""<uses-permission[^>]*android:name=["']([^"']+)["']""")
        return regex.findAll(manifest).map { it.groupValues[1] }.toList()
    }

    private fun extractDependencies(textFiles: Map<String, String>): List<String> {
        val list = mutableListOf<String>()
        val gradleFiles = textFiles.filter { it.key.contains("build.gradle") }
        for ((_, content) in gradleFiles) {
            val depRegex = Regex("""implementation\s*\(?["']([^"':]+:[^"':]+:[^"']+)["']\)?""")
            for (match in depRegex.findAll(content)) {
                list.add(match.groupValues[1])
            }
        }
        return list.distinct()
    }

    /**
     * Creates ready-to-build templates.
     */
    fun getBuiltInTemplates(): List<AndroidProject> {
        return listOf(
            createCodeDexLocalAiTemplate(),
            createNeonCounterTemplate(),
            createHardwareSensorsTemplate(),
            createWebWrapperTemplate()
        )
    }

    private fun createCodeDexLocalAiTemplate(): AndroidProject {
        val files = mapOf(
            "app/src/main/AndroidManifest.xml" to """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <application
                        android:allowBackup="true"
                        android:label="CodeDex Local AI"
                        android:theme="@android:style/Theme.Material.Light.NoActionBar"
                        android:usesCleartextTraffic="false">
                        <activity
                            android:name=".MainActivity"
                            android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent(),
            "app/build.gradle.kts" to """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                    id("org.jetbrains.kotlin.plugin.compose")
                }
                android {
                    namespace = "dev.codedex.localai"
                    compileSdk = 35
                    defaultConfig {
                        applicationId = "dev.codedex.localai"
                        minSdk = 26
                        targetSdk = 35
                        versionCode = 1
                        versionName = "0.1.0"
                    }
                    buildFeatures {
                        compose = true
                    }
                }
                dependencies {
                    implementation("androidx.core:core-ktx:1.15.0")
                    implementation("androidx.activity:activity-compose:1.9.3")
                    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
                    implementation("com.google.mediapipe:tasks-genai:0.10.24")
                }
            """.trimIndent(),
            "settings.gradle.kts" to """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                rootProject.name = "CodeDexLocalAI"
                include(":app")
            """.trimIndent(),
            "build.gradle.kts" to """
                plugins {
                    id("com.android.application") version "8.7.3" apply false
                    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
                    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
                }
            """.trimIndent(),
            "gradle.properties" to """
                org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
                android.useAndroidX=true
                kotlin.code.style=official
            """.trimIndent(),
            "app/src/main/java/dev/codedex/localai/MainActivity.kt" to """
                package dev.codedex.localai

                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.material3.Text

                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContent {
                            Text("CodeDex Local AI - Initialized")
                        }
                    }
                }
            """.trimIndent()
        )

        return AndroidProject(
            id = "template_codedex_localai",
            name = "CodeDex Local AI",
            packageName = "dev.codedex.localai",
            versionName = "0.1.0",
            versionCode = 1,
            appLabel = "CodeDex Local AI",
            launcherActivity = "MainActivity",
            files = files,
            permissions = listOf("android.permission.INTERNET"),
            dependencies = listOf(
                "androidx.core:core-ktx:1.15.0",
                "androidx.activity:activity-compose:1.9.3",
                "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7",
                "com.google.mediapipe:tasks-genai:0.10.24"
            ),
            isTemplate = true
        )
    }

    private fun createNeonCounterTemplate(): AndroidProject {
        val files = mapOf(
            "app/src/main/AndroidManifest.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.forge.neoncounter">
                    <uses-permission android:name="android.permission.VIBRATE" />
                    <application
                        android:allowBackup="true"
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity
                            android:name=".MainActivity"
                            android:exported="true"
                            android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent(),
            "app/build.gradle.kts" to """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
                android {
                    namespace = "com.forge.neoncounter"
                    compileSdk = 34
                    defaultConfig {
                        applicationId = "com.forge.neoncounter"
                        minSdk = 24
                        targetSdk = 34
                        versionCode = 1
                        versionName = "1.0.0"
                    }
                }
                dependencies {
                    implementation("androidx.core:core-ktx:1.12.0")
                    implementation("androidx.appcompat:appcompat:1.6.1")
                    implementation("com.google.android.material:material:1.11.0")
                }
            """.trimIndent(),
            "app/src/main/res/values/strings.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">Neon Counter</string>
                    <string name="counter_title">Tally Counter</string>
                    <string name="reset_btn">Reset</string>
                    <string name="step_label">Step +1</string>
                </resources>
            """.trimIndent(),
            "app/src/main/res/values/colors.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <color name="primary_neon">#00E5FF</color>
                    <color name="bg_dark">#0A0F1D</color>
                    <color name="surface_card">#161F30</color>
                </resources>
            """.trimIndent(),
            "app/src/main/res/layout/activity_main.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical"
                    android:gravity="center"
                    android:padding="24dp"
                    android:background="@color/bg_dark">
                    <TextView
                        android:id="@+id/tv_title"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/counter_title"
                        android:textSize="24sp"
                        android:textColor="@color/primary_neon" />
                    <TextView
                        android:id="@+id/tv_count"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="0"
                        android:textSize="84sp"
                        android:textStyle="bold"
                        android:textColor="#FFFFFF"
                        android:layout_margin="32dp" />
                    <Button
                        android:id="@+id/btn_increment"
                        android:layout_width="200dp"
                        android:layout_height="64dp"
                        android:text="+ TAP COUNT"
                        android:backgroundTint="@color/primary_neon"
                        android:textColor="#000000" />
                </LinearLayout>
            """.trimIndent(),
            "app/src/main/java/com/forge/neoncounter/MainActivity.kt" to """
                package com.forge.neoncounter

                import android.app.Activity
                import android.os.Bundle
                import android.widget.Button
                import android.widget.TextView

                class MainActivity : Activity() {
                    private var count = 0

                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)

                        val tvCount = findViewById<TextView>(R.id.tv_count)
                        val btnIncrement = findViewById<Button>(R.id.btn_increment)

                        btnIncrement.setOnClickListener {
                            count++
                            tvCount.text = count.toString()
                        }
                    }
                }
            """.trimIndent()
        )

        return AndroidProject(
            id = "template_neon_counter",
            name = "Neon Counter App",
            packageName = "com.forge.neoncounter",
            versionName = "1.0.0",
            versionCode = 1,
            appLabel = "Neon Counter",
            launcherActivity = "MainActivity",
            files = files,
            permissions = listOf("android.permission.VIBRATE"),
            dependencies = listOf(
                "androidx.core:core-ktx:1.12.0",
                "androidx.appcompat:appcompat:1.6.1",
                "com.google.android.material:material:1.11.0"
            ),
            isTemplate = true
        )
    }

    private fun createHardwareSensorsTemplate(): AndroidProject {
        val files = mapOf(
            "app/src/main/AndroidManifest.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.forge.sensorsuite">
                    <uses-permission android:name="android.permission.BODY_SENSORS" />
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
                    <application
                        android:allowBackup="true"
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity
                            android:name=".SensorActivity"
                            android:exported="true"
                            android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent(),
            "app/build.gradle.kts" to """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
                android {
                    namespace = "com.forge.sensorsuite"
                    compileSdk = 34
                    defaultConfig {
                        applicationId = "com.forge.sensorsuite"
                        minSdk = 26
                        targetSdk = 34
                        versionCode = 2
                        versionName = "1.1.0"
                    }
                }
                dependencies {
                    implementation("androidx.core:core-ktx:1.12.0")
                    implementation("androidx.appcompat:appcompat:1.6.1")
                }
            """.trimIndent(),
            "app/src/main/res/values/strings.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">Sensor Suite</string>
                    <string name="sensor_header">Live Telemetry</string>
                </resources>
            """.trimIndent(),
            "app/src/main/java/com/forge/sensorsuite/SensorActivity.kt" to """
                package com.forge.sensorsuite

                import android.app.Activity
                import android.hardware.Sensor
                import android.hardware.SensorEvent
                import android.hardware.SensorEventListener
                import android.hardware.SensorManager
                import android.os.Bundle

                class SensorActivity : Activity(), SensorEventListener {
                    private lateinit var sensorManager: SensorManager
                    private var accelerometer: Sensor? = null

                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    }

                    override fun onSensorChanged(event: SensorEvent?) {}
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
            """.trimIndent()
        )

        return AndroidProject(
            id = "template_sensor_suite",
            name = "Sensor Suite",
            packageName = "com.forge.sensorsuite",
            versionName = "1.1.0",
            versionCode = 2,
            appLabel = "Sensor Suite",
            launcherActivity = "SensorActivity",
            files = files,
            permissions = listOf("android.permission.BODY_SENSORS", "android.permission.ACCESS_FINE_LOCATION"),
            dependencies = listOf("androidx.core:core-ktx:1.12.0", "androidx.appcompat:appcompat:1.6.1"),
            isTemplate = true
        )
    }

    private fun createWebWrapperTemplate(): AndroidProject {
        val files = mapOf(
            "app/src/main/AndroidManifest.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.forge.webwrapper">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
                    <application
                        android:allowBackup="true"
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity
                            android:name=".WebActivity"
                            android:exported="true"
                            android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent(),
            "app/build.gradle.kts" to """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
                android {
                    namespace = "com.forge.webwrapper"
                    compileSdk = 34
                    defaultConfig {
                        applicationId = "com.forge.webwrapper"
                        minSdk = 24
                        targetSdk = 34
                        versionCode = 1
                        versionName = "1.0"
                    }
                }
                dependencies {
                    implementation("androidx.core:core-ktx:1.12.0")
                    implementation("androidx.webkit:webkit:1.9.0")
                }
            """.trimIndent(),
            "app/src/main/res/values/strings.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">Web Wrapper</string>
                    <string name="loading_msg">Loading secure webpage...</string>
                </resources>
            """.trimIndent(),
            "app/src/main/java/com/forge/webwrapper/WebActivity.kt" to """
                package com.forge.webwrapper

                import android.app.Activity
                import android.os.Bundle
                import android.webkit.WebView
                import android.webkit.WebViewClient

                class WebActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        val webView = WebView(this)
                        webView.settings.javaScriptEnabled = true
                        webView.webViewClient = WebViewClient()
                        webView.loadUrl("https://android.com")
                        setContentView(webView)
                    }
                }
            """.trimIndent()
        )

        return AndroidProject(
            id = "template_web_wrapper",
            name = "Web App Wrapper",
            packageName = "com.forge.webwrapper",
            versionName = "1.0",
            versionCode = 1,
            appLabel = "Web Wrapper",
            launcherActivity = "WebActivity",
            files = files,
            permissions = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"),
            dependencies = listOf("androidx.core:core-ktx:1.12.0", "androidx.webkit:webkit:1.9.0"),
            isTemplate = true
        )
    }

    /**
     * Packs an AndroidProject back into a ZIP byte array.
     */
    fun exportProjectToZip(project: AndroidProject): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((path, content) in project.files) {
                val entry = ZipEntry(path)
                zos.putNextEntry(entry)
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            for ((path, bytes) in project.binaryFiles) {
                val entry = ZipEntry(path)
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
