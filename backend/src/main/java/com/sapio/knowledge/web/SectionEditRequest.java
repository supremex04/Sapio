package com.sapio.knowledge.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A section's new text, as Markdown (figures as {@code ![caption](figure:<id>)}).
 *
 * @param revision the section revision the edit started from (see {@link KnowledgeResponses.ContentSection})
 */
public record SectionEditRequest(@NotBlank @Size(max = 400) String title, @NotNull @Size(max = 200_000) String markdown,
                                 @Min(0) int revision) {
}
