package com.demo.rag.chunk;

import java.util.ArrayList;
import java.util.List;

public class StaticSplitter {

    private final int pageSize;
    private final int overlap;

    public StaticSplitter(int pageSize, int overlap) {
        this.pageSize = pageSize;
        this.overlap = overlap;
    }

    public List<TextChunk> process(String content) {
        List<TextChunk> chunks = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return chunks;
        }

        int textSize = content.length();
        if (textSize <= pageSize) {
            chunks.add(new TextChunk()
                    .setStartIndex(0)
                    .setEndIndex(textSize)
                    .setContent(content));
            return chunks;
        }

        double targetStep = pageSize - overlap;
        int additionalChunks = (int) Math.ceil((textSize - pageSize) / targetStep);
        int totalChunks = 1 + additionalChunks;
        double exactStep = (double) (textSize - pageSize) / additionalChunks;

        for (int i = 0; i < totalChunks; i++) {
            int startIndex;
            if (i == totalChunks - 1) {
                startIndex = textSize - pageSize;
            } else {
                startIndex = (int) (i * exactStep);
            }
            int endIndex = startIndex + pageSize;
            if (endIndex > textSize) {
                endIndex = textSize;
                startIndex = Math.max(0, endIndex - pageSize);
            }
            chunks.add(new TextChunk()
                    .setStartIndex(startIndex)
                    .setEndIndex(endIndex)
                    .setContent(content.substring(startIndex, endIndex)));
        }
        return chunks;
    }
}
