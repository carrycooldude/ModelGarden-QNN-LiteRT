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
 * Singleton manager for LiteRT-LM Engine
 * Handles model initialization and conversation management
 */
class LiteRTLMManager private constructor(private val context: Context) {
    
    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private var isInitialized = false
    
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
     * This should be called on a background thread as it can take several seconds
     */
    suspend fun initialize(modelPath: String, systemPrompt: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                Log.d(TAG, "Re-initializing engine...")
                cleanup()
            }
            
            Log.d(TAG, "Initializing LiteRT-LM engine with path: $modelPath")
            
            // Try detected backend first (GPU)
            val preferredBackend = detectBestBackend()
            Log.d(TAG, "Using preferred backend: $preferredBackend")
            
            try {
                initializeEngineWithBackend(modelPath, preferredBackend)
            } catch (e: Exception) {
                if (preferredBackend == Backend.GPU) {
                    Log.w(TAG, "GPU initialization failed, falling back to CPU", e)
                    // If GPU fails, cleanup (just in case) and retry with CPU
                    cleanup()
                    initializeEngineWithBackend(modelPath, Backend.CPU)
                } else {
                    throw e
                }
            }
            
            // Create a conversation with default configuration
            val conversationConfig = ConversationConfig(
                systemMessage = Message.of(systemPrompt ?: "You are a helpful AI assistant powered by LiteRT-LM running on device."),
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = 0.8
                )
            )
            
            conversation = engine?.createConversation(conversationConfig)
            isInitialized = true
            
            Log.d(TAG, "LiteRT-LM initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LiteRT-LM", e)
            Result.failure(e)
        }
    }
    
    private fun initializeEngineWithBackend(modelPath: String, backend: Backend) {
        // Use simpler config to avoid potential issues with multimodal backends on text-only models
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = context.cacheDir.path
        )
        
        engine = Engine(engineConfig)
        engine?.initialize()
    }
    
    /**
     * Send a message and receive streaming response
     */
    suspend fun sendMessage(messageText: String): Flow<Message> {
        if (!isInitialized || conversation == null) {
            throw IllegalStateException("LiteRT-LM not initialized. Call initialize() first.")
        }
        
        return withContext(Dispatchers.IO) {
            val userMessage = Message.of(messageText)
            conversation!!.sendMessageAsync(userMessage)
        }
    }
    
    /**
     * Detect the best available backend
     */
    private fun detectBestBackend(): Backend {
        return try {
            Log.d(TAG, "Attempting to use GPU backend...")
            Backend.GPU
        } catch (e: Exception) {
            Log.w(TAG, "GPU backend not available, falling back to CPU", e)
            Backend.CPU
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            conversation?.close()
            engine?.close()
            conversation = null
            engine = null
            isInitialized = false
            Log.d(TAG, "LiteRT-LM resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resources", e)
        }
    }
}
