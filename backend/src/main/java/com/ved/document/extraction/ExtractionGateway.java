package com.ved.document.extraction;

/**
 * Port to whatever turns a document into text (native PDF text and/or OCR). Today this is the Python
 * inference service; the backend does not know which engines run behind it.
 */
public interface ExtractionGateway {

    ExtractionResult extract(ExtractionRequest request) throws ExtractionException;
}
