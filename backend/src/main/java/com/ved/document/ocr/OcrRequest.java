package com.ved.document.ocr;

import org.springframework.core.io.Resource;

public record OcrRequest(Resource content, String filename, String contentType) {
}
