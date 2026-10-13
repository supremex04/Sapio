package com.sapio.document;

import java.util.UUID;

public record DocumentResultResponse(UUID id, String format, String content) {
}
