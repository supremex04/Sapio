package com.ved.document.ocr;

/**
 * Conversion failed. The message is safe to show to the document owner.
 */
public class OcrException extends Exception {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
