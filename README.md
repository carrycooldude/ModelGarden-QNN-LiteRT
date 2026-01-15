# ModelGarden-QNN-LiteRT Android Chat

A premium on-device LLM chat application for Android, powered by **Google LiteRT (formerly TensorFlow Lite)**. Now supporting multiple Small Language Models (SLMs) including **Gemma 3n** and **Qwen 3**.

**Gemma 3n** is Google's latest family of models enabling efficient AI on everyday devices.
*   **"E2B"**: Effective ~2 Billion parameters.
*   **Performance**: Capable of 30-50+ tokens/sec on modern mobile processors.

**Qwen 3 0.6B** is a highly efficient, compact model from the LiteRT Community.
*   **Ultra-Lightweight**: Only 0.6 Billion parameters.
*   **High Speed**: Extremely fast on-device inference suitable for instant chat.

**Gemma 3 1B** is an efficient, compact model from Google.
*   **1B**: 1 Billion parameters.
*   **Versatile**: Pretty efficient model useful for a number of use-cases



## 🚀 Features

*   **Multi-Model Support**: Switch between **Gemma 3n (Int4)** and **Qwen 3 0.6B (Int4)** on the fly using the toolbar spinner.
*   **Built-in Benchmarking**: Real-time display of **Time To First Token (TTFT)**, **Generation Speed (tokens/sec)**, and response length.
*   **Secure Downloads**: 
    *   Models are downloaded directly within the app.
    *   **Hugging Face Token** support for accessing gated/private models.
*   **LiteRT-LM Engine**: Latest Google AI Edge runtime with robust fallback (GPU -> CPU) to ensure stability across devices.
*   **Modern Premium UI**:
    *   Deep Blue & Soft Gray aesthetic.
    *   Streaming responses with performance metrics.
    *   Custom vector avatars and markdown support.

## 📊 Benchmarks (Samsung S24 Ultra)

| Metric | Qwen 3 0.6B (Int4) | Gemma 3n (Int4) |
| :--- | :--- | :--- |
| **Time To First Token** | ~690 ms | ~630 ms |
| **Generation Speed** | **~28 tokens/sec** | ~16 tokens/sec |
| **Use Case** | Quick Chat, Speed | Depth, Reasoning |

## 🛠️ Setup & Installation

### Prerequisites
*   Android Studio Ladybug (or newer).
*   Android Device (Android 10+ recommended).
*   ~2GB free storage.

### 1. Clone the Repository
```bash
git clone https://github.com/carrycooldude/ModelGarden-QNN-LiteRT.git
cd ModelGarden-QNN-LiteRT
```

### 2. Build & Install
Open the project in Android Studio and run:
```bash
./gradlew installDebug
```

### 3. Usage
1.  **Launch the App**: The app will check for the default model (Gemma 3n).
2.  **Download on Device**: If the model is missing, the app will attempt to download it automatically.
    *   *Note:* If you see a 401/403/404 error, click the menu (three dots) -> **Set HF Token** and enter your Hugging Face API token.
3.  **Switch Models**: Use the dropdown in the top bar to try **Qwen 3**.
4.  **Benchmark**: Watch the green text above the input bar to see how fast your device runs!

## ⚠️ Notes on Hardware Acceleration
*   The app attempts to use **GPU** delegates by default.
*   If GPU initialization fails (common with some model architectures on specific SoCs), it automatically falls back to **CPU**, which is slower but more compatible.
*   QNN (NPU) support is experimental and depends on specific device binaries.

## 🎥 Demo

https://github.com/user-attachments/assets/4c3c494e-a119-45d5-9726-4e43b2351ed9


## 📜 License
Apache 2.0
