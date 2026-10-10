package com.ved.knowledge;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Knowledge-store tunings ({@code ved.knowledge.*} in application.yml). See docs/guides/configuration.md.
 *
 * @param figureUrlSecret HMAC key for figure image URLs
 * @param figureUrlTtl    how long a signed figure URL stays valid (on top of hour alignment)
 */
@Validated
@ConfigurationProperties("ved.knowledge")
public record KnowledgeProperties(@NotBlank String figureUrlSecret, @NotNull Duration figureUrlTtl,
                                  @Valid @NotNull Retrieval retrieval, @Valid @NotNull Indexing indexing,
                                  @Valid @NotNull Reading reading) {

    /**
     * @param candidates results taken from each ranker (keyword, vector) before fusion
     * @param rrfK       Reciprocal Rank Fusion constant; higher flattens the influence of top ranks
     * @param figureMinSimilarity cosine similarity a figure needs to count as a semantic match
     */
    public record Retrieval(@Positive int candidates, @Positive int rrfK,
                            @DecimalMin("-1") @DecimalMax("1") double figureMinSimilarity) {
    }

    /**
     * Study units of the reading view: the sections that get Expand / Revision. A unit is the highest section whose
     * whole subtree has between {@code unitMinChars} and {@code unitMaxChars} of text (keep the max below
     * ved.study.context.notes.chars, so a unit's rewrite sees all of its text).
     */
    public record Reading(@Positive int unitMinChars, @Positive int unitMaxChars) {
    }

    /** Items per inference call / per committed batch while indexing. */
    public record Indexing(@Positive int figureBatch, @Positive int summaryBatch, @Positive int chunkEmbedBatch,
                           @Positive int figureEmbedBatch, @Positive int summaryTextChars) {
    }
}
