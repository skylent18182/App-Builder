package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ApkBuilderEngine
import com.example.engine.PackageManagerEngine
import com.example.engine.ProjectVerifier
import com.example.engine.ProjectZipManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("APK Builder", appName)
  }

  @Test
  fun `verify built-in templates exist and are valid`() {
    val templates = ProjectZipManager.getBuiltInTemplates()
    assertTrue(templates.isNotEmpty())
    val neon = templates.first()
    assertEquals("Neon Counter App", neon.name)
    assertEquals("com.forge.neoncounter", neon.packageName)

    val report = ProjectVerifier.verify(neon, emptySet())
    assertNotNull(report)
    assertTrue(report.checks.any { it.title.contains("AndroidManifest") && it.passed })
    assertTrue(report.missingPackages.isNotEmpty())
  }

  @Test
  fun `test apk build produces 100 percent parseable and installable package`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pkgEngine = PackageManagerEngine(context)
    val builderEngine = ApkBuilderEngine(context, pkgEngine)

    for (template in ProjectZipManager.getBuiltInTemplates()) {
      val result = builderEngine.buildProject(
        project = template,
        onStepChanged = {},
        onLog = { println("LOG [${template.name}]: ${it.message}") }
      )

      assertTrue("Build should succeed for ${template.name}", result.success)
      assertNotNull("APK file should exist for ${template.name}", result.apkFile)

      val packageInfo = context.packageManager.getPackageArchiveInfo(result.apkFile!!.absolutePath, 0)
      println("VERIFIED APK: ${result.apkFile?.name} -> PackageInfo: $packageInfo")
      assertNotNull("PackageArchiveInfo must NOT be null! If null, Android displays 'There was a problem parsing the package.'", packageInfo)
      assertEquals(template.packageName, packageInfo?.packageName)
    }
  }

  @Test
  fun `test user zip extraction and build`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val zipFile = java.io.File("public/assets/aistudio/CodeDexLocalAI-Android-Projekt-v2_b6f38e53.zip")
    if (zipFile.exists()) {
      val project = ProjectZipManager.extractProjectFromZip(java.io.FileInputStream(zipFile), "CodeDexLocalAI")
      println("PARSED USER PROJECT:")
      println("  Name: ${project.name}")
      println("  PackageName: ${project.packageName}")
      println("  AppLabel: ${project.appLabel}")
      println("  Version: ${project.versionName} (${project.versionCode})")
      println("  Launcher: ${project.launcherActivity}")
      println("  Files: ${project.files.size}")
      println("  Permissions: ${project.permissions}")
      println("  Dependencies: ${project.dependencies}")

      val pkgEngine = PackageManagerEngine(context)
      val builderEngine = ApkBuilderEngine(context, pkgEngine)
      val result = builderEngine.buildProject(
        project = project,
        onStepChanged = {},
        onLog = { println("LOG [${project.name}]: ${it.message}") }
      )
      println("USER APK BUILD RESULT: success = ${result.success}, path = ${result.apkFile?.absolutePath}")
      assertNotNull(result.apkFile)

      val packageInfo = context.packageManager.getPackageArchiveInfo(result.apkFile!!.absolutePath, 0)
      println("USER APK PACKAGE INFO: $packageInfo")
      assertNotNull("User project APK must parse!", packageInfo)
      assertEquals("dev.codedex.localai", packageInfo?.packageName)
    }
  }
}

