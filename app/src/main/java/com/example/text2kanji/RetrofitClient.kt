package com.example.text2kanji

import com.google.gson.GsonBuilder
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {
    const val PRIMARY_URL = "http://192.168.254.163/"
    const val FALLBACK_URL =  "http://126.209.7.246/"

    // Configuring the logging interceptor
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    // Create an OkHttpClient with logging, timeout settings, and fallback logic
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(RetryInterceptor()) // Adding the retry interceptor
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Creating an instance of ApiService with lenient JSON parsing
    val instance: ApiService by lazy {
        val gson = GsonBuilder()
            .setLenient() // Enable lenient parsing to handle large or non-strict JSON
            .create()
        Retrofit.Builder()
            .baseUrl(PRIMARY_URL) // Start with the primary URL
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson)) // Set custom Gson instance
            .build()
            .create(ApiService::class.java)
    }

    // Interceptor to retry requests to a fallback URL if the primary URL is unreachable
    class RetryInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            var response: Response? = null
            var url = chain.request().url

            try {
                response = chain.proceed(chain.request())
                if (!response.isSuccessful) {
                    // If the response is not successful, retry with the fallback URL
                    response = retryWithFallbackUrl(chain, url)
                }
            } catch (e: Exception) {
                // If there was an exception (e.g., timeout or network failure), retry with the fallback URL
                response = retryWithFallbackUrl(chain, url)
            }

            return response ?: throw IOException("Network request failed.")
        }

        // Function to retry with the fallback URL
        private fun retryWithFallbackUrl(chain: Interceptor.Chain, url: HttpUrl): Response {
            val newUrl = url.newBuilder()
                .host(FALLBACK_URL.substringAfter("://").substringBefore("/"))
                .build()
            val newRequest = chain.request().newBuilder().url(newUrl).build()
            return chain.proceed(newRequest)
        }
    }
}
