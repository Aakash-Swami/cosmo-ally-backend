package com.chatbot.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class VectorStoreService {

    private final WebClient qdrant;
    private final String collection;
    private final EmbeddingService embeddings;

    public VectorStoreService(
        EmbeddingService embeddings,
        @Value("${rag.qdrant.url}") String qdrantUrl,
        @Value("${rag.qdrant.collection}") String collection
    ) {
        this.embeddings = embeddings;
        this.qdrant = WebClient.builder().baseUrl(qdrantUrl).build();
        this.collection = collection;
    }

    @PostConstruct
    public void ensureCollection() {
        int dim = embeddings.embeddingDimension();

        Map<String, Object> vectors = new HashMap<>();
        vectors.put("size", dim);
        vectors.put("distance", "Cosine");

        Map<String, Object> payload = new HashMap<>();
        payload.put("vectors", vectors);

        qdrant.put()
            .uri("/collections/{name}", collection)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(Map.class)
            .onErrorResume(e -> qdrant.get().uri("/collections/{name}", collection)
                .retrieve().bodyToMono(Map.class))
            .block();
    }

    public void upsert(String id, float[] vector, Map<String, Object> payload) {
        if (vector == null || vector.length == 0) return; // skip bad vectors

        Map<String, Object> point = new HashMap<>();
        point.put("id", id);
        point.put("vector", vector);
        point.put("payload", payload);

        Map<String, Object> body = new HashMap<>();
        body.put("points", List.of(point));

        try {
            qdrant.put()
                .uri("/collections/{name}/points?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        } catch (WebClientResponseException e) {
            System.err.println("Failed to upsert point: " + e.getResponseBodyAsString());
        }
    }


    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(float[] queryVector, int topK) {
        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryVector);
        body.put("limit", topK);

        Map<String, Object> resp = qdrant.post()
            .uri("/collections/{name}/points/search", collection)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (resp == null || !resp.containsKey("result")) {
            return new ArrayList<>();
        }

        return (List<Map<String, Object>>) resp.get("result");
    }
}
