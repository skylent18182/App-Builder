package com.example

import com.example.engine.ProjectZipManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class UserZipDebugUnitTest {

  @Test
  fun testExtractCodeDexZip() {
    val currentDir = File(System.getProperty("user.dir") ?: ".")
    val zipFile = listOf(
      File(currentDir, "public/assets/aistudio/CodeDexLocalAI-Android-Projekt-v2_b6f38e53.zip"),
      File(currentDir.parentFile, "public/assets/aistudio/CodeDexLocalAI-Android-Projekt-v2_b6f38e53.zip"),
      File("./public/assets/aistudio/CodeDexLocalAI-Android-Projekt-v2_b6f38e53.zip")
    ).firstOrNull { it.exists() }

    assertNotNull("ZIP file must exist at path", zipFile)
    println("Found zip at: ${zipFile!!.absolutePath}")

    val project = ProjectZipManager.extractProjectFromZip(FileInputStream(zipFile), "CodeDexLocalAI")
    println("=== EXTRACTED PROJECT DETAILS ===")
    println("Name: ${project.name}")
    println("PackageName: '${project.packageName}'")
    println("AppLabel: '${project.appLabel}'")
    println("VersionName: '${project.versionName}'")
    println("VersionCode: ${project.versionCode}")
    println("LauncherActivity: '${project.launcherActivity}'")
    println("Permissions: ${project.permissions}")
    println("Dependencies: ${project.dependencies}")
    println("File Count: ${project.files.size}")
    for (f in project.files.keys) {
      println("   • $f (${project.files[f]?.length} chars)")
    }

    assertEquals("dev.codedex.localai", project.packageName)
    assertEquals("CodeDex Local AI", project.appLabel)
    assertEquals("CodeDex Local AI", project.name)
    assertEquals("0.1.0", project.versionName)
    assertTrue("Should contain MainActivity.kt", project.files.keys.any { it.endsWith("MainActivity.kt") })
    assertTrue("Should contain app/build.gradle.kts", project.files.containsKey("app/build.gradle.kts"))
    assertTrue("Should contain settings.gradle.kts", project.files.containsKey("settings.gradle.kts"))

    // Test extraction when default name is generic "ImportedProject"
    val projectDefault = ProjectZipManager.extractProjectFromZip(FileInputStream(zipFile), "ImportedProject")
    assertEquals("CodeDex Local AI", projectDefault.name)
    assertEquals("dev.codedex.localai", projectDefault.packageName)
  }
}
