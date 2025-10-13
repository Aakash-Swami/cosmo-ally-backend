package com.chatbot.service;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.chatbot.model.UploadedFile;
import com.chatbot.model.User;
import com.chatbot.repository.UploadedFileRepository;
import com.chatbot.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class FileStorageService {

    @Autowired
    private UploadedFileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DropboxService dropboxService;

    @Transactional
    public UploadedFile storeFile(MultipartFile file, String username) throws Exception {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // ✅ Upload to Dropbox
        String dropboxPath = dropboxService.uploadToDropbox(file);

        // ✅ Extract text using Tika
        Tika tika = new Tika();
        String extractedText = tika.parseToString(file.getInputStream());

        // ✅ Strip extension from file name
        String originalFileName = file.getOriginalFilename();
        String baseFileName = originalFileName != null
                ? originalFileName.replaceFirst("\\.[^.]+$", "")  // Remove last extension
                : "untitled";

        // ✅ Create and save entity
        UploadedFile newFile = new UploadedFile(
            baseFileName,                             // File name without extension
            file.getContentType(),
            file.getBytes(),
            user
        );
        newFile.setDropboxLink(dropboxPath);
        newFile.setTextContent(extractedText);

        return fileRepository.save(newFile);
    }
}
