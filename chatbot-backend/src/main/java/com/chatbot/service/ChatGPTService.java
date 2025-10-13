package com.chatbot.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.chatbot.model.ChatMessage;
import com.chatbot.repository.ChatRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ChatGPTService {

    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private com.chatbot.repository.ResourceRepository resourceRepository;


    private final WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:11434") // Ollama local server
            .build();

    private String injectDateTime(String userMessage) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"));
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
        return """
        	    [System Info] Today is %s and the current time is %s.

        	    Please respond using:
        	    - Clear, well-structured paragraphs
        	    - Markdown formatting (bold, italics, bullet points)
        	    - Short sentences

        	    %s
        	    """.formatted(date, time, userMessage);

    }

    public String ask(String userMessage) {
    	String promptWithContext; 
    	promptWithContext = injectDateTime(userMessage);
    	


        // 🐛 Debug prompt
        System.out.println("📝 Prompt Sent to Ollama:\n" + promptWithContext);

        Mono<Map> responseMono = webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "model", "llama3.1",
                        "prompt", promptWithContext,
                        "stream", false
                ))
                .retrieve()
                .bodyToMono(Map.class);

        Map response = responseMono.block();
        String fullReply = (String) response.get("response");

        // 🐛 Debug raw response
        System.out.println("📥 Raw Response from Ollama:\n" + fullReply);

        String cleanedReply = stripThinkTag(fullReply);

        // 🐛 Debug cleaned and formatted reply
        System.out.println("✨ Cleaned Response Before Format Fix:\n" + cleanedReply);
        String formattedReply = cleanAndFormatResponse(cleanedReply);
        System.out.println("✅ Final Cleaned + Formatted Response:\n" + formattedReply);

        chatRepository.save(new ChatMessage(null, userMessage, formattedReply));
        return formattedReply;
    }


    public Flux<String> streamReply(String prompt) {
        String promptWithContext = injectDateTime(prompt);

        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "model", "llama3.1",
                        "prompt", promptWithContext,
                        "stream", true
                ))
                .retrieve()
                .bodyToFlux(String.class)
                .map(line -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        Map<String, Object> jsonMap = mapper.readValue(line, new TypeReference<>() {});
                        String responseChunk = (String) jsonMap.getOrDefault("response", "");
                        return responseChunk;
                    } catch (Exception e) {
                        System.out.println("❌ JSON parse error: " + line);
                        return "";
                    }
                })
                .filter(chunk -> !chunk.isBlank())
                .doOnNext(chunk -> System.out.println("🧠 Streamed: " + chunk));
    }

    private String stripThinkTag(String response) {
    	return response
    		    .replaceAll("(?s)<think>.*?</think>\\s*", "")   // Remove hidden thoughts
    		    .replaceAll("\\n{3,}", "\n\n")                  // Collapse triple+ line breaks
    		    .replaceAll(" +", " ")                          // Collapse double spaces
    		    .trim();
    }
    
    public String wrapInFormattedPrompt(String userQuery, String fileContext) {
    	return """
    		    You are a smart academic assistant helping a student with class schedule queries.

    		    %s

    		    Please follow these response guidelines:
    		    - Start each new idea or section on a **new line**
    		    - Keep it **precise**
    		    - Use **paragraphs** where needed
    		    - bold texts between **text** or ** text** or **text **
    		    - Respond using **markdown with bold text only**
    		    - **Do NOT use tables or bullet points**
    		    - Keep answers in 1–2 short paragraphs
    		    - Mention only the relevant class (if any) based on current time

    		    Question:
    		    %s
    		    """.formatted(fileContext.isBlank() ? "" : "Attached documents:\n" + fileContext, userQuery);

    }
    
    public static String cleanAndFormatResponse(String input) {
        if (input == null) return "";

        return input
            .replaceAll("\\s{2,}", " ")            // Replace multiple spaces with a single space
            .replaceAll("(?<=[a-zA-Z])\\s(?=[a-zA-Z])", " ") // Join words split unnecessarily
            .replaceAll("\\*{2}\\s*", "**")        // Fix bold markdown syntax like "** text"
            .replaceAll("\\s*\\*{2}", "**")        // Fix trailing space before **
            .replaceAll("(?<=\\d)\\s*-\\s*(?=\\d)", "-") // Fix time like "10 : 30 - 11 : 30"
            .replaceAll("(?<=\\d)\\s*:\\s*(?=\\d)", ":") // Fix time like "10 : 30"
            .replaceAll("(?<=\\d)(AM|PM)", " $1")   // Ensure space before AM/PM
            .replaceAll("\\s+", " ")                // Final cleanup of extra spaces
            .trim();
    }
    
    public String tryAnswerWithResources(String userMessage) {
        if (userMessage == null) return null;

        String q = userMessage.toLowerCase();
        // Simple intent check; add patterns as you like
        boolean wantsResources = q.contains("academic resources") ||
                                 q.contains("resources") ||
                                 q.contains("drive link") ||
                                 q.contains("drive folder") ||
                                 q.contains("study material");

        if (!wantsResources) return null;

        var resources = resourceRepository.findByTypeIgnoreCase("Academic");
        if (resources.isEmpty()) {
            return "No academic resources found yet.";
        }

        StringBuilder sb = new StringBuilder("Here are your academic resources:<br/><br/>");
        for (var r : resources) {
            sb.append("<a href=\"")
              .append(r.getDriveLink())
              .append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
              .append(escape(r.getName()))
              .append("</a><br/>");
        }
        return sb.toString();
    }

    // Minimal escape to avoid breaking HTML if names have < or &
    // (replace with a proper escaper if you already use one)
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }




    public List<ChatMessage> getAllChats() {
        return chatRepository.findAll();
    }
}
