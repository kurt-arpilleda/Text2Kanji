package com.example.text2kanji

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.widget.Toast
import androidx.core.content.FileProvider
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateService(private val context: Context) {

    private val service: ApiService = RetrofitClient.instance

    fun checkForAppUpdate() {
        val currentVersionCode = getCurrentAppVersionCode()

        service.getAppUpdateDetails().enqueue(object : Callback<AppUpdateResponse> {
            override fun onResponse(call: Call<AppUpdateResponse>, response: Response<AppUpdateResponse>) {
                if (response.isSuccessful) {
                    val appUpdateResponse = response.body()

                    if (appUpdateResponse?.elements?.isNotEmpty() == true) {
                        val newVersionCode = appUpdateResponse.elements[0].versionCode

                        if (newVersionCode > currentVersionCode) {
                            showUpdateDialog(appUpdateResponse.elements[0].outputFile)
                        }
                    }
                }
            }

            override fun onFailure(call: Call<AppUpdateResponse>, t: Throwable) {
                // Handle network failures here
            }
        })
    }

    private fun getCurrentAppVersionCode(): Int {
        return try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            -1
        }
    }

    private fun showUpdateDialog(fileName: String) {
        AlertDialog.Builder(context)
            .setTitle("New Version Available")
            .setMessage("A new version of the app is available. Please update now.")
            .setPositiveButton("Update") { _, _ ->
                showDownloadProgressDialog(fileName)
            }
            .setCancelable(false)
            .show()
    }

    private fun showDownloadProgressDialog(fileName: String) {
        val progressDialog = ProgressDialog(context).apply {
            setTitle("Downloading Update")
            setMessage("Downloading $fileName...")
            setCancelable(false)
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setIndeterminate(false)
        }

        progressDialog.show()

        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        downloadFile(fileName, RetrofitClient.PRIMARY_URL, progressDialog, destinationFile) // Start download from primary URL
    }

    private fun downloadFile(fileName: String, baseUrl: String, progressDialog: ProgressDialog, destinationFile: File) {
        Thread {
            var url = "$baseUrl/V4/Others/Kurt/LatestVersionAPK/Text2Kanji/$fileName"
            var downloadedBytes: Long = 0
            var totalBytes: Long = 0

            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                totalBytes = connection.contentLength.toLong()
                progressDialog.max = totalBytes.toInt()

                val inputStream: InputStream = BufferedInputStream(connection.inputStream)
                val outputStream = FileOutputStream(destinationFile)
                val buffer = ByteArray(8192)
                var count: Int
                while (inputStream.read(buffer).also { count = it } != -1) {
                    outputStream.write(buffer, 0, count)
                    downloadedBytes += count.toLong()

                    // Update progress on the main thread
                    Handler(context.mainLooper).post {
                        progressDialog.progress = downloadedBytes.toInt()
                        progressDialog.setMessage("$fileName... ${(downloadedBytes * 100) / totalBytes}%")
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                Handler(context.mainLooper).post {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Download Complete!", Toast.LENGTH_SHORT).show()
                    installAPK(destinationFile)
                }

            } catch (e: Exception) {
                // Attempt to download from the fallback URL
                downloadFile(fileName, RetrofitClient.FALLBACK_URL, progressDialog, destinationFile)
            }
        }.start()
    }

    private fun installAPK(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    }
}
