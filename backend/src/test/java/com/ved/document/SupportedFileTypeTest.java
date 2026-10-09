package com.ved.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedFileTypeTest {

    @Test
    void resolvesTypeFromExtensionCaseInsensitively() {
        assertThat(SupportedFileType.fromFilename("Scan.JPEG")).contains(SupportedFileType.JPEG);
        assertThat(SupportedFileType.fromFilename("notes.pdf")).contains(SupportedFileType.PDF);
        assertThat(SupportedFileType.fromFilename("archive.zip")).isEmpty();
        assertThat(SupportedFileType.fromFilename("noextension")).isEmpty();
        assertThat(SupportedFileType.fromFilename("trailingdot.")).isEmpty();
    }

    @Test
    void checksMagicBytes() {
        assertThat(SupportedFileType.PDF.matchesSignature("%PDF-1.7".getBytes())).isTrue();
        assertThat(SupportedFileType.PDF.matchesSignature("MZ".getBytes())).isFalse();
        assertThat(SupportedFileType.JPEG.matchesSignature(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isTrue();
        assertThat(SupportedFileType.WEBP.matchesSignature("RIFF\0\0\0\0WEBPVP8 ".getBytes())).isTrue();
    }

    @Test
    void sanitizesFilenames() {
        assertThat(DocumentService.sanitizeFilename("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(DocumentService.sanitizeFilename("C:\\Users\\me\\scan.png")).isEqualTo("scan.png");
        assertThat(DocumentService.sanitizeFilename("  ")).isEqualTo("document");
        assertThat(DocumentService.sanitizeFilename(null)).isEqualTo("document");
    }
}
