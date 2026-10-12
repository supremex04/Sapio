package com.ved.common.web;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Correlation id of the current unit of work (an HTTP request, or the background job it started). Kept in the
 * logging MDC, so it appears on every log line, is sent to the inference service and is echoed in error bodies:
 * one learner action can be followed across services.
 */
public final class RequestId {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    /** Ids we accept from callers: safe to log and echo. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private RequestId() {
    }

    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }

    /** The caller's id when it is well-formed, otherwise a new one. */
    static String accept(String incoming) {
        return incoming != null && VALID.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString().replace("-", "");
    }

    /** Carries the submitting thread's MDC (and so its request id) into async workers. */
    public static TaskDecorator propagatingDecorator() {
        return task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        };
    }
}
