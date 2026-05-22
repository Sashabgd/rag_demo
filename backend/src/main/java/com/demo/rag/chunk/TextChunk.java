package com.demo.rag.chunk;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TextChunk {
    private int startIndex;
    private int endIndex;
    private String content;
}
