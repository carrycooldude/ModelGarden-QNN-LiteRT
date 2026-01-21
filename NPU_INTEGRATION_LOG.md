# NPU Integration Technical Log: EmbeddingGemma 300M

This document tracks the technical journey of enabling NPU acceleration for generative and embedding models on the Samsung Galaxy S25 Ultra (Snapdragon 8 Gen 4).

## 🛠 Project Objective
Successfully deploy `embeddinggemma-300m` using LiteRT with Qualcomm QNN delegation, achieving high-performance on-device inference.

---

## 🛑 Technical Hurdles & Resolutions

### 1. Build Layer: QNN API Incompatibility
- **Symptom**: `Unresolved reference 'HTP'` during Kotlin compilation.
- **Cause**: Using `BackendType.HTP` which was either deprecated or incorrectly namespaced in the latest QNN delegate AAR.
- **Fix**: Updated to `BackendType.HTP_BACKEND`.
- **Status**: ✅ **Resolved**

### 2. Dependency Layer: Op Code 206 Mismatch
- **Symptom**: `Internal error: Cannot create interpreter: Op builtin_code out of range: 206.`
- **Visual Evidence**:
![TFLite Version Error](docs/images/tflite_version_error.jpg)
- **Cause**: The model was compiled with a newer version of the TFLite schema than the 2.14.0 runtime provided.
- **Fix**: Upgraded project to **TensorFlow Lite 2.17.0** (Jan 2025 release).
- **Status**: ✅ **Resolved**

### 3. Storage Layer: Model Corruption
- **Symptom**: `The model is not a valid Flatbuffer buffer`.
- **Visual Evidence**:
![Corruption Error](docs/images/model_corruption_error.jpg)
- **Cause**: `adb push` transfer to the app's `files/` directory failed silently or was truncated, resulting in an invalid model header.
- **Fix**: Re-downloaded via HF CLI and used a multi-stage `adb` transfer (`/sdcard/` -> `run-as cp`).
- **Status**: ✅ **Resolved**

### 4. Runtime Layer: `DISPATCH_OP` Blocker (Current)
- **Symptom**: `Encountered unresolved custom op: DISPATCH_OP. Node number 0 (DISPATCH_OP) failed to prepare.`
- **Visual Evidence**:
![DISPATCH_OP Error](docs/images/dispatch_op_error.jpg)
- **Cause**: Standard TensorFlow Lite (`org.tensorflow:tensorflow-lite`) does not recognize the internal `DISPATCH_OP` helper used by modern LiteRT-distributed models for QNN offloading.
- **Requirement**: Switch to the official **LiteRT** runtime libraries (`com.google.ai.edge.litert:litert`).
- **Status**: 🔄 **Active Fix in Progress**

---

## 📋 Current System State

| Component | Version/State |
| :--- | :--- |
| **SoC** | Snapdragon 8 Gen 4 (SM8750) |
| **QNN Delegate** | 2.42.0 |
| **Primary Runtime** | TFLite 2.17.0 (Upgrading to LiteRT 1.4.1+) |
| **Model** | EmbeddingGemma 300M (176MB) |
| **NPU State** | HTP Backend Requested |

---

## 🚀 Next Steps
1. **Remove** all `org.tensorflow` dependencies.
2. **Implement** `com.google.ai.edge.litert` core and API dependencies.
3. **Refine** `LiteRTLMManager.kt` imports to strictly use the LiteRT namespace.
4. **Deploy** and verify `DISPATCH_OP` resolution on S25 Ultra.
