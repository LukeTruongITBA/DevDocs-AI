
package com.devdocs.ai.ingeststatic.chunk;

public record TextChunk(
        int chunkIndex,
        int pageNumber,
        String content,
        String checksum
) {
}
