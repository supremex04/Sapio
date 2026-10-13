package com.sapio.document.extraction;

import org.springframework.core.io.Resource;

public record ExtractionRequest(Resource content, String filename, String contentType) {
}
