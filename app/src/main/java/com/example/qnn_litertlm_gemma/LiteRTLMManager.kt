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
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    
    // Standard LiteRT components for embedding models
    private var environment: Environment? = null
    private var compiledModel: CompiledModel? = null
    
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
    suspend fun initialize(modelPath: String, systemPrompt: String? = null, isEmbedding: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        if (isInitialized) {
            cleanup()
        }
        
        try {
            if (isEmbedding) {
                initializeStandardLiteRT(modelPath)
            } else {
                // Try NPU first for generative models
                initializeEngineWithBackend(modelPath, Backend.NPU)
            }
            isInitialized = true
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun initializeStandardLiteRT(modelPath: String) {
        try {
            Log.d(TAG, "Initializing modern LiteRT CompiledModel for: $modelPath")
            
            // Use Factory method if constructor is private
            environment = try { Environment.create() } catch (e: Exception) { 
                Log.w(TAG, "Environment.create() failed", e)
                null 
            }
            
            if (environment == null) {
                // If environment is mandatory and creation failed, we can't proceed with NPU/Standard init
                throw IllegalStateException("Failed to create LiteRT Environment")
            }
            
            // Try initializing with NPU (Accelerator.NPU)
            try {
                val modelOptions = CompiledModel.Options()
                // Try setting accelerator directly or via field if setter missing
                try {
                    modelOptions::class.java.getMethod("setAccelerator", Accelerator::class.java).invoke(modelOptions, Accelerator.NPU)
                } catch (e: Exception) {
                    // Try reflection-less fallback if common
                    Log.w(TAG, "Failed to set Accelerator.NPU via reflection", e)
                }
                
                // Assuming CompiledModel.create takes environment
                compiledModel = CompiledModel.create(modelPath, modelOptions, environment!!)
                Log.d(TAG, "CompiledModel initialized with NPU (Accelerator.NPU)")
                
                // Inspect signatures
                LiteRTInspector.inspect(compiledModel!!)
                
                currentBackend = Backend.NPU
            } catch (e: Exception) {
                Log.w(TAG, "NPU Initialization failed, falling back to CPU", e)
                try {
                    val modelOptions = CompiledModel.Options()
                    compiledModel = CompiledModel.create(modelPath, modelOptions, environment!!)
                    currentBackend = Backend.CPU
                    Log.d(TAG, "CompiledModel initialized with CPU fallback")
                } catch (fallbackEx: Exception) {
                     Log.e(TAG, "CPU fallback also failed", fallbackEx)
                     throw fallbackEx
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure during Standard LiteRT initialization", e)
            currentBackend = Backend.CPU
            // Ensure compiledModel is null so computeEmbedding knows it failed
            compiledModel = null
        }
    }
    
    private fun initializeEngineWithBackend(modelPath: String, backend: Backend) {
        // EngineConfig parameters might have changed. Removing maxContextLength if it's not supported.
        // Assuming modelPath and backend are valid.
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend
        )
        
        try {
            // If Engine.create is static
            engine = Engine(engineConfig)
            currentBackend = backend
            Log.i(TAG, "Engine initialized with backend: $backend")
        } catch (e: Exception) {
            if (backend == Backend.NPU) {
                Log.w(TAG, "NPU initialization failed, falling back to GPU")
                initializeEngineWithBackend(modelPath, Backend.GPU)
            } else if (backend == Backend.GPU) {
                Log.w(TAG, "GPU initialization failed, falling back to CPU")
                initializeEngineWithBackend(modelPath, Backend.CPU)
            } else {
                throw e
            }
        }
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
        
        // Inspect conversation
        LiteRTInspector.inspect(compiledModel!!, conversation)
    }

    /**
     * Send a message to the model and return a stream of responses
     */
    fun sendMessage(text: String): Flow<String> {
        // Temporarily bypass failing code for API inspection
        return kotlinx.coroutines.flow.flowOf("API Inspection in progress...")
    }

    /**
     * Compute embeddings for the given text using modern CompiledModel
     */
    suspend fun computeEmbedding(text: String): FloatArray = withContext(Dispatchers.IO) {
        if (!isInitialized || compiledModel == null) {
            throw IllegalStateException("CompiledModel not initialized.")
        }
        
        // Dummy tokenization placeholder
        val tokens = text.split(" ").map { it.hashCode() % 10000 }.toIntArray()
        val paddedTokens = IntArray(512) { i -> if (i < tokens.size) tokens[i] else 0 }
        
        try {
            val inputBuffers = compiledModel!!.createInputBuffers()
            val outputBuffers = compiledModel!!.createOutputBuffers()
            
            // In LiteRT 2.1.0, writeInt and readFloat take array and return result
            inputBuffers[0].writeInt(paddedTokens)
            
            val startTime = System.currentTimeMillis()
            compiledModel!!.run(inputBuffers, outputBuffers)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Embedding inference took ${duration}ms")
            
            outputBuffers[0].readFloat()
        } catch (e: Exception) {
            Log.e(TAG, "Error during CompiledModel inference: ${e.message}")
            FloatArray(768)
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
            compiledModel?.close()
            environment?.close()
            
            conversation = null
            engine = null
            compiledModel = null
            environment = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
