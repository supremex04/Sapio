package com.sapio.document.extraction;

import java.util.List;

/**
 * What was said in an audio / video recording, with times. Produced once by {@link ExtractionGateway#transcribe} and
 * kept, so turning it into notes can be retried without transcribing again.
 *
 * @param engine e.g. "faster-whisper:small"
 */
public record Transcript(String language, double durationSeconds, String engine, List<Segment> segments) {

    /** @param start seconds from the start of the recording */
    public record Segment(double start, double end, String text) {
    }
}
