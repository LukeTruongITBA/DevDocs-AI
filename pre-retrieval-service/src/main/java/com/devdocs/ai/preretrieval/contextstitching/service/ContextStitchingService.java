package com.devdocs.ai.preretrieval.contextstitching.service;

import com.devdocs.ai.preretrieval.contextstitching.dto.ChunkCitation;
import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;
import com.devdocs.ai.preretrieval.reranking.dto.RerankedChunk;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ContextStitchingService {

    public List<StitchedContext> stitch(List<RerankedChunk> rerankedChunks) {
        if (rerankedChunks == null) {
            throw new IllegalArgumentException(
                    "Reranked chunks must not be null"
            );
        }

        List<RerankedChunk> sortedChunks = new ArrayList<>(rerankedChunks);

        for (RerankedChunk rerankedChunk : sortedChunks) {
            if (rerankedChunk == null || rerankedChunk.chunk() == null) {
                throw new IllegalArgumentException(
                        "Reranked chunk and its chunk data must not be null"
                );
            }

            VectorSearchResult chunk = rerankedChunk.chunk();

            if (chunk.documentId() == null
                    || chunk.versionId() == null
                    || chunk.chunkIndex() == null) {
                throw new IllegalArgumentException(
                        "Document ID, version ID and chunk index must not be null"
                );
            }
        }

        sortedChunks.sort(
                Comparator.comparing(
                                (RerankedChunk item) ->
                                        item.chunk().documentId()
                        )
                        .thenComparing(
                                item -> item.chunk().versionId()
                        )
                        .thenComparing(
                                item -> item.chunk().chunkIndex()
                        )
        );

        List<List<RerankedChunk>> groups = new ArrayList<>();

        for (RerankedChunk current : sortedChunks) {
            if (groups.isEmpty()) {
                List<RerankedChunk> firstGroup = new ArrayList<>();
                firstGroup.add(current);
                groups.add(firstGroup);
                continue;
            }

            List<RerankedChunk> lastGroup =
                    groups.get(groups.size() - 1);

            RerankedChunk previous =
                    lastGroup.get(lastGroup.size() - 1);

            if (isContiguous(previous.chunk(), current.chunk())) {
                lastGroup.add(current);
            } else {
                List<RerankedChunk> newGroup = new ArrayList<>();
                newGroup.add(current);
                groups.add(newGroup);
            }
        }

        List<RankedContext> rankedContexts = new ArrayList<>();

        for (List<RerankedChunk> group : groups) {
            StitchedContext context = createStitchedContext(group);

            int bestOriginalRank = group.stream()
                    .map(RerankedChunk::originalRank)
                    .filter(Objects::nonNull)
                    .min(Integer::compareTo)
                    .orElse(Integer.MAX_VALUE);

            rankedContexts.add(
                    new RankedContext(context, bestOriginalRank)
            );
        }

        rankedContexts.sort(
                Comparator.comparingInt(RankedContext::bestOriginalRank)
        );

        return rankedContexts.stream()
                .map(RankedContext::context)
                .toList();
    }

    private boolean isContiguous(
            VectorSearchResult previous,
            VectorSearchResult current
    ) {
        return Objects.equals(
                        previous.documentId(),
                        current.documentId()
                )
                && Objects.equals(
                        previous.versionId(),
                        current.versionId()
                )
                && current.chunkIndex() == previous.chunkIndex() + 1;
    }

    private StitchedContext createStitchedContext(
            List<RerankedChunk> group
    ) {
        VectorSearchResult first = group.get(0).chunk();
        VectorSearchResult last =
                group.get(group.size() - 1).chunk();

        String content = group.stream()
                .map(item -> item.chunk().content())
                .filter(Objects::nonNull)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        List<ChunkCitation> citations = group.stream()
                .map(item -> {
                    VectorSearchResult chunk = item.chunk();

                    return new ChunkCitation(
                            chunk.id(),
                            chunk.chunkIndex(),
                            chunk.pageNumber(),
                            chunk.sourceUri(),
                            chunk.documentTitle(),
                            chunk.versionLabel(),
                            chunk.metadata()
                    );
                })
                .toList();

        return new StitchedContext(
                first.documentId(),
                first.versionId(),
                first.documentTitle(),
                first.versionLabel(),
                first.sourceUri(),
                first.chunkIndex(),
                last.chunkIndex(),
                content,
                citations
        );
    }

    private record RankedContext(
            StitchedContext context,
            int bestOriginalRank
    ) {
    }
}