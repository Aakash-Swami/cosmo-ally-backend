package com.chatbot.service;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class DropboxServiceImpl implements DropboxService {

    private final DbxClientV2 dropboxClient;

    public DropboxServiceImpl(@Value("${dropbox.access.token}") String accessToken) {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("cosmo-ally-app").build();
        this.dropboxClient = new DbxClientV2(config, accessToken);
    }

    @Override
    public String uploadToDropbox(MultipartFile file) throws Exception {
        String dropboxPath = "/uploads/" + file.getOriginalFilename();
        try (InputStream in = file.getInputStream()) {
            FileMetadata metadata = dropboxClient.files()
                .uploadBuilder(dropboxPath)
                .withMode(WriteMode.OVERWRITE)
                .uploadAndFinish(in);
            System.out.println("✅ Dropbox upload complete: " + metadata.getPathDisplay());
            return metadata.getPathDisplay();
        } catch (Exception e) {
            System.err.println("❌ Dropbox upload error: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public InputStream downloadFromDropbox(String dropboxPath) throws Exception {
        System.out.println("📁 [Dropbox Download] Attempting path: " + dropboxPath);
        return dropboxClient.files().download(dropboxPath).getInputStream();
    }

    @Override
    public List<Metadata> listAllFiles(String dropboxFolderPath) throws Exception {
        List<Metadata> files = new ArrayList<>();
        ListFolderResult result = dropboxClient.files().listFolder(dropboxFolderPath);
        while (true) {
            for (Metadata entry : result.getEntries()) {
                System.out.println("Found file in Dropbox: " + entry.getPathDisplay());
                files.add(entry);
            }
            if (!result.getHasMore()) break;
            result = dropboxClient.files().listFolderContinue(result.getCursor());
        }
        return files;
    }
}
