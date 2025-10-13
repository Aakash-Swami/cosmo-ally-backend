package com.chatbot.service;

import java.util.ArrayList;
import java.util.List;

public class Chunker {
    public static List<String> chunk(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null) return chunks;
        text = text.replaceAll("\\s+", " ").trim();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start = Math.max(end - overlap, 0);
        }
        return chunks;
    }
}
