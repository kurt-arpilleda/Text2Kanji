package com.example.text2kanji

import retrofit2.Call
import retrofit2.http.GET

interface ApiService {
    // Update APK
    @GET("V4/Others/Kurt/LatestVersionAPK/Text2Kanji/output-metadata.json")
    fun getAppUpdateDetails(): Call<AppUpdateResponse>
}