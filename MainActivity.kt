package com.lite.downloader

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val allowedDomains = listOf(
        "youtube.com", "youtu.be",
        "facebook.com", "fb.watch", "fb.com",
        "twitter.com", "x.com",
        "instagram.com",
        "tiktok.com"
    )

    private val apiEndpoint = "https://api.cobalt.tools/api/json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val pasteBtn = findViewById<Button>(R.id.pasteBtn)
        val downloadBtn = findViewById<Button>(R.id.downloadBtn)
        val statusText = findViewById<TextView>(R.id.statusText)

        // Handle shared links directly from YT/FB/IG/X/TikTok Share menu
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                urlInput.setText(sharedText.trim())
            }
        }

        pasteBtn.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            urlInput.setText(item?.text ?: "")
        }

        downloadBtn.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            if (!isValidPlatform(rawUrl)) {
                statusText.setTextColor(0xFFFF5252.toInt())
                statusText.text = "Invalid link! Only YT, FB, X, IG, & TikTok are allowed."
                return@setOnClickListener
            }

            statusText.setTextColor(0xFFFFFFFF.toInt())
            statusText.text = "Extracting video stream..."
            downloadBtn.isEnabled = false

            thread {
                try {
                    val directVideoUrl = fetchDirectStream(rawUrl)
                    runOnUiThread {
                        downloadBtn.isEnabled = true
                        if (directVideoUrl != null) {
                            startSystemDownload(directVideoUrl)
                            statusText.setTextColor(0xFF4CAF50.toInt())
                            statusText.text = "Download started! Check your notification bar."
                        } else {
                            statusText.setTextColor(0xFFFF5252.toInt())
                            statusText.text = "Could not extract stream. Check link privacy or API."
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        downloadBtn.isEnabled = true
                        statusText.setTextColor(0xFFFF5252.toInt())
                        statusText.text = "Error: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    private fun isValidPlatform(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return allowedDomains.any { host == it || host.endsWith(".$it") }
    }

    private fun fetchDirectStream(videoUrl: String): String? {
        val connection = URL(apiEndpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val payload = JSONObject().apply {
            put("url", videoUrl)
            put("vQuality", "720")
        }

        OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

        if (connection.responseCode in 200..299) {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            if (json.has("url")) {
                return json.getString("url")
            }
        }
        return null
    }

    private fun startSystemDownload(directUrl: String) {
        val request = DownloadManager.Request(Uri.parse(directUrl))
            .setTitle("Video_${System.currentTimeMillis()}.mp4")
            .setDescription("Downloading video...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "video_${System.currentTimeMillis()}.mp4")

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    }
}
