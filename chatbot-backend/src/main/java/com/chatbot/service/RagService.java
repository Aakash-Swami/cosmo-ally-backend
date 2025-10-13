package com.chatbot.service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chatbot.model.UploadedFile;
import com.chatbot.model.Resource;
import com.chatbot.repository.ResourceRepository;

@Service
public class RagService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;
    private final ResourceRepository resourceRepository;
    private final int chunkSize;
    private final int overlap;
    private final int topK;

    // Cache for query embeddings to avoid repeated computation
    private final Map<String, float[]> queryEmbeddingCache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public RagService(
        EmbeddingService embeddingService,
        VectorStoreService vectorStore,
        ResourceRepository resourceRepository,
        @Value("${rag.chunk.size}") int chunkSize,
        @Value("${rag.chunk.overlap}") int overlap,
        @Value("${rag.search.topK}") int topK
    ) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.resourceRepository = resourceRepository;
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        this.topK = topK;
    }

    @PostConstruct
    public void seedResourcesIntoVectors() {
        List<Resource> all = resourceRepository.findAll();
        for (Resource r : all) {
            String doc = buildResourceDoc(r);
            indexLogicalDoc(doc, Map.of(
                "type", "resource",
                "name", r.getName(),
                "driveLink", r.getDriveLink(),
                "rawType", r.getType()
            ));
        }
    }

    private String buildResourceDoc(Resource r) {
        return """
            Name: %s
            Also known as: %s
            Type: %s
            Drive Link: %s
            Description: Academic resource for %s. Use this link to open the folder: %s
            """.formatted(
                r.getName(), r.getName().toUpperCase(), r.getType(), r.getDriveLink(),
                r.getName(), r.getDriveLink()
            );
    }

    /** Index any logical document (string). Chunk + embed + upsert using UUIDs for point IDs */
    public void indexLogicalDoc(String text, Map<String,Object> payloadBase) {
        List<String> chunks = Chunker.chunk(text, chunkSize, overlap);
        for (String chunk : chunks) {
            String id = UUID.randomUUID().toString();
            float[] vec = embeddingService.embed(chunk);
            Map<String,Object> payload = new HashMap<>(payloadBase);
            payload.put("text", chunk);
            vectorStore.upsert(id, vec, payload);
        }
    }

    /** Index an uploaded text file */
    public void indexFile(UploadedFile file) {
        String content = file.getTextContent() != null
            ? file.getTextContent()
            : new String(file.getData(), StandardCharsets.UTF_8);

        Map<String,Object> payload = new HashMap<>();
        payload.put("type", "file");
        payload.put("fileName", file.getFileName());
        payload.put("fileType", file.getFileType());

        indexLogicalDoc(content, payload);
    }

    /** Retrieve topK chunks for a query (parallel + cached embeddings) */
    public List<Map<String,Object>> retrieve(String query) {
        // Get embedding from cache or compute
        float[] qv = queryEmbeddingCache.computeIfAbsent(query, embeddingService::embed);

        // Can parallelize search if vectorStore supports batch search
        try {
            CompletableFuture<List<Map<String,Object>>> future = CompletableFuture.supplyAsync(() -> vectorStore.search(qv, topK), executor);
            return future.get(3, TimeUnit.SECONDS); // timeout to prevent long waits
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /** Build compact context block (null-safe) */
    public String buildContext(String query) {
        List<Map<String,Object>> hits = retrieve(query);
        if (hits.isEmpty()) return "";

        return hits.stream()
            .map(hit -> {
                @SuppressWarnings("unchecked")
                Map<String,Object> payload = (Map<String,Object>) hit.get("payload");
                if (payload == null) payload = new HashMap<>();

                String text = String.valueOf(payload.getOrDefault("text", ""));
                String name = String.valueOf(payload.getOrDefault("name", ""));
                String drive = String.valueOf(payload.getOrDefault("driveLink", ""));

                if (!drive.isBlank()) {
                    return "**" + name + "** — " + drive + "\n" + text;
                }
                return text;
            })
            .collect(Collectors.joining("\n---\n"));
    }

    // Shutdown executor when service stops
    public void shutdown() {
        executor.shutdown();
    }
}