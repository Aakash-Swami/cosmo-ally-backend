package com.chatbot.controller;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chatbot.model.UploadedFile;
import com.chatbot.repository.UploadedFileRepository;
import com.chatbot.service.FileStorageService;
import com.chatbot.service.RagService;

import jakarta.servlet.http.HttpServletRequest;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;

@RestController
@RequestMapping("/files")
public class FileController {

    @Autowired
    private FileStorageService fileService;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private RagService ragService;

    // 📤 Upload file and store Dropbox-style link
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("username");
            if (username == null) {
                System.out.println("❌ No username found in request attribute.");
                return ResponseEntity.badRequest().body("Username missing from request.");
            }

            System.out.println("📤 [Upload] User: " + username + " | File: " + file.getOriginalFilename());

            if (file.isEmpty()) {
                System.out.println("❌ File is empty.");
                return ResponseEntity.badRequest().body("Empty file.");
            }

            // ✅ Store the uploaded file
            UploadedFile savedFile = fileService.storeFile(file, username);

            if (savedFile == null) {
                System.out.println("❌ UploadedFile is null after storing.");
                return ResponseEntity.status(500).body("Failed to store file.");
            }

            System.out.println("✅ File saved: " + savedFile.getFileName());

            // ✅ Index file in RAG if it's text-based
            if (savedFile.getTextContent() != null 
                    || savedFile.getFileType().contains("text")
                    || savedFile.getFileType().contains("json")
                    || savedFile.getFileType().contains("csv")) {
                ragService.indexFile(savedFile);
            }

            return ResponseEntity.ok(savedFile);

        } catch (Exception e) {
            System.out.println("❌ Exception during upload: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Upload failed.");
        }
    }

    // 📥 Download endpoint
    @GetMapping("/download/{filename}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String filename, HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        System.out.println("📥 [Download] Request by: " + username + " | File requested: " + filename);

        try {
            UploadedFile file = uploadedFileRepository.findByFileNameIgnoreCase(filename)
                    .orElseThrow(() -> new RuntimeException("File not found in DB"));

            String fileType = file.getFileType(); // e.g., application/pdf
            String downloadFileName = file.getFileName();

            // ✅ Add correct extension if not already present
            String extension = getExtensionFromMimeType(fileType);
            if (extension != null && !downloadFileName.toLowerCase().endsWith(extension)) {
                downloadFileName += extension;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"")
                    .contentType(MediaType.parseMediaType(fileType))
                    .body(file.getData());

        } catch (Exception e) {
            System.out.println("❌ [Download Error] " + e.getMessage());
            return ResponseEntity.status(403).build();
        }
    }

    private String getExtensionFromMimeType(String mimeType) {
        switch (mimeType) {
            case "application/pdf": return ".pdf";
            case "image/png": return ".png";
            case "image/jpeg": return ".jpg";
            case "text/plain": return ".txt";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return ".docx";
            default: return null;
        }
    }

    @GetMapping("")
    public ResponseEntity<List<Map<String, String>>> listAllFiles() {
        List<UploadedFile> files = uploadedFileRepository.findAll();

        List<Map<String, String>> fileList = files.stream().map(file -> {
            Map<String, String> fileInfo = new HashMap<>();
            fileInfo.put("fileName", file.getFileName());
            fileInfo.put("downloadUrl", "https://www.dropbox.com/s/yourfilelink"); // Replace with real URL if needed
            return fileInfo;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(fileList);
    }

    @GetMapping("/dropbox-link/{filename}")
    public ResponseEntity<String> getDropboxLink(@PathVariable String filename) {
        return uploadedFileRepository.findByFileNameIgnoreCase(filename)
                .map(file -> {
                    if (file.getDropboxLink() == null || file.getDropboxLink().isEmpty()) {
                        return ResponseEntity.status(404).body("❌ Dropbox link not found for " + filename);
                    }
                    return ResponseEntity.ok(file.getDropboxLink());
                })
                .orElse(ResponseEntity.status(404).body("❌ File not found"));
    }
    
    
    @GetMapping("/ics-deadlines")
    public List<String> getIcsDeadlines(@RequestParam String icsUrl) throws Exception {
        // 1️⃣ Fetch ICS file directly from the URL
        URL url = new URL(icsUrl);
        try (InputStream in = url.openStream()) {
            CalendarBuilder builder = new CalendarBuilder();
            Calendar calendar = builder.build(in);

            List<String> deadlines = new ArrayList<>();
            LocalDate today = LocalDate.now();

            // 2️⃣ Iterate over events
            for (Object obj : calendar.getComponents("VEVENT")) {
                VEvent event = (VEvent) obj;
                String summary = event.getSummary().getValue().toLowerCase();
                String start = event.getStartDate().getValue(); // format: YYYYMMDD or YYYYMMDDTHHmmss

                LocalDate eventDate = LocalDate.parse(start.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);

                // 3️⃣ Filter relevant events
                if ((summary.contains("assignment") || summary.contains("deadline") || summary.contains("due"))
                        && !eventDate.isBefore(today)) {
                    deadlines.add(eventDate + ": " + event.getSummary().getValue());
                }
            }

            // 4️⃣ Handle no results
            if (deadlines.isEmpty()) {
                deadlines.add("No upcoming assignments or deadlines found.");
            }

            return deadlines;
        }
    }



    // 📄 Optional: Get file by ID
    @GetMapping("/{id}")
    public ResponseEntity<UploadedFile> getFileById(@PathVariable Long id) {
        Optional<UploadedFile> file = uploadedFileRepository.findById(id);
        return file.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    
}
