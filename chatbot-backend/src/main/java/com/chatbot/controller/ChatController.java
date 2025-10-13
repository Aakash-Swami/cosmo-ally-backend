package com.chatbot.controller;

import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.chatbot.model.ChatMessage;
import com.chatbot.model.Resource;
import com.chatbot.model.UploadedFile;
import com.chatbot.repository.ChatRepository;
import com.chatbot.repository.ResourceRepository;
import com.chatbot.repository.UploadedFileRepository;
import com.chatbot.service.ChatGPTService;
import com.chatbot.service.RagService;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatGPTService chatGPTService;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private RagService ragService;

    // 🔗 Live Moodle ICS URL
    private static final String MOODLE_ICS_URL =
            "https://moodle.coep.org.in/moodle/calendar/export_execute.php?userid=25932&authtoken=214046f471911012c4e9537d0a62043b8a4615f3&preset_what=all&preset_time=custom";

    // ============================= Helpers =============================
    private String buildContextFromFiles() {
        return uploadedFileRepository.findAll().stream()
                .filter(file -> file.getTextContent() != null && !file.getTextContent().isBlank())
                .map(file -> "[FILE: " + file.getFileName() + "]\n" + file.getTextContent())
                .collect(Collectors.joining("\n\n"));
    }

    private List<String> getIcsDeadlinesFromUrl(String icsUrl, boolean allDeadlines) throws Exception {
        try (InputStream in = new URL(icsUrl).openStream()) {
            Calendar calendar = new CalendarBuilder().build(in);
            LocalDate today = LocalDate.now();

            List<Map.Entry<LocalDate, String>> events = new ArrayList<>();

            for (Object obj : calendar.getComponents("VEVENT")) {
                VEvent event = (VEvent) obj;
                String summary = event.getSummary().getValue().toLowerCase();
                String start = event.getStartDate().getValue();

                LocalDate eventDate = LocalDate.parse(start.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);

                if (!eventDate.isBefore(today) &&
                    (summary.contains("assignment") || summary.contains("deadline") || summary.contains("due"))) {
                    events.add(Map.entry(eventDate, event.getSummary().getValue()));
                }
            }

            if (events.isEmpty()) {
                return List.of("No upcoming assignments or deadlines found.");
            }

            if (allDeadlines) {
                return events.stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .toList();
            } else {
                LocalDate nextDate = events.stream()
                        .map(Map.Entry::getKey)
                        .min(LocalDate::compareTo)
                        .orElse(today);

                return events.stream()
                        .filter(e -> e.getKey().equals(nextDate))
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .toList();
            }
        }
    }

    // ============================= /chat (single reply) =============================
    @PostMapping
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body) throws Exception {
        String userMessage = body.get("message").trim().toLowerCase();

        // 1️⃣ ICS Deadlines
        if (userMessage.contains("assignment") || userMessage.contains("deadline") || userMessage.contains("due")) {
            boolean allDeadlines = userMessage.contains("all");
            List<String> deadlines = getIcsDeadlinesFromUrl(MOODLE_ICS_URL, allDeadlines);

            // Add double line breaks for proper Markdown rendering
            String reply = (allDeadlines ? "📝 All upcoming assignments/deadlines:\n\n"
                                         : "📝 Next assignment/deadline:\n\n")
                           + String.join("\n\n", deadlines);

            chatRepository.save(new ChatMessage(null, userMessage, reply));
            return ResponseEntity.ok(Map.of("reply", ChatGPTService.cleanAndFormatResponse(reply)));
        }

        // 2️⃣ File download
        if (userMessage.startsWith("download")) {
            String fileNameInput = userMessage.substring("download".length()).trim().toLowerCase();
            Optional<UploadedFile> matchedFile = uploadedFileRepository.findAll().stream()
                    .filter(file -> file.getFileName().toLowerCase().contains(fileNameInput))
                    .findFirst();

            if (matchedFile.isPresent()) {
                String encodedFileName = URLEncoder.encode(matchedFile.get().getFileName(), StandardCharsets.UTF_8);
                String reply = "**📄 File Found!**  \n\n[📥 Click here to download](http://localhost:8080/files/download/" + encodedFileName + ")";
                chatRepository.save(new ChatMessage(null, userMessage, reply));
                return ResponseEntity.ok(Map.of("reply", ChatGPTService.cleanAndFormatResponse(reply)));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ File not found: " + fileNameInput);
            }
        }

        // 3️⃣ Resource search
        List<Resource> allResources = resourceRepository.findAll();
        Optional<Resource> matchByName = allResources.stream()
                .filter(r -> r.getName() != null && userMessage.contains(r.getName().toLowerCase()))
                .findFirst();
        List<Resource> matchByType = allResources.stream()
                .filter(r -> r.getType() != null && userMessage.contains(r.getType().toLowerCase()))
                .collect(Collectors.toList());

        if (userMessage.contains("resource") || matchByName.isPresent() || !matchByType.isEmpty()) {
            StringBuilder reply = new StringBuilder();
            if (matchByName.isPresent()) {
                Resource res = matchByName.get();
                reply.append("**📂 Resource Found!**  \n\n**").append(res.getName())
                     .append("**  \n[📎 Open in Google Drive](").append(res.getDriveLink()).append(")");
            } else {
                reply.append("**📂 Resources Found!**\n\n");
                matchByType.forEach(res -> reply.append("**").append(res.getName())
                        .append("**  \n[📎 Open in Google Drive](").append(res.getDriveLink()).append(")\n\n"));
            }
            chatRepository.save(new ChatMessage(null, userMessage, reply.toString()));
            return ResponseEntity.ok(Map.of("reply", ChatGPTService.cleanAndFormatResponse(reply.toString())));
        }

        // 4️⃣ General chat (RAG + file context)
        String ragContext = ragService.buildContext(userMessage);
        String fileContext = buildContextFromFiles();
        String systemCtx = "";
        if (!ragContext.isBlank()) systemCtx += "Retrieved context:\n" + ragContext + "\n\n";
        if (!fileContext.isBlank()) systemCtx += "Attached documents:\n" + fileContext + "\n\n";

        String formattedPrompt = chatGPTService.wrapInFormattedPrompt(userMessage, systemCtx);
        String reply = chatGPTService.ask(formattedPrompt);

        // Ensure Markdown line breaks remain
        chatRepository.save(new ChatMessage(null, userMessage, ChatGPTService.cleanAndFormatResponse(reply)));

        return ResponseEntity.ok(Map.of("reply", ChatGPTService.cleanAndFormatResponse(reply)));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory() {
        return ResponseEntity.ok(chatGPTService.getAllChats());
    }

    // ============================= /stream (real-time) =============================
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody Map<String, String> body) {
        String userMessage = body.get("message").trim().toLowerCase();

        // 1️⃣ ICS Deadlines
        try {
            if (userMessage.contains("assignment") || userMessage.contains("deadline") || userMessage.contains("due")) {
                boolean allDeadlines = userMessage.contains("all");
                List<String> deadlines = getIcsDeadlinesFromUrl(MOODLE_ICS_URL, allDeadlines);
                return Flux.fromIterable(deadlines)
                        .map(e -> {
                            String msg = "📝 " + e;
                            chatRepository.save(new ChatMessage(null, userMessage, msg));
                            return msg;
                        });
            }
        } catch (Exception e) {
            return Flux.just("❌ Error reading ICS calendar: " + e.getMessage());
        }

        // 2️⃣ File download
        if (userMessage.startsWith("download")) {
            String fileNameInput = userMessage.substring("download".length()).trim().toLowerCase();
            Optional<UploadedFile> matchedFile = uploadedFileRepository.findAll().stream()
                    .filter(file -> file.getFileName().toLowerCase().contains(fileNameInput))
                    .findFirst();

            if (matchedFile.isPresent()) {
                String encodedFileName = URLEncoder.encode(matchedFile.get().getFileName(), StandardCharsets.UTF_8);
                String msg = "**📄 File Found!**  \n\n[📥 Click here to download](http://localhost:8080/files/download/" + encodedFileName + ")";
                chatRepository.save(new ChatMessage(null, userMessage, msg));
                return Flux.just(ChatGPTService.cleanAndFormatResponse(msg));
            } else {
                return Flux.just("❌ File not found: " + fileNameInput);
            }
        }

        // 3️⃣ Resource links
        List<Resource> allResources = resourceRepository.findAll();
        Optional<Resource> matchByName = allResources.stream()
                .filter(r -> r.getName() != null && userMessage.contains(r.getName().toLowerCase()))
                .findFirst();
        List<Resource> matchByType = allResources.stream()
                .filter(r -> r.getType() != null && userMessage.contains(r.getType().toLowerCase()))
                .collect(Collectors.toList());

        if (userMessage.contains("resource") || matchByName.isPresent() || !matchByType.isEmpty()) {
            StringBuilder msgBuilder = new StringBuilder();
            if (matchByName.isPresent()) {
                Resource res = matchByName.get();
                msgBuilder.append("**📂 Resource Found!**  \n\n**").append(res.getName())
                        .append("**  \n[📎 Open in Google Drive](").append(res.getDriveLink()).append(")");
            } else {
                msgBuilder.append("**📂 Resources Found!**\n\n");
                matchByType.forEach(res -> msgBuilder.append("**").append(res.getName())
                        .append("**  \n[📎 Open in Google Drive](").append(res.getDriveLink()).append(")\n\n"));
            }
            String msg = msgBuilder.toString();
            chatRepository.save(new ChatMessage(null, userMessage, msg));
            return Flux.just(ChatGPTService.cleanAndFormatResponse(msg));
        }

        // 4️⃣ General chat
        String ragContext = ragService.buildContext(userMessage);
        String fileContext = buildContextFromFiles();
        String systemCtx = "";
        if (!ragContext.isBlank()) systemCtx += "Retrieved context:\n" + ragContext + "\n\n";
        if (!fileContext.isBlank()) systemCtx += "Attached documents:\n" + fileContext + "\n\n";

        String formattedPrompt = chatGPTService.wrapInFormattedPrompt(userMessage, systemCtx);
        return chatGPTService.streamReply(formattedPrompt)
                .map(ChatGPTService::cleanAndFormatResponse)
                .doOnNext(reply -> chatRepository.save(new ChatMessage(null, userMessage, reply)));
    }
}