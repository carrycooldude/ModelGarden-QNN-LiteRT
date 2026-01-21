# LiteRT NPU Integration Error Report

This report summarizes the sequence of errors encountered while integrating **EmbeddingGemma 300M** with NPU acceleration on the Samsung Galaxy S25 Ultra.

## 1. Initial Build & Configuration Errors

### 1.1 Unresolved References
- **Error**: `Unresolved reference 'litert'` and `Unresolved reference 'close'`
- **Context**: Occurred when trying to use LiteRT 1.4.1 without full API dependencies.
- **Resolution**: Attempted to add `litert-api` and switched versions, eventually identifying that `litert` 2.1.0 was missing essential `Interpreter` classes in Maven.

### 1.2 QNN API Mismatch
- **Error**: `Unresolved reference 'HTP'`
- **Context**: In `LiteRTLMManager.kt`, `QnnDelegate.Options.BackendType.HTP` was used.
- **Resolution**: Corrected to `QnnDelegate.Options.BackendType.HTP_BACKEND` based on the Qualcomm QNN Delegate API.

---

## 2. Runtime Compatibility Errors

### 2.1 Operation Code Out of Range
- **Error**: `Internal error: Cannot create interpreter: Op builtin_code out of range: 206.`
- **Evidence**:
![TFLite Version Error](docs/images/tflite_version_error.jpg)
- **Root Cause**: TensorFlow Lite 2.14.0 was too old for the EmbeddingGemma model, which uses newer op codes (206).
- **Resolution**: Upgraded dependency to `org.tensorflow:tensorflow-lite:2.17.0` (latest stable).

### 2.2 Model File Corruption
- **Error**: `The model is not a valid Flatbuffer buffer`
- **Evidence**:
![Corruption Error](docs/images/model_corruption_error.jpg)
- **Root Cause**: The model file was corrupted during the initial `adb push` transfer (likely due to shell truncation or interruption).
- **Resolution**: Re-downloaded the model via Hugging Face CLI and re-transferred it using a robust path (`adb push` to `/data/local/tmp` followed by `run-as cp`).

---

## 3. Advanced LiteRT Compatibility Error (Current Blocker)

### 3.1 Unresolved Custom Op: DISPATCH_OP
- **Error**: `Encountered unresolved custom op: DISPATCH_OP. Node number 0 (DISPATCH_OP) failed to prepare.`
- **Evidence**:
![DISPATCH_OP Error](docs/images/dispatch_op_error.jpg)
- **Root Cause**: The **EmbeddingGemma 300M** model is a modern LiteRT model that uses the `DISPATCH_OP` for NPU offloading. This op is **not supported** by the standard TensorFlow Lite runtime (`org.tensorflow:tensorflow-lite`).
- **Current Status**: We must successfully build with the true **LiteRT** runtime (`com.google.ai.edge.litert:litert`) to support this internal operation, as standard TFLite cannot "see" the implementation of this custom dispatcher.

---

## Summary of Resolution Attempts

| Error Type | Resolution Step | Status |
| :--- | :--- | :--- |
| Build Errors | Corrected QNN Enum & Imports | ✅ Fixed |
| Version Mismatch | Upgraded TFLite to 2.17.0 | ✅ Fixed |
| Corruption | Verified Re-transfer | ✅ Fixed |
| Runtime Op | Switch to LiteRT Runtime | 🔄 In Progress |
