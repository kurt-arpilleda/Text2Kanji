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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateService(private val context: Context) {

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("http://14.1.67.29/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service: ApiService = retrofit.create(ApiService::class.java)

    fun checkForAppUpdate() {
        // Check current version code
        val currentVersionCode = getCurrentAppVersionCode()

        // Fetch update information
        service.getAppUpdateDetails().enqueue(object : Callback<AppUpdateResponse> {
            override fun onResponse(call: Call<AppUpdateResponse>, response: Response<AppUpdateResponse>) {
                if (response.isSuccessful) {
                    val appUpdateResponse = response.body()

                    // Check if there is a new version
                    if (appUpdateResponse != null && appUpdateResponse.elements.isNotEmpty()) {
                        val newVersionCode = appUpdateResponse.elements[0].versionCode

                        if (newVersionCode > currentVersionCode) {
                            // New version available, prompt user with a dialog
                            showUpdateDialog(appUpdateResponse.elements[0].outputFile)
                        }
                    }
                }
            }

            override fun onFailure(call: Call<AppUpdateResponse>, t: Throwable) {
                // Handle network errors or other failures
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
        val dialogBuilder = AlertDialog.Builder(context)
            .setTitle("New Version Available")
            .setMessage("A new version of the app is available. Please update now.")
            .setPositiveButton("Update") { _, _ ->
                // If "Update" is clicked, download and install the APK
                showDownloadProgressDialog(fileName)
            }

        val dialog = dialogBuilder.create()
        dialog.setCancelable(false) // Make the dialog non-cancelable
        dialog.show()
    }

    private fun showDownloadProgressDialog(fileName: String) {
        val progressDialog = ProgressDialog(context)
        progressDialog.setTitle("Downloading Update")

        progressDialog.setMessage("Downloading $fileName...") // Display filename
        progressDialog.setCancelable(false)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL); // Important for percentage
        progressDialog.setIndeterminate(false);
        progressDialog.show()

        val url = "http://14.1.67.29/V4/Others/Kurt/LatestVersionAPK/Text2Kanji/$fileName"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        Thread {
            var downloadedBytes: Long = 0
            var totalBytes: Long = 0

            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                totalBytes = connection.contentLength.toLong()
                progressDialog.setMax(totalBytes.toInt()); // Set max value for progress bar

                val inputStream: InputStream = BufferedInputStream(connection.inputStream)
                val outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8192)
                var count: Int
                while (inputStream.read(buffer).also { count = it } != -1) {
                    outputStream.write(buffer, 0, count)
                    downloadedBytes += count.toLong()

                    // Update progress on the main thread
                    Handler(context.mainLooper).post {
                        val progress = (downloadedBytes * 100) / totalBytes
                        progressDialog.setMessage("$fileName... $progress%") // Keep filename
                        progressDialog.setProgress(downloadedBytes.toInt());
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
                Handler(context.mainLooper).post {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Error downloading APK", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }.start()
    }

    private fun installAPK(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(intent)
    }
}