package com.chatbot.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class EmbeddingService {

    private final WebClient webClient;
    private final String model;

    public EmbeddingService(
        @Value("${rag.embeddings.ollamaUrl}") String ollamaUrl,
        @Value("${rag.embeddings.model}") String model
    ) {
        this.webClient = WebClient.builder().baseUrl(ollamaUrl).build();
        this.model = model;
    }

    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        Map<String, Object> resp = webClient.post()
            .uri("/api/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("model", model, "prompt", text))
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (resp == null || !resp.containsKey("embedding")) {
            throw new RuntimeException("Embedding API returned no data");
        }

        List<Double> values = (List<Double>) resp.get("embedding");
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i).floatValue();
        }
        return out;
    }

    /** Convenience: dimension = length of any embedding */
    public int embeddingDimension() {
        return embed("dimension-probe").length;
    }
}
