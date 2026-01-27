package com.example.qnn_litertlm_gemma

import android.os.Bundle
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qnn_litertlm_gemma.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var liteRTLMManager: LiteRTLMManager
    private lateinit var modelDownloader: ModelDownloader
    
    // Store conversation history
    private val messages = mutableListOf<ChatMessage>()
    
    private var currentModel: ModelConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e("MainActivity", "!!! MainActivity.onCreate STARTED !!!")
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        liteRTLMManager = LiteRTLMManager.getInstance(this)
        modelDownloader = ModelDownloader(this)
        
        // Settings / Token Dialog
        binding.iconSettings.setOnClickListener {
            // Simple logic: click -> set token, long click -> nothing for now or model select?
            // Actually let's make it show model selection if token exists, or token dialog if not?
            // For now, simpler: Show simple dialog with options
            showSettingsDialog()
        }

        setupRecyclerView()
        setupInput()
        
        // Auto-initialize default model if ready
        val defaultModel = ModelDownloader.AVAILABLE_MODELS.first()
        currentModel = defaultModel
        checkAndInitialize(defaultModel)
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Set HF Token", "Select Model (Coming Soon)")
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                 when (which) {
                     0 -> showTokenDialog()
                     1 -> Toast.makeText(this, "Only Gemma 3n supported currently", Toast.LENGTH_SHORT).show()
                 }
            }
            .show()
    }

    // setupModelSpinner removed (Model selection fixed to Gemma 3n for now per user request)

    private fun showTokenDialog() {
        val input = EditText(this)
        input.hint = "hf_..."
        val currentToken = modelDownloader.getToken()
        if (!currentToken.isNullOrBlank()) {
            input.setText(currentToken)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Set Hugging Face Token")
            .setMessage("Enter your API token to access gated models.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val token = input.text.toString().trim()
                modelDownloader.saveToken(token)
                Toast.makeText(this, "Token saved!", Toast.LENGTH_SHORT).show()
                // Retry download if current model failed?
                if (currentModel != null && !modelDownloader.isModelDownloaded(currentModel!!)) {
                   checkAndInitialize(currentModel!!)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerViewMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            
            // Scroll to bottom when keyboard opens
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom) {
                    binding.recyclerViewMessages.postDelayed({
                        if (messages.isNotEmpty()) {
                            binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
                        }
                    }, 100)
                }
            }
        }
    }
    
    private fun setupInput() {
        // Enable/disable send button
        binding.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Change button color/alpha?
                binding.buttonSend.alpha = if (!s.isNullOrBlank()) 1.0f else 0.5f
                binding.buttonSend.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        binding.buttonSend.setOnClickListener {
            val text = binding.editTextMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.editTextMessage.text?.clear()
            }
        }
        
        binding.buttonSend.alpha = 0.5f
        binding.buttonSend.isEnabled = false
    }
    
    private fun checkAndInitialize(modelConfig: ModelConfig) {
        lifecycleScope.launch {
            // Reset UI state for new model
            binding.cardInput.visibility = View.GONE
            binding.layoutLoading.visibility = View.VISIBLE
            binding.layoutLoading.visibility = View.VISIBLE
            binding.textLoadingStatus.text = "Checking ${modelConfig.name}..."
            
            // Update Header
            binding.textModelName.text = modelConfig.name
            
            if (modelDownloader.isModelDownloaded(modelConfig)) {
                initializeEngine(modelConfig)
            } else {
                downloadModel(modelConfig)
            }
        }
    }
    
    private fun downloadModel(modelConfig: ModelConfig) {
        lifecycleScope.launch {
            binding.textLoadingStatus.text = "Downloading ${modelConfig.name}..."
            
            modelDownloader.downloadModel(modelConfig).collect { progress ->
                when (progress) {
                    is DownloadProgress.Started -> {
                         binding.textLoadingStatus.text = "Starting download..."
                    }
                    is DownloadProgress.Progress -> {
                        binding.textLoadingStatus.text = "Downloading: ${progress.percentage}%"
                    }
                    is DownloadProgress.Complete -> {
                        binding.textLoadingStatus.text = "Download complete!"
                        initializeEngine(modelConfig)
                    }
                    is DownloadProgress.Error -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.textLoadingStatus.text = "Error: ${progress.message}"
                        
                        // Suggest token if 401/403 or generic error
                        if (progress.message.contains("401") || progress.message.contains("403") || progress.message.contains("404")) {
                             AlertDialog.Builder(this@MainActivity)
                                .setTitle("Download Error")
                                .setMessage("Failed: ${progress.message}.\n\nDo you need to set a Hugging Face Token?")
                                .setPositiveButton("Set Token") { _, _ -> showTokenDialog() }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                             Toast.makeText(this@MainActivity, "Download failed: ${progress.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
    
    private fun initializeEngine(modelConfig: ModelConfig) {
        lifecycleScope.launch {
            binding.layoutLoading.visibility = View.VISIBLE
            binding.textLoadingStatus.text = "Initializing ${modelConfig.name}..."
            
            val startTime = System.currentTimeMillis()
            
            val result = liteRTLMManager.initialize(
                modelDownloader.getModelPath(modelConfig),
                modelConfig.systemPrompt,
                false, // isEmbedding
                modelConfig.preferredBackend
            )
            
            val loadTime = System.currentTimeMillis() - startTime
            
            binding.layoutLoading.visibility = View.GONE
            
            if (result.isSuccess) {
                binding.cardInput.visibility = View.VISIBLE
                val backend = liteRTLMManager.getActiveBackendName()
                val memory = liteRTLMManager.getMemoryUsageMb()
                addSystemMessage("${modelConfig.name} initialized on $backend.")
                
                binding.textBackendStatus.text = "Gemma"
                // binding.textBenchmarkStats.text = "Ready" // Already set in XML
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                binding.layoutLoading.visibility = View.VISIBLE
                binding.textLoadingStatus.text = "Initialization failed: $error"
                Toast.makeText(this@MainActivity, "Init failed: $error", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun sendMessage(text: String) {
        // Add user message to UI
        val userMessage = ChatMessage(MessageSender.USER, text)
        messages.add(userMessage)
        updateMessages()
        
        // Prepare assistant message placeholder
        val assistantMessageIndex = messages.size
        val assistantMessage = ChatMessage(MessageSender.ASSISTANT, "", isStreaming = true)
        messages.add(assistantMessage)
        updateMessages()
        
        var firstTokenReceived = false
        var startTime = System.currentTimeMillis()
        var ttft: Long = 0
        var tokenCount = 0

        lifecycleScope.launch {
            var fullResponse = ""
            val requestStartTime = System.nanoTime()
            var firstTokenTime = 0L
            // var tokenCount = 0 // Approximate - moved above
            
            try {
                liteRTLMManager.sendMessage(text)
                    .catch { e ->
                        // Handle error in stream
                        messages[assistantMessageIndex] = assistantMessage.copy(
                            content = "Error: ${e.message}",
                            isStreaming = false
                        )
                        updateMessages()
                    }
                    .collect { messageChunk ->
                        if (!firstTokenReceived) { // Changed condition
                            ttft = System.currentTimeMillis() - startTime
                            firstTokenReceived = true
                            // Reset timer for throughput
                            startTime = System.currentTimeMillis()
                            firstTokenTime = System.nanoTime() // Kept for original benchmark calculation
                        }
                        
                        val chunkText = messageChunk.toString()
                        fullResponse += chunkText
                        // Estimate token count roughly (e.g. 4 chars per token)
                        // Ideally we'd use a tokenizer, but for benchmark approximation:
                        tokenCount = fullResponse.length / 4 + 1 // Updated tokenCount logic
                        
                        messages[assistantMessageIndex] = assistantMessage.copy(
                            content = fullResponse,
                            isStreaming = true
                        )
                        chatAdapter.notifyItemChanged(assistantMessageIndex)
                        binding.recyclerViewMessages.smoothScrollToPosition(assistantMessageIndex) // Used binding and smoothScroll
                        
                        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                        val speed = if (elapsed > 0) String.format("%.2f", tokenCount / elapsed) else "0.00"
                        val backend = liteRTLMManager.getActiveBackendName() // Reverted to original manager
                        val memory = liteRTLMManager.getMemoryUsageMb() // Reverted to original manager
                        
                        binding.textBenchmarkStats.text = "TTFT: ${ttft}ms | Speed: $speed t/s"
                        // binding.textBackendStatus.text = "NPU Active" // Keep static or update only if changed
                    }
                
                // Final update when done
                messages[assistantMessageIndex] = assistantMessage.copy(
                    content = fullResponse,
                    isStreaming = false
                )
                updateMessages()
                
                // Final Stats (original calculation, adjusted to use new tokenCount and ttft)
                val endTime = System.nanoTime()
                val ttftMs = (firstTokenTime - requestStartTime) / 1_000_000 // Kept original TTFT calculation
                val generationTimeMs = (endTime - firstTokenTime) / 1_000_000
                // simple TPS calculation
                val tokens = fullResponse.length / 4.0 // Approx
                val tps = if (generationTimeMs > 0) (tokens / (generationTimeMs / 1000.0)) else 0.0
                
                val backend = liteRTLMManager.getActiveBackendName()
                val memory = liteRTLMManager.getMemoryUsageMb()
                
                binding.textBenchmarkStats.text = String.format(
                    "TTFT: %dms | Speed: %.2f t/s", 
                    ttftMs, tps
                )
                
            } catch (e: Exception) {
                messages[assistantMessageIndex] = assistantMessage.copy(
                    content = "Error sending message: ${e.message}",
                    isStreaming = false
                )
                updateMessages()
            }
        }
    }
    
    private fun addSystemMessage(text: String) {
        messages.add(ChatMessage(MessageSender.SYSTEM, text))
        updateMessages()
    }
    
    private fun updateMessages() {
        chatAdapter.submitList(messages.toList())
        if (messages.isNotEmpty()) {
            binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        liteRTLMManager.cleanup()
    }
}
