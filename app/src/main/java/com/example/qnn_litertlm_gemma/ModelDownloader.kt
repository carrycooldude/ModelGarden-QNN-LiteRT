package com.example.qnn_litertlm_gemma

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility class for downloading LiteRT-LM models
 */
class ModelDownloader(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ModelDownloader"
        private const val KEY_HF_TOKEN = "hf_token"

        val AVAILABLE_MODELS = listOf(
            ModelConfig(
                id = "gemma-3n",
                name = "Gemma 3n (Int4)",
                filename = "gemma-3n-E2B-it-int4.litertlm",
                url = "https://huggingface.co/google/gemma-3n-it-int4/resolve/main/gemma-3n-it-int4.litertlm",
                systemPrompt = null, // Testing if prompt causes init failure
                preferredBackend = "NPU"
            )
        )
    }



    
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_HF_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_HF_TOKEN, null)
    }
    
    /**
     * Download model with progress reporting
     * @return Flow emitting download progress (0-100)
     */
    fun downloadModel(modelConfig: ModelConfig): Flow<DownloadProgress> = flow {
        try {
            emit(DownloadProgress.Started)
            
            val modelFile = File(context.filesDir, modelConfig.filename)
            
            // Check if already exists
            if (modelFile.exists()) {
                Log.d(TAG, "Model already exists at ${modelFile.absolutePath}")
                emit(DownloadProgress.Complete(modelFile.absolutePath))
                return@flow
            }
            
            Log.d(TAG, "Downloading model from ${modelConfig.url}")
            
            val url = URL(modelConfig.url)
            val connection = url.openConnection() as HttpURLConnection
            
            // Add Authorization header if token exists
            val token = getToken()
            if (!token.isNullOrBlank()) {
                Log.d(TAG, "Using HF Token for authentication")
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // If it's 404, maybe the filename is wrong, but we can't do much.
                // If 401/403, it's auth.
                throw Exception("Server returned HTTP $responseCode: ${connection.responseMessage}")
            }
            
            val fileLength = connection.contentLength
            
            connection.inputStream.use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        output.write(buffer, 0, count)
                        
                        // Emit progress
                        if (fileLength > 0) {
                            val progress = (total * 100 / fileLength).toInt()
                            emit(DownloadProgress.Progress(progress, total, fileLength.toLong()))
                        }
                    }
                }
            }
            
            Log.d(TAG, "Model downloaded successfully to ${modelFile.absolutePath}")
            emit(DownloadProgress.Complete(modelFile.absolutePath))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model: ${e.message}", e)
            emit(DownloadProgress.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get the local model path
     */
    fun getModelPath(modelConfig: ModelConfig): String {
        return File(context.filesDir, modelConfig.filename).absolutePath
    }
    
    /**
     * Check if model is downloaded
     */
    fun isModelDownloaded(modelConfig: ModelConfig): Boolean {
        return File(getModelPath(modelConfig)).exists()
    }
}

/**
 * Sealed class representing download progress states
 */
sealed class DownloadProgress {
    object Started : DownloadProgress()
    data class Progress(val percentage: Int, val downloaded: Long, val total: Long) : DownloadProgress()
    data class Complete(val filePath: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
