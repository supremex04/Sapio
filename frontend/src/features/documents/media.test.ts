import { describe, expect, it } from "vitest";
import { pipelineState, pipelineSteps, workStage } from "./pipeline";
import { isMediaFile, lengthLabel, MAX_FILE_BYTES, MAX_MEDIA_BYTES, spanLabel, uploadLimit } from "./types";

describe("media documents", () => {
  it("allow larger uploads for recordings", () => {
    expect(isMediaFile("Lecture 3.MP4")).toBe(true);
    expect(isMediaFile("essay.docx")).toBe(false);
    expect(uploadLimit("talk.m4a")).toBe(MAX_MEDIA_BYTES);
    expect(uploadLimit("notes.pdf")).toBe(MAX_FILE_BYTES);
  });

  it("measure recordings in time, documents in pages", () => {
    expect(lengthLabel({ kind: "VIDEO", pageCount: 48, durationSeconds: 2890 })).toBe("48m");
    expect(lengthLabel({ kind: "DOCUMENT", pageCount: 1, durationSeconds: null })).toBe("1 page");
    expect(spanLabel(3, 8, false)).toBe("pp. 3–8");
    expect(spanLabel(13, 19, true)).toBe("12:00–19:00");
    expect(spanLabel(61, 62, true)).toBe("1:00:00–1:02:00");
  });

  it("describe listening rather than reading", () => {
    const doc = { status: "EXTRACTING" as const, textPages: null, retryAt: null, kind: "AUDIO" as const };
    expect(pipelineSteps(doc)[1]).toBe("Listening");
    expect(pipelineState(doc).label).toBe("Listening and taking notes");
    expect(workStage(doc).lines[0]).toContain("Listening");
    expect(pipelineSteps({ ...doc, kind: "DOCUMENT" })[1]).toBe("Reading text");
  });
});
