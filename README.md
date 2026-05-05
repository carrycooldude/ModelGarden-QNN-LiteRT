# ModelGarden-QNN-LiteRT — Gemma 4 NPU Chatbot

An on-device LLM chat application for Android, powered by **Google LiteRT-LM** and **Qualcomm NPU** acceleration. Runs **Gemma 4 2B** natively on the **Snapdragon 8 Elite** NPU for fast, private inference.

Based on the official [Google AI Edge LiteRT Samples](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/llm_chatbot_npu).

## Features

*   **NPU-First Inference**: Runs Gemma 4 2B on the Qualcomm Hexagon NPU via LiteRT Compiled Model API
*   **Backend Fallback**: NPU → GPU → CPU automatic fallback chain
*   **Multi-Model Support**: Switch between Gemma 4 2B and FastVLM 0.5B (vision) via in-app selector
*   **Real-time Benchmarks**: TTFT, tokens/sec displayed in the header
*   **ADB Push Models**: No in-app download — push `.litertlm` files via ADB
*   **Modern UI**: Material 3 design with streaming responses and chat bubbles

## Supported Models

| Model | Filename | Modality | Backend |
| :--- | :--- | :--- | :--- |
| **Gemma 4 2B** | `gemma4_2b_181450_244_sm8750.litertlm` | Text | NPU |
| **FastVLM 0.5B** | `FastVLM-0.5B.qualcomm.sm8750.litertlm` | Text + Image | NPU |

## Setup & Installation

### Prerequisites
*   Android Studio Ladybug (or newer)
*   Samsung S25 Ultra (Snapdragon 8 Elite / SM8750)
*   ~3GB free storage for the model
*   ADB configured

### 1. Clone the Repository
```bash
git clone https://github.com/carrycooldude/ModelGarden-QNN-LiteRT.git
cd ModelGarden-QNN-LiteRT
```

### 2. Build & Install the APK
```bash
./gradlew installDebug
```

### 3. Push Models via ADB

#### Gemma 4 2B (Text-only, NPU)
```bash
# Option A: If you have the compiled model locally
adb push gemma4_2b_181450_244_sm8750.litertlm /sdcard/Android/data/com.example.qnn_litertlm_gemma/files/
```

See the [NPU Compilation Guide](https://github.com/google-ai-edge/litert-samples/blob/main/compiled_model_api/qualcomm/llm_chatbot_npu/NPU_COMPILATION_GUIDE.md) for how to compile your own model for NPU.

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://github.com/google-ai-edge/litert-samples/blob/main/compiled_model_api/colab/LiteRT_AOT_Compilation_Tutorial.ipynb) — **AOT Compilation Tutorial** for compiling models to `.litertlm` format

#### FastVLM 0.5B (Vision, NPU)
```bash
# Download from HuggingFace
pip install huggingface_hub
huggingface-cli download litert-community/FastVLM-0.5B FastVLM-0.5B.qualcomm.sm8750.litertlm --local-dir .

# Push to device
adb push FastVLM-0.5B.qualcomm.sm8750.litertlm /sdcard/Android/data/com.example.qnn_litertlm_gemma/files/
```

### 4. Launch & Use
1.  **Launch the App**: Select a model from the dropdown — it initializes on NPU automatically
2.  **Chat**: Type messages and get streaming responses
3.  **Switch Models**: Use the dropdown in the header to switch between Gemma 4 and FastVLM
4.  **Benchmarks**: Watch real-time TTFT and tokens/sec in the header

## Architecture

```
app/src/main/java/com/example/qnn_litertlm_gemma/
├── MainActivity.kt        # UI, chat, audio recording, image attachment
├── LiteRTLMManager.kt     # Engine singleton, backend fallback, multimodal messaging
├── ModelDownloader.kt      # Model registry & local file resolver
├── ModelConfig.kt          # Data class for model configuration
├── ChatAdapter.kt          # RecyclerView adapter for chat messages
└── ChatMessage.kt          # Message data model

app/src/main/jniLibs/arm64-v8a/
├── libGemmaModelConstraintProvider.so
├── libLiteRtDispatch_Qualcomm.so
├── libQnnHtp.so
├── libQnnHtpV79Skel.so
├── libQnnHtpV79Stub.so
└── libQnnSystem.so
```

## Dependencies

*   **LiteRT-LM** `0.11.0-rc1` — Google's on-device LLM runtime
*   **Qualcomm QNN** — NPU dispatch libraries for Snapdragon 8 Elite

## Notes on NPU Acceleration

*   The model `.litertlm` file must be compiled specifically for the target NPU (SM8750 for S25 Ultra)
*   `LD_LIBRARY_PATH` and `ADSP_LIBRARY_PATH` are configured at runtime for QNN dispatch
*   `cacheDir` is used for JIT compilation caching on subsequent launches
*   Models compiled without `--enable_vision` / `--enable_audio` are text-only

## References

*   [Google AI Edge LiteRT Samples (Reference)](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/llm_chatbot_npu)
*   [NPU Compilation Guide](https://github.com/google-ai-edge/litert-samples/blob/main/compiled_model_api/qualcomm/llm_chatbot_npu/NPU_COMPILATION_GUIDE.md)
*   [Gemma 4 Overview](https://ai.google.dev/gemma/docs/core)
*   [LiteRT-LM Android Guide](https://ai.google.dev/edge/litert-lm/android)
*   [LiteRT Community Models on HuggingFace](https://huggingface.co/litert-community)

## Demo

https://github.com/user-attachments/assets/4c3c494e-a119-45d5-9726-4e43b2351ed9

## License
Apache 2.0
