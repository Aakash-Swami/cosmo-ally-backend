package com.chatbot.service;

import java.io.InputStream;
import java.util.List;

import com.dropbox.core.v2.files.Metadata;
import org.springframework.web.multipart.MultipartFile;

public interface DropboxService {
    String uploadToDropbox(MultipartFile file) throws Exception;
    InputStream downloadFromDropbox(String dropboxPath) throws Exception;
    List<Metadata> listAllFiles(String dropboxFolderPath) throws Exception; // ✅ NEW
}
