package com.ved.document;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * File types accepted for conversion. The type is derived from the file extension (never trusting the
 * client-sent Content-Type) and then confirmed by sniffing the file's leading "magic" bytes.
 */
public enum SupportedFileType {
    PDF("application/pdf", Set.of("pdf")),
    PNG("image/png", Set.of("png")),
    JPEG("image/jpeg", Set.of("jpg", "jpeg")),
    WEBP("image/webp", Set.of("webp")),
    TIFF("image/tiff", Set.of("tif", "tiff")),
    BMP("image/bmp", Set.of("bmp"));

    private final String mediaType;
    private final Set<String> extensions;

    SupportedFileType(String mediaType, Set<String> extensions) {
        this.mediaType = mediaType;
        this.extensions = extensions;
    }

    public String mediaType() {
        return mediaType;
    }

    public static Optional<SupportedFileType> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extensions.contains(ext)).findFirst();
    }

    /** Checks the file signature so e.g. an executable renamed to .pdf is rejected. */
    public boolean matchesSignature(byte[] head) {
        return switch (this) {
            case PDF -> startsWith(head, "%PDF-".getBytes());
            case PNG -> startsWith(head, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case JPEG -> startsWith(head, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case WEBP -> head.length >= 12 && startsWith(head, "RIFF".getBytes())
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            case TIFF -> startsWith(head, new byte[]{'I', 'I', 0x2A, 0x00})
                    || startsWith(head, new byte[]{'M', 'M', 0x00, 0x2A});
            case BMP -> startsWith(head, "BM".getBytes());
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
