package com.example.qnn_litertlm_gemma

import android.os.Bundle
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
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        liteRTLMManager = LiteRTLMManager.getInstance(this)
        modelDownloader = ModelDownloader(this)
        
        // Setup Menu for Token
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_set_token -> {
                    showTokenDialog()
                    true
                }
                else -> false
            }
        }

        setupRecyclerView()
        setupInput()
        setupModelSpinner()
    }
    
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

    private fun setupModelSpinner() {
        val models = ModelDownloader.AVAILABLE_MODELS
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            models.map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val spinner = binding.toolbar.findViewById<Spinner>(R.id.spinnerModel)
        spinner.adapter = adapter
        
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedModel = models[position]
                if (currentModel != selectedModel) {
                    currentModel = selectedModel
                    checkAndInitialize(selectedModel)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerViewMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            
            // Scroll to bottom when keyboard opens (layout shrinks)
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
        // Enable/disable send button based on input
        binding.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
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
        
        // Initially disable send button
        binding.buttonSend.isEnabled = false
    }
    
    private fun checkAndInitialize(modelConfig: ModelConfig) {
        lifecycleScope.launch {
            // Reset UI state for new model
            binding.cardInput.visibility = View.GONE
            binding.progressBarLoading.visibility = View.VISIBLE
            binding.textLoadingStatus.visibility = View.VISIBLE
            binding.textLoadingStatus.text = "Checking ${modelConfig.name}..."
            
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
                        binding.progressBarLoading.visibility = View.GONE
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
            binding.progressBarLoading.visibility = View.VISIBLE
            binding.textLoadingStatus.visibility = View.VISIBLE
            binding.textLoadingStatus.text = "Initializing ${modelConfig.name}..."
            
            val startTime = System.currentTimeMillis()
            
            val result = liteRTLMManager.initialize(
                modelDownloader.getModelPath(modelConfig),
                modelConfig.systemPrompt
            )
            
            val loadTime = System.currentTimeMillis() - startTime
            
            binding.progressBarLoading.visibility = View.GONE
            binding.textLoadingStatus.visibility = View.GONE
            
            if (result.isSuccess) {
                binding.cardInput.visibility = View.VISIBLE
                addSystemMessage("${modelConfig.name} initialized. Ready to chat!")
                binding.textBenchmark.text = "Init Time: ${loadTime}ms | Model: ${modelConfig.name}"
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                binding.textLoadingStatus.visibility = View.VISIBLE
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
        
        lifecycleScope.launch {
            var fullResponse = ""
            val requestStartTime = System.nanoTime()
            var firstTokenTime = 0L
            var tokenCount = 0 // Approximate 
            
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
                        if (firstTokenTime == 0L) {
                            firstTokenTime = System.nanoTime()
                        }
                        
                        val chunkText = messageChunk.toString()
                        fullResponse += chunkText
                        // Estimate token count roughly (e.g. 4 chars per token)
                        // Ideally we'd use a tokenizer, but for benchmark approximation:
                        tokenCount += chunkText.length / 4 + 1
                        
                        messages[assistantMessageIndex] = assistantMessage.copy(
                            content = fullResponse,
                            isStreaming = true
                        )
                        chatAdapter.notifyItemChanged(assistantMessageIndex)
                        binding.recyclerViewMessages.smoothScrollToPosition(assistantMessageIndex)
                    }
                
                // Final update when done
                messages[assistantMessageIndex] = assistantMessage.copy(
                    content = fullResponse,
                    isStreaming = false
                )
                updateMessages()
                
                // Final Stats
                val endTime = System.nanoTime()
                val ttftMs = (firstTokenTime - requestStartTime) / 1_000_000
                val generationTimeMs = (endTime - firstTokenTime) / 1_000_000
                // simple TPS calculation
                val tokens = fullResponse.length / 4.0 // Approx
                val tps = if (generationTimeMs > 0) (tokens / (generationTimeMs / 1000.0)) else 0.0
                
                binding.textBenchmark.text = String.format(
                    "TTFT: %dms | Speed: %.2f t/s (approx) | Len: %d", 
                    ttftMs, tps, fullResponse.length
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
