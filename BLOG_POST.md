# Benchmarking the Edge: Gemma 3n vs. Qwen 3 on Android with LiteRT-LM

In the rapidly evolving world of Large Language Models (LLMs), the focus is shifting from massive cloud-based monsters to lean, efficient **Small Language Models (SLMs)** designed to run entirely on-device. 

Recently, I built **ModelGarden-QNN-LiteRT**, an Android application that serves as a technical playground for comparing these models. Today, I'm deep-diving into the unique architectures of these models and the framework that makes on-device AI a reality.

---

## 🏗️ The Framework: Google LiteRT-LM

To run these models, we use **LiteRT-LM**, Google's dedicated on-device AI runtime. Unlike standard TFLite, LiteRT-LM is specialized for the sequence-to-sequence nature of LLMs.

### Architectural Pillars of LiteRT-LM:
*   **Singleton Engine & Stateful Sessions**: The framework uses a shared "Engine" to manage the heavy model weights in memory. However, it supports multiple "Sessions," allowing for independent chat histories (KV Caching) without duplicating the model.
*   **Intelligent KV Caching**: It implements copy-on-write and context-switching optimizations for the Key-Value (KV) cache, enabling sub-second Time To First Token (TTFT).
*   **Adaptive Backend Strategy**: It provides a unified API for CPU, GPU (OpenCL), and NPU (Qualcomm QNN). Our app implements a **GPU -> CPU fallback** to handle architecture-specific acceleration failures gracefully.

---

## 💎 Model Deep-Dive: Gemma 3n (The Nested Giant)

**Gemma 3n** is Google's state-of-the-art mobile-first model. Its "n" stands for **Nested**, reflecting two major architectural innovations:

### 1. MatFormer (Matryoshka Transformer)
Inspired by the Russian nesting dolls, MatFormer allows a single model (like the 4B version) to contain smaller, fully functional sub-models (like the 2B version). 
*   **Why it matters**: Developers can train one "Universal" model and extract different sizes for different hardware tiers without separate training.
*   **Elastic Inference**: In our app, we use the 2B sub-model, which is lightweight yet inherits the complex reasoning capabilities of its larger parent.

### 2. Per-Layer Embeddings (PLE)
Traditional models keep all token embeddings in high-speed VRAM/V-cache.
*   **The Innovation**: PLE moves token embeddings to the CPU, processing them in parallel with the GPU transformer layers.
*   **Result**: This dramatically reduces the memory footprint on the accelerator. Gemma 3n can run with as little as **2GB-3GB of RAM**, while performing like a much larger model.

---

## ⚡ Model Deep-Dive: Qwen 3 0.6B (The Compact Speedster)

**Qwen 3** from Alibaba is a masterclass in "Dense" architecture scaling. Despite having only 600 million parameters, it punches significantly above its weight.

### 1. Grouped Query Attention (GQA)
Standard transformers use Multi-Head Attention, which is memory-intensive. 
*   **The Qwen Approach**: GQA shares "Keys" and "Values" across multiple "Query" heads. 
*   **Impact**: This slashes memory bandwidth requirements, which is the #1 bottleneck on mobile phones, enabling the blazing **28 t/s** we observed.

### 2. Dual-Mode Reasoning ("Thinking")
Qwen 3 features an internal "thinking" mode. It can generate internal reasoning chains before outputting the final answer.
*   **The Structure**: It uses 28 transformer blocks with SwiGLU activation and RoPE (Rotary Positional Embeddings), allowing it to handle contexts up to **32k tokens**—rare for a model this small.

---

## 📊 The Showdown: Benchmarking Results

We tested both models on a **Samsung Galaxy S24 Ultra**:

| Metric | Qwen 3 0.6B | Gemma 3n |
| :--- | :--- | :--- |
| **TTFT (Latency)** | 690 ms | **630 ms** |
| **Throughput (Speed)** | **~28 tokens/sec** | ~16 tokens/sec |
| **Architecture** | Dense + GQA | Nested (MatFormer) + PLE |

### Performance Analysis
1.  **Gemma 3n (The Knowledge Base)**: Thanks to PLE, Gemma generates incredibly detailed and detailed responses (nearly 4000 characters in our test). It is the choice for comprehensive information.
2.  **Qwen 3 (The Conversationalist)**: Qwen's GQA-driven throughput (28 t/s) makes it feel near-instant. It is perfect for fast, iterative chat.

---

## 🚀 Conclusion

By combining **LiteRT-LM**'s efficient runtime with the **Nested Architecture** of Gemma and the **Dense Efficiency** of Qwen, we've created a versatile "Model Garden." On-device AI is no longer a compromise—it's a choice between specialized architectures.

👉 [**Explore the Project on GitHub**](https://github.com/carrycooldude/ModelGarden-QNN-LiteRT)

---
*Built with Google LiteRT-LM & Android Material Design.*
