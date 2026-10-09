package com.ved.document.ocr;

public record OcrResult(String markdown, String engine, Integer pageCount) {
}
