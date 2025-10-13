package com.chatbot.repository;

import com.chatbot.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
	Resource findByNameIgnoreCase(String name);
	List<Resource> findByTypeIgnoreCase(String type);
    
}
