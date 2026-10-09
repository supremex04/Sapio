package com.ved.document;

import java.util.UUID;

public record DocumentResultResponse(UUID id, String format, String content) {
}
