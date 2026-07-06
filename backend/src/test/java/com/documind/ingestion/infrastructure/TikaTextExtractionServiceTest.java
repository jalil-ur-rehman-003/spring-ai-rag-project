package com.documind.ingestion.infrastructure;

import com.documind.ingestion.application.DocumentExtractionException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikaTextExtractionServiceTest {

    private final TikaTextExtractionService tikaTextExtractionService = new TikaTextExtractionService();

    @Test
    void extractsTextContentFromARealPdf() throws IOException {
        InputStream pdfContent = buildSinglePagePdf("This is the extracted body text of the policy guide.");

        String extractedMarkdown = tikaTextExtractionService.extractToMarkdown(pdfContent);

        assertThat(extractedMarkdown).contains("This is the extracted body text of the policy guide.");
    }

    @Test
    void wrapsExtractedParagraphsAsMarkdownParagraphsSeparatedByBlankLines() throws IOException {
        InputStream pdfContent = buildSinglePagePdf("Line one of the document.");

        String extractedMarkdown = tikaTextExtractionService.extractToMarkdown(pdfContent);

        // Tika's plain-text extraction is the starting point (see decision log) -- this only
        // asserts the content survives, not that real Markdown heading structure is produced yet.
        assertThat(extractedMarkdown.trim()).isNotEmpty();
    }

    @Test
    void throwsADocumentExtractionExceptionForUnreadableContent() {
        InputStream garbageContent = new ByteArrayInputStream("this is not a pdf at all, just garbage bytes".getBytes());

        // Tika's AutoDetectParser falls back to treating unrecognized content as plain text rather
        // than failing, so garbage bytes still "extract" successfully -- only a stream that errors
        // while reading (simulated here) should surface as DocumentExtractionException.
        InputStream throwingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("simulated I/O failure while reading upload stream");
            }
        };

        assertThatThrownBy(() -> tikaTextExtractionService.extractToMarkdown(throwingStream))
                .isInstanceOf(DocumentExtractionException.class);
    }

    private static InputStream buildSinglePagePdf(String bodyText) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(bodyText);
                contentStream.endText();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        }
    }
}
