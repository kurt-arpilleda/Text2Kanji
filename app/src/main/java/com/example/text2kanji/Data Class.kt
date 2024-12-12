package com.example.text2kanji

// Update APK
data class AppUpdateResponse(
    val version: Int,
    val artifactType: ArtifactType,
    val applicationId: String,
    val variantName: String,
    val elements: List<Element>,
    val elementType: String,
    val minSdkVersionForDexing: Int
)
data class ArtifactType(
    val type: String,
    val kind: String
)
data class Element(
    val type: String,
    val filters: List<Any>,
    val attributes: List<Any>,
    val versionCode: Int,
    val versionName: String,
    val outputFile: String
)
// Update APK end

