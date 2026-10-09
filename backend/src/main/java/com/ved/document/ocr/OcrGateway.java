package com.ved.document.ocr;

/**
 * Port to whatever converts a document into Markdown. Today this is the Python inference service;
 * the backend does not know (or care) which OCR model runs behind it.
 */
public interface OcrGateway {

    OcrResult extract(OcrRequest request) throws OcrException;
}
