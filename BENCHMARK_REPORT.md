# On-Device Model Benchmark Report

**Date:** 2026-01-14
**Device:** Samsung Galaxy S24 Ultra (SM-S938B)
**Framework:** LiteRT-LM (with QNN/CPU Fallback)

## Overview
This report compares the performance of two Small Language Models (SLMs) running locally on the device using the custom Android application.

## Results Table

| Metric | Qwen 3 0.6B (Int4) | Gemma 3n (Int4) |
| :--- | :--- | :--- |
| **Time To First Token (TTFT)** | 690 ms | **630 ms** |
| **Generation Speed** | **~27.96 tokens/sec** | ~16.35 tokens/sec |
| **Response Length** | 1,589 chars | 3,996 chars |

## Analysis

### 1. Latency (Time To First Token)
Both models demonstrate excellent responsiveness, with **Gemma 3n** having a slight edge (60ms faster startup). This confirms that the initialization overhead and initial prefill for both models are highly optimized for mobile deployment.

### 2. Throughput (Generation Speed)
**Qwen 3 0.6B** is significantly faster, achieving nearly **28 tokens per second**. This is approximately **70% faster** than Gemma 3n.
*   **Reasoning:** Qwen 3 0.6B is a smaller model (0.6 Billion parameters) compared to Gemma 3n (likely larger, e.g., 2B-3B range). The reduced parameter count allows for faster memory access and computation on the NPU/CPU.

### 3. Output Quality and Length
*   **Gemma 3n** produced a much more comprehensive response (3,996 characters vs 1,589). It covered the topic (India) with greater depth, structure, and detail.
*   **Qwen 3 0.6B** provided a concise and accurate summary but lacked the extensive elaboration of Gemma.

## Conclusion and Recommendations

*   **For Chat & Quick Queries:** Use **Qwen 3 0.6B**. Its superior speed (28 t/s) makes it feel instant and natural for conversational interfaces where brevity is preferred.
*   **For Content Generation & Knowledge:** Use **Gemma 3n**. Despite being slower (16 t/s is still very usable), it generates higher-quality, deeply informative content suitable for complex questions.

## Technical Notes
*   **Qwen 3 Issue:** Initial GPU initialization for Qwen 3 failed on this device, requiring a fallback to CPU/NPU via the updated robustness logic. This might explain why its TTFT isn't even lower given its small size.
*   **Engine Config:** Vision/Audio backends were disabled to ensure stability for these text-only models.
