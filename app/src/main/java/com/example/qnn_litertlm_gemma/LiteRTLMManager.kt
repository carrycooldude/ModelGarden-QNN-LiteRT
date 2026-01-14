package com.example.qnn_litertlm_gemma

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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
     * Initialize the LiteRT-LM engine with the model
     */
    suspend fun initialize(modelPath: String, systemPrompt: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            if (isInitialized) {
                Log.d(TAG, "Re-initializing engine...")
                cleanup()
            }
            
            Log.d(TAG, "Initializing LiteRT-LM engine with path: $modelPath")
            
            // Try priority: NPU (QNN) -> GPU (OpenCL) -> CPU
            val backendsToTry = listOf(Backend.NPU, Backend.GPU, Backend.CPU)
            var success = false
            var lastError: Exception? = null
            
            for (backend in backendsToTry) {
                try {
                    Log.d(TAG, "Attempting initialization with backend: $backend")
                    initializeEngineWithBackend(modelPath, backend)
                    currentBackend = backend
                    success = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "$backend initialization failed, trying next...", e)
                    lastError = e
                    cleanup()
                }
            }
            
            if (!success) {
                throw lastError ?: Exception("Failed to initialize with any backend")
            }
            
            // Create a conversation
            val conversationConfig = ConversationConfig(
                systemMessage = Message.of(systemPrompt ?: "You are a helpful AI assistant powered by LiteRT-LM."),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)
            )
            
            conversation = engine?.createConversation(conversationConfig)
            isInitialized = true
            
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "LiteRT-LM initialized successfully on $currentBackend in ${duration}ms")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LiteRT-LM", e)
            Result.failure(e)
        }
    }
    
    private fun initializeEngineWithBackend(modelPath: String, backend: Backend) {
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = context.cacheDir.path
        )
        engine = Engine(engineConfig)
        engine?.initialize()
    }
    
    suspend fun sendMessage(messageText: String): Flow<Message> {
        if (!isInitialized || conversation == null) {
            throw IllegalStateException("LiteRT-LM not initialized.")
        }
        
        return withContext(Dispatchers.IO) {
            val userMessage = Message.of(messageText)
            conversation!!.sendMessageAsync(userMessage)
        }
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
            conversation = null
            engine = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resources", e)
        }
    }
}
