package com.sapio.document;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * File types accepted for conversion. The type is derived from the file extension (never trusting the
 * client-sent Content-Type) and then confirmed by sniffing the file's leading "magic" bytes.
 *
 * <p>Documents are parsed into text; audio and video are transcribed (only the audio track is used) and turned into
 * study notes.
 */
public enum SupportedFileType {
    PDF("application/pdf", Kind.DOCUMENT, "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Kind.DOCUMENT, "docx"),
    PNG("image/png", Kind.DOCUMENT, "png"),
    JPEG("image/jpeg", Kind.DOCUMENT, "jpg", "jpeg"),
    WEBP("image/webp", Kind.DOCUMENT, "webp"),
    TIFF("image/tiff", Kind.DOCUMENT, "tif", "tiff"),
    BMP("image/bmp", Kind.DOCUMENT, "bmp"),

    MP3("audio/mpeg", Kind.AUDIO, "mp3"),
    M4A("audio/mp4", Kind.AUDIO, "m4a"),
    AAC("audio/aac", Kind.AUDIO, "aac"),
    WAV("audio/wav", Kind.AUDIO, "wav"),
    OGG("audio/ogg", Kind.AUDIO, "ogg", "oga", "opus"),
    FLAC("audio/flac", Kind.AUDIO, "flac"),

    MP4("video/mp4", Kind.VIDEO, "mp4", "m4v"),
    MOV("video/quicktime", Kind.VIDEO, "mov"),
    WEBM("video/webm", Kind.VIDEO, "webm"),
    MKV("video/x-matroska", Kind.VIDEO, "mkv"),
    AVI("video/x-msvideo", Kind.VIDEO, "avi");

    /** What the pipeline does with the file. */
    public enum Kind {
        DOCUMENT,
        AUDIO,
        VIDEO;

        public boolean isMedia() {
            return this != DOCUMENT;
        }
    }

    private final String mediaType;
    private final Kind kind;
    private final Set<String> extensions;

    SupportedFileType(String mediaType, Kind kind, String... extensions) {
        this.mediaType = mediaType;
        this.kind = kind;
        this.extensions = Set.of(extensions);
    }

    public String mediaType() {
        return mediaType;
    }

    public Kind kind() {
        return kind;
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

    /** The kind of a stored document, from its content type (documents keep the type they were uploaded as). */
    public static Kind kindOf(String contentType) {
        return Arrays.stream(values()).filter(t -> t.mediaType.equals(contentType)).findFirst()
                .map(SupportedFileType::kind).orElse(Kind.DOCUMENT);
    }

    /** Checks the file signature so e.g. an executable renamed to .pdf is rejected. */
    public boolean matchesSignature(byte[] head) {
        return switch (this) {
            case PDF -> startsWith(head, ascii("%PDF-"));
            case DOCX -> startsWith(head, new byte[]{'P', 'K', 0x03, 0x04}); // a ZIP package (OOXML)
            case PNG -> startsWith(head, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case JPEG -> startsWith(head, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case WEBP -> riff(head, "WEBP");
            case TIFF -> startsWith(head, new byte[]{'I', 'I', 0x2A, 0x00})
                    || startsWith(head, new byte[]{'M', 'M', 0x00, 0x2A});
            case BMP -> startsWith(head, ascii("BM"));
            case MP3 -> startsWith(head, ascii("ID3")) || mpegFrame(head);
            case AAC -> head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xF6) == 0xF0; // ADTS
            case M4A, MP4, MOV -> isoMedia(head);
            case WAV -> riff(head, "WAVE");
            case AVI -> riff(head, "AVI ");
            case OGG -> startsWith(head, ascii("OggS"));
            case FLAC -> startsWith(head, ascii("fLaC"));
            case WEBM, MKV -> startsWith(head, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}); // EBML
        };
    }

    /** ISO base media (MP4 / M4A / MOV): a box type at offset 4. */
    private static boolean isoMedia(byte[] head) {
        if (head.length < 8) {
            return false;
        }
        String box = new String(head, 4, 4, StandardCharsets.US_ASCII);
        return Set.of("ftyp", "moov", "mdat", "wide", "free", "skip").contains(box);
    }

    /** An MPEG audio frame header without an ID3 tag. */
    private static boolean mpegFrame(byte[] head) {
        return head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0;
    }

    private static boolean riff(byte[] head, String format) {
        return head.length >= 12 && startsWith(head, ascii("RIFF"))
                && new String(head, 8, 4, StandardCharsets.US_ASCII).equals(format);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
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
