package com.chatbot.model;

import jakarta.persistence.*;

@Entity
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userMessage;
    
    @Lob
    @Column(name = "bot_reply")
    private String botReply;

    // Default constructor (required by JPA)
    public ChatMessage() {}

    // Constructor used in your service
    public ChatMessage(Long id, String userMessage, String botReply) {
        this.id = id;
        this.userMessage = userMessage;
        this.botReply = botReply;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getUserMessage() {
        return userMessage;
    }
    
   
    public String getBotReply() {
        return botReply;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public void setBotReply(String botReply) {
        this.botReply = botReply;
    }

    // Optional toString() (for debugging)
    @Override
    public String toString() {
        return "ChatMessage{" +
                "id=" + id +
                ", userMessage='" + userMessage + '\'' +
                ", botReply='" + botReply + '\'' +
                '}';
    }
}
