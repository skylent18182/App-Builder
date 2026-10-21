package com.example.engine

import android.content.Context
import com.example.model.AndroidProject
import com.example.model.GitHubConfig
import com.example.model.GitHubReleaseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class GitHubReleaseManager(private val context: Context) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val prefs by lazy {
        context.getSharedPreferences("github_apk_forge", Context.MODE_PRIVATE)
    }

    fun loadSavedConfig(): GitHubConfig {
        return GitHubConfig(
            owner = prefs.getString("gh_owner", "") ?: "",
            repo = prefs.getString("gh_repo", "") ?: "",
            token = prefs.getString("gh_token", "") ?: "",
            tagName = prefs.getString("gh_tag", "v1.0.0") ?: "v1.0.0",
            releaseTitle = prefs.getString("gh_title", "APK Release") ?: "APK Release",
            releaseNotes = prefs.getString("gh_notes", "Automated release build created by APK Builder on Android.") ?: "",
            isDraft = prefs.getBoolean("gh_draft", false),
            isPrerelease = prefs.getBoolean("gh_prerelease", false)
        )
    }

    fun saveConfig(config: GitHubConfig) {
        prefs.edit()
            .putString("gh_owner", config.owner.trim())
            .putString("gh_repo", config.repo.trim())
            .putString("gh_token", config.token.trim())
            .putString("gh_tag", config.tagName.trim())
            .putString("gh_title", config.releaseTitle.trim())
            .putString("gh_notes", config.releaseNotes.trim())
            .putBoolean("gh_draft", config.isDraft)
            .putBoolean("gh_prerelease", config.isPrerelease)
            .apply()
    }

    suspend fun validateRepository(config: GitHubConfig): Result<String> = withContext(Dispatchers.IO) {
        val owner = config.owner.trim()
        val repo = config.repo.trim()
        if (owner.isEmpty() || repo.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Repository owner and name are required."))
        }

        try {
            val reqBuilder = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "APKBuilder-Android/1.0")

            if (config.token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${config.token.trim()}")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val fullName = json.optString("full_name", "$owner/$repo")
                    val isPrivate = json.optBoolean("private", false)
                    Result.success("Connected to $fullName (${if (isPrivate) "Private" else "Public"})")
                } else if (response.code == 404) {
                    Result.failure(Exception("Repository $owner/$repo not found or token lacks access permissions."))
                } else {
                    Result.failure(Exception("GitHub API Error (${response.code}): ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadApkToRelease(
        config: GitHubConfig,
        apkFile: File,
        onProgress: suspend (Float, String) -> Unit
    ): Result<GitHubReleaseResult> = withContext(Dispatchers.IO) {
        val owner = config.owner.trim()
        val repo = config.repo.trim()
        val token = config.token.trim()

        if (owner.isEmpty() || repo.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("GitHub owner and repository are required."))
        }

        if (token.isEmpty()) {
            // Simulated upload demo mode when user doesn't have an access token yet
            onProgress(0.1f, "Dry-run mode: Verifying GitHub release specifications...")
            delay(200)
            onProgress(0.4f, "Creating GitHub Release '${config.tagName}' for $owner/$repo...")
            delay(300)
            onProgress(0.7f, "Uploading ${apkFile.name} (${apkFile.length() / 1024} KB)...")
            delay(400)
            val demoUrl = "https://github.com/$owner/$repo/releases/tag/${config.tagName}"
            val downloadUrl = "https://github.com/$owner/$repo/releases/download/${config.tagName}/${apkFile.name}"
            onProgress(1.0f, "Simulated release published! Configure a GitHub Personal Access Token to publish to real GitHub.")
            return@withContext Result.success(
                GitHubReleaseResult(
                    success = true,
                    htmlUrl = demoUrl,
                    downloadUrl = downloadUrl,
                    releaseId = 999999L,
                    tagName = config.tagName
                )
            )
        }

        try {
            onProgress(0.15f, "Querying/Creating GitHub Release tag '${config.tagName}'...")

            // 1. Create or Find Release
            val releaseInfo = getOrCreateRelease(config)
            val releaseId = releaseInfo.first
            val releaseHtmlUrl = releaseInfo.second
            var uploadUrlTemplate = releaseInfo.third

            onProgress(0.4f, "Release ready (ID: $releaseId). Preparing APK asset...")

            // Clean existing asset with same name if already uploaded
            deleteExistingAssetIfPresent(owner, repo, releaseId, token, apkFile.name)

            // 2. Upload APK binary asset
            val uploadEndpoint = uploadUrlTemplate.substringBefore("{") + "?name=${apkFile.name}"
            onProgress(0.55f, "Uploading ${apkFile.name} (${apkFile.length() / 1024} KB) to GitHub...")

            val requestBody = createCountingRequestBody(
                file = apkFile,
                contentType = "application/vnd.android.package-archive"
            ) { bytesWritten, totalBytes ->
                val progress = 0.55f + ((bytesWritten.toFloat() / totalBytes) * 0.4f)
                val kbWritten = bytesWritten / 1024
                val kbTotal = totalBytes / 1024
                onProgress(progress, "Uploading APK: $kbWritten KB / $kbTotal KB (${(progress * 100).toInt()}%)")
            }

            val uploadRequest = Request.Builder()
                .url(uploadEndpoint)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/vnd.android.package-archive")
                .header("User-Agent", "APKBuilder-Android/1.0")
                .post(requestBody)
                .build()

            httpClient.newCall(uploadRequest).execute().use { uploadResponse ->
                val uploadBodyStr = uploadResponse.body?.string() ?: ""
                if (!uploadResponse.isSuccessful) {
                    throw IllegalStateException("GitHub Asset Upload Failed (${uploadResponse.code}): $uploadBodyStr")
                }

                val assetJson = JSONObject(uploadBodyStr)
                val downloadUrl = assetJson.optString(
                    "browser_download_url",
                    "https://github.com/$owner/$repo/releases/download/${config.tagName}/${apkFile.name}"
                )

                onProgress(1.0f, "✓ APK successfully uploaded to GitHub Release!")

                Result.success(
                    GitHubReleaseResult(
                        success = true,
                        htmlUrl = releaseHtmlUrl,
                        downloadUrl = downloadUrl,
                        releaseId = releaseId,
                        tagName = config.tagName
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getOrCreateRelease(config: GitHubConfig): Triple<Long, String, String> {
        val owner = config.owner.trim()
        val repo = config.repo.trim()
        val token = config.token.trim()

        // First attempt to create new release
        val createPayload = JSONObject().apply {
            put("tag_name", config.tagName.trim())
            put("name", config.releaseTitle.ifBlank { "Release ${config.tagName}" })
            put("body", config.releaseNotes.ifBlank { "Automated release build created by APK Builder." })
            put("draft", config.isDraft)
            put("prerelease", config.isPrerelease)
        }

        val createRequest = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "APKBuilder-Android/1.0")
            .post(RequestBody.create("application/json".toMediaType(), createPayload.toString()))
            .build()

        httpClient.newCall(createRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                val id = json.getLong("id")
                val htmlUrl = json.getString("html_url")
                val uploadUrl = json.getString("upload_url")
                return Triple(id, htmlUrl, uploadUrl)
            }

            // If 422 (tag exists), retrieve existing release by tag
            if (response.code == 422) {
                val getRequest = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/releases/tags/${config.tagName.trim()}")
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "APKBuilder-Android/1.0")
                    .build()

                httpClient.newCall(getRequest).execute().use { getResp ->
                    val getBody = getResp.body?.string() ?: ""
                    if (getResp.isSuccessful) {
                        val json = JSONObject(getBody)
                        val id = json.getLong("id")
                        val htmlUrl = json.getString("html_url")
                        val uploadUrl = json.getString("upload_url")
                        return Triple(id, htmlUrl, uploadUrl)
                    }
                }
            }

            throw IllegalStateException("Failed to create GitHub release (${response.code}): $body")
        }
    }

    private fun deleteExistingAssetIfPresent(
        owner: String,
        repo: String,
        releaseId: Long,
        token: String,
        assetName: String
    ) {
        try {
            val listReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/$releaseId/assets")
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "APKBuilder-Android/1.0")
                .build()

            httpClient.newCall(listReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONArray(resp.body?.string() ?: "[]")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("name") == assetName) {
                            val assetId = obj.getLong("id")
                            val delReq = Request.Builder()
                                .url("https://api.github.com/repos/$owner/$repo/releases/assets/$assetId")
                                .header("Accept", "application/vnd.github+json")
                                .header("Authorization", "Bearer $token")
                                .header("User-Agent", "APKBuilder-Android/1.0")
                                .delete()
                                .build()
                            httpClient.newCall(delReq).execute().close()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Non-fatal if asset removal fails
        }
    }

    private fun createCountingRequestBody(
        file: File,
        contentType: String,
        onProgress: suspend (Long, Long) -> Unit
    ): RequestBody {
        val totalLength = file.length()
        return object : RequestBody() {
            override fun contentType() = contentType.toMediaType()
            override fun contentLength() = totalLength

            override fun writeTo(sink: BufferedSink) {
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    var uploaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                        uploaded += read
                        kotlinx.coroutines.runBlocking {
                            onProgress(uploaded, totalLength)
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates a GitHub Actions CI/CD workflow file to build APKs on GitHub automatically.
     */
    fun generateGitHubWorkflowYaml(project: AndroidProject): String {
        return """
            name: Build and Release Android APK

            on:
              push:
                branches: [ "main", "master" ]
                tags: [ "v*" ]
              workflow_dispatch:

            jobs:
              build:
                runs-on: ubuntu-latest

                steps:
                - name: Checkout Repository
                  uses: actions/checkout@v4

                - name: Set up JDK 17
                  uses: actions/setup-java@v4
                  with:
                    java-version: '17'
                    distribution: 'temurin'
                    cache: gradle

                - name: Grant execute permission for gradlew
                  run: chmod +x gradlew || true

                - name: Build APK with Gradle
                  run: |
                    if [ -f "./gradlew" ]; then
                      ./gradlew assembleRelease || ./gradlew assembleDebug
                    else
                      gradle assembleRelease || gradle assembleDebug
                    fi

                - name: Upload APK Artifact
                  uses: actions/upload-artifact@v4
                  with:
                    name: ${project.name.replace(" ", "_")}-apk
                    path: app/build/outputs/apk/**/*.apk

                - name: Create GitHub Release
                  if: startsWith(github.ref, 'refs/tags/')
                  uses: softprops/action-gh-release@v2
                  with:
                    files: app/build/outputs/apk/**/*.apk
                    generate_release_notes: true
                  env:
                    GITHUB_TOKEN: ${'$'}{{ secrets.GITHUB_TOKEN }}
        """.trimIndent()
    }
}
