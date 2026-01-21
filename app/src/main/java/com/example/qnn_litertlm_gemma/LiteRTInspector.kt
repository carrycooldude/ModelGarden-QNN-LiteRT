package com.example.qnn_litertlm_gemma

import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Conversation
import android.util.Log

object LiteRTInspector {
    fun inspect(compiledModel: CompiledModel, conversation: Conversation? = null) {
        Log.d("LiteRTInspector", "--- CompiledModel Signatures ---")
        try {
            compiledModel::class.java.methods.forEach {
                if (it.name.contains("signature", ignoreCase = true) || it.name.contains("tensor", ignoreCase = true)) {
                    Log.d("LiteRTInspector", "CompiledModel Method: ${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})")
                }
            }
            
            if (conversation != null) {
                Log.d("LiteRTInspector", "--- Conversation Signatures ---")
                conversation::class.java.methods.forEach {
                    Log.d("LiteRTInspector", "Conversation Method: ${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})")
                }
                
                Log.d("LiteRTInspector", "--- Message Signatures ---")
                Message::class.java.methods.forEach {
                    Log.d("LiteRTInspector", "Message Method: ${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})")
                }
            }
            
            // Inspect buffers
            val inBuffers = compiledModel.createInputBuffers()
            val outBuffers = compiledModel.createOutputBuffers()
            
            if (inBuffers.isNotEmpty()) {
                val buf = inBuffers[0]
                Log.d("LiteRTInspector", "--- TensorBuffer Signatures ---")
                buf::class.java.methods.forEach {
                    Log.d("LiteRTInspector", "TensorBuffer Method: ${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }}) -> ${it.returnType.simpleName}")
                }
            }
            
            inBuffers.forEachIndexed { i, buf ->
                Log.d("LiteRTInspector", "Input Tensor $i")
            }
            outBuffers.forEachIndexed { i, buf ->
                Log.d("LiteRTInspector", "Output Tensor $i")
            }
            
        } catch (e: Exception) {
            Log.e("LiteRTInspector", "Error inspecting CompiledModel", e)
        }
    }
}
