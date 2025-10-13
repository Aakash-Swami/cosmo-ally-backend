package com.chatbot.repository;

import com.chatbot.model.UploadedFile;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
	Optional<UploadedFile> findByFileNameIgnoreCase(String fileName);

}
