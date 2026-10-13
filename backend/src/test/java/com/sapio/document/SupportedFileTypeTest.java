package com.sapio.document;

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
    void acceptsWordAudioAndVideoBySignature() {
        assertThat(SupportedFileType.fromFilename("notes.docx")).contains(SupportedFileType.DOCX);
        assertThat(SupportedFileType.DOCX.matchesSignature(new byte[]{'P', 'K', 3, 4, 0})).isTrue();
        assertThat(SupportedFileType.MP3.matchesSignature("ID3\u0004".getBytes())).isTrue();
        assertThat(SupportedFileType.MP3.matchesSignature(new byte[]{(byte) 0xFF, (byte) 0xFB, 0})).isTrue();
        assertThat(SupportedFileType.WAV.matchesSignature("RIFF\0\0\0\0WAVEfmt ".getBytes())).isTrue();
        assertThat(SupportedFileType.WAV.matchesSignature("RIFF\0\0\0\0AVI LIST".getBytes())).isFalse();
        assertThat(SupportedFileType.MP4.matchesSignature("\0\0\0\u0018ftypmp42".getBytes())).isTrue();
        assertThat(SupportedFileType.M4A.matchesSignature("\0\0\0\u0020ftypM4A ".getBytes())).isTrue();
        assertThat(SupportedFileType.WEBM.matchesSignature(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3})).isTrue();
        assertThat(SupportedFileType.OGG.matchesSignature("OggS".getBytes())).isTrue();
        assertThat(SupportedFileType.MP4.matchesSignature("MZ\u0090\0".getBytes())).isFalse();

        assertThat(SupportedFileType.fromFilename("lecture.MP4").orElseThrow().kind())
                .isEqualTo(SupportedFileType.Kind.VIDEO);
        assertThat(SupportedFileType.kindOf("audio/mpeg")).isEqualTo(SupportedFileType.Kind.AUDIO);
        assertThat(SupportedFileType.kindOf("application/pdf").isMedia()).isFalse();
    }

    @Test
    void sanitizesFilenames() {
        assertThat(DocumentService.sanitizeFilename("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(DocumentService.sanitizeFilename("C:\\Users\\me\\scan.png")).isEqualTo("scan.png");
        assertThat(DocumentService.sanitizeFilename("  ")).isEqualTo("document");
        assertThat(DocumentService.sanitizeFilename(null)).isEqualTo("document");
    }
}
