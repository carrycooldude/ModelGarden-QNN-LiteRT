package com.example.qnn_litertlm_gemma

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Data class for model performance metrics
 */
data class PerformanceMetrics(
    val initializationTimeMs: Long = 0,
    val timeToFirstTokenMs: Long = 0,
    val tokensPerSecond: Double = 0.0,
    val activeBackend: String = "Unknown",
    val memoryUsageMb: Long = 0
)

/**
 * Singleton manager for LiteRT-LM Engine
 * Handles model initialization and conversation management
 */
class LiteRTLMManager private constructor(private val context: Context) {
    
    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private var isInitialized = false
    private var currentBackend: Backend = Backend.CPU
    
    // Standard LiteRT components disabled
    
    companion object {
        private const val TAG = "LiteRTLMManager"
        
        @Volatile
        private var INSTANCE: LiteRTLMManager? = null
        
        fun getInstance(context: Context): LiteRTLMManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiteRTLMManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Initialize the LiteRT-LM Engine with the specified model
     */
    suspend fun initialize(modelPath: String, systemPrompt: String? = null, isEmbedding: Boolean = false, preferredBackend: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.e(TAG, "!!! LiteRTLMManager.initialize STARTED for: $modelPath !!!")
        if (isInitialized) {
            cleanup()
        }
        
        try {
            Log.e(TAG, "Checking backend availability...")
            if (isEmbedding) {
                initializeStandardLiteRT(modelPath)
            } else {
                val initialBackend = if (preferredBackend != null) {
                    try {
                        Backend.valueOf(preferredBackend.uppercase())
                    } catch (e: Exception) {
                        Log.w(TAG, "Invalid preferred backend: $preferredBackend, defaulting to GPU")
                        Backend.GPU
                    }
                } else {
                    Log.e(TAG, "No preference, defaulting to GPU for generative model")
                    Backend.GPU
                }
                
                Log.e(TAG, "Initializing with initial backend: $initialBackend")
                initializeEngineWithRotation(modelPath, initialBackend)
            }
            isInitialized = true
            Log.e(TAG, "!!! LiteRTLMManager.initialize COMPLETED SUCCESSFULLY !!!")
            Result.success(true)
        } catch (e: Throwable) {
            Log.e(TAG, "!!! INITIALIZATION CRITICAL FAILURE !!!: ${e.message}", e)
            Result.failure(Exception(e))
        }
    }

    private fun initializeStandardLiteRT(modelPath: String) {
        // Standard LiteRT disabled in 0.8.0 downgrade due to API incompatibility
        Log.e(TAG, "Standard LiteRT initialization disabled in 0.8.0")
        currentBackend = Backend.CPU
    }
    
    // Updated to support rotation starting from any backend
    private fun initializeEngineWithRotation(modelPath: String, backend: Backend) {
        try {
            initializeEngineLimit(modelPath, backend)
        } catch (e: Throwable) {
            Log.w(TAG, "Backend $backend failed: ${e.message}. Attempting fallback...", e)
            
            // Define fallback chain based on what failed
            val nextBackend = when (backend) {
                Backend.NPU -> Backend.GPU
                Backend.GPU -> Backend.CPU
                Backend.CPU -> null // End of line
            }
            
            if (nextBackend != null) {
                Log.i(TAG, "Falling back to $nextBackend")
                initializeEngineWithRotation(modelPath, nextBackend)
            } else {
                Log.e(TAG, "All backends failed. Last error: ${e.message}", e)
                throw e
            }
        }
    }

    private fun initializeEngineLimit(modelPath: String, backend: Backend) {
        val file = File(modelPath)
        Log.e(TAG, "Checking model file: ${file.absolutePath}")
        if (!file.exists()) {
            throw java.io.FileNotFoundException("Model file not found at $modelPath")
        }
        if (!file.canRead()) {
            throw java.io.IOException("Model file exists but cannot be read (permissions?)")
        }
        Log.e(TAG, "Model file valid. Size: ${file.length()} bytes. Backend: $backend")

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend
        )
        
        Log.e(TAG, "Instantiating Engine with backend: $backend")
        val candidateEngine = Engine(engineConfig) // Constructor might throw
        Log.e(TAG, "Initializing Engine explicitly (0.8.0 requirement)...")
        candidateEngine.initialize()
        
        Log.e(TAG, "Engine instance created, verifying conversation support...")
        try {
            val testConv = candidateEngine.createConversation(ConversationConfig())
            testConv.close()
            Log.e(TAG, "!!! Engine verified successfully with backend: $backend !!!")
        } catch (cvError: Throwable) {
            Log.e(TAG, "Conversation verification failed for $backend: ${cvError.message}")
            candidateEngine.close()
            throw cvError 
        }

        engine = candidateEngine
        currentBackend = backend
    }

    /**
     * Start a new conversation
     */
    fun startConversation() {
        if (!isInitialized || engine == null) {
            throw IllegalStateException("Engine not initialized.")
        }
        
        val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(
                temperature = 0.7,
                topK = 40,
                topP = 0.9
            )
        )
        conversation = engine?.createConversation(conversationConfig)
    }

    fun sendMessage(text: String): Flow<String> {
        if (!isInitialized || engine == null) {
            throw IllegalStateException("Engine not initialized.")
        }
        
        if (conversation == null) {
            startConversation()
        }
        // Efficient extraction from Message contents
        // 0.8.0 API: Message.of(text)
        val message = Message.of(text)
        return conversation!!.sendMessageAsync(message).map { msg ->
             msg.toString()
        }
    }

    suspend fun computeEmbedding(text: String): FloatArray = withContext(Dispatchers.IO) {
         // Embeddings disabled
         FloatArray(768)
    }

    fun getActiveBackendName(): String = currentBackend.name

    fun getMemoryUsageMb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
    
    fun cleanup() {
        try {
            conversation?.close()
            engine?.close()
            // compiledModel?.close()
            // environment?.close()
            
            conversation = null
            engine = null
            // compiledModel = null
            // environment = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
