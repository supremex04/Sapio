package com.sapio.document.extraction;

/**
 * Port to whatever turns an upload into text and structure: documents (native PDF / Word text and OCR) and recordings
 * (speech-to-text, then study notes). Today this is the Python inference service; the backend does not know which
 * engines run behind it.
 */
public interface ExtractionGateway {

    /** A document (PDF, Word, image) → text, tree and figures. */
    ExtractionResult extract(ExtractionRequest request) throws ExtractionException;

    /** An audio or video recording → what was said, with times. Slow (minutes for a lecture). */
    Transcript transcribe(ExtractionRequest request) throws ExtractionException;

    /** A transcript → study notes as the document's tree; its raw text is the transcript. Needs the AI. */
    ExtractionResult notes(String title, Transcript transcript) throws ExtractionException;
}
