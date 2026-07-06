package com.documind.ingestion.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits Markdown-converted document text into chunks for embedding.
 * Strategy: split along heading boundaries first (so retrieval can cite
 * "Chapter 3 > Section 3.2" rather than an arbitrary token window), then
 * fall back to token-count splitting with overlap for any section that's
 * still too large on its own -- overlap preserves context across a forced
 * split that heading structure alone couldn't avoid.
 */
@Service
public class ChunkingService {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");

    private final int maxTokensPerChunk;
    private final int overlapTokens;

    public ChunkingService(
            @Value("${documind.ingestion.chunking.max-tokens-per-chunk}") int maxTokensPerChunk,
            @Value("${documind.ingestion.chunking.overlap-tokens}") int overlapTokens
    ) {
        this.maxTokensPerChunk = maxTokensPerChunk;
        this.overlapTokens = overlapTokens;
    }

    public List<DocumentChunkDraft> chunk(String markdown) {
        List<DocumentChunkDraft> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (MarkdownSection section : splitIntoHeadingSections(markdown)) {
            for (String sectionPart : splitIfOverTokenLimit(section.body())) {
                chunks.add(new DocumentChunkDraft(chunkIndex++, section.headingPath(), sectionPart));
            }
        }

        return chunks;
    }

    /** One record per top-level pass over the document: either the untitled preamble, or a heading and everything under it until the next heading of equal-or-higher level. */
    private record MarkdownSection(String headingPath, String body) {
    }

    private List<MarkdownSection> splitIntoHeadingSections(String markdown) {
        List<MarkdownSection> sections = new ArrayList<>();
        Matcher headingMatcher = MARKDOWN_HEADING_PATTERN.matcher(markdown);

        List<String> headingPathStack = new ArrayList<>();
        int previousHeadingLevel = 0;
        int previousSectionBodyStart = 0;
        String previousHeadingPath = null;

        while (headingMatcher.find()) {
            int headingLevel = headingMatcher.group(1).length();
            String headingText = headingMatcher.group(2).trim();

            String precedingBody = markdown.substring(previousSectionBodyStart, headingMatcher.start()).trim();
            addSectionIfNonBlank(sections, previousHeadingPath, precedingBody);

            adjustHeadingPathStack(headingPathStack, headingLevel, previousHeadingLevel);
            headingPathStack.add(headingText);

            previousHeadingLevel = headingLevel;
            previousHeadingPath = String.join(" > ", headingPathStack);
            previousSectionBodyStart = headingMatcher.end();
        }

        String finalBody = markdown.substring(previousSectionBodyStart).trim();
        addSectionIfNonBlank(sections, previousHeadingPath, finalBody);

        return sections;
    }

    private void adjustHeadingPathStack(List<String> headingPathStack, int currentLevel, int previousLevel) {
        // Popping back to the current heading's depth handles both "new sibling" (pop one) and
        // "returning from a deeper subsection" (pop several) with the same logic.
        int levelsToPop = Math.max(0, headingPathStack.size() - (currentLevel - 1));
        for (int i = 0; i < levelsToPop && !headingPathStack.isEmpty(); i++) {
            headingPathStack.remove(headingPathStack.size() - 1);
        }
    }

    private void addSectionIfNonBlank(List<MarkdownSection> sections, String headingPath, String body) {
        if (!body.isBlank()) {
            sections.add(new MarkdownSection(headingPath, body));
        }
    }

    private List<String> splitIfOverTokenLimit(String sectionBody) {
        String[] tokens = sectionBody.trim().split("\\s+");
        if (tokens.length <= maxTokensPerChunk) {
            return List.of(sectionBody.trim());
        }

        List<String> parts = new ArrayList<>();
        int stepSize = Math.max(1, maxTokensPerChunk - overlapTokens);

        for (int startIndex = 0; startIndex < tokens.length; startIndex += stepSize) {
            int endIndex = Math.min(startIndex + maxTokensPerChunk, tokens.length);
            parts.add(String.join(" ", java.util.Arrays.copyOfRange(tokens, startIndex, endIndex)));
            if (endIndex == tokens.length) {
                break;
            }
        }

        return parts;
    }
}
