package com.sapio.knowledge;

import java.util.UUID;

/**
 * The learner saved new text for a section of the reading view. After commit, the indexer embeds the section's
 * new chunks (cheap, and needed for search to see the new wording). Nothing AI-written is regenerated: summaries
 * and notes record the content version they were written from and are offered for a rewrite instead.
 */
public record SectionEdited(UUID documentId, UUID nodeId) {
}
