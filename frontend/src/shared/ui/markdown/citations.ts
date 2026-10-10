/**
 * Source citations in AI text look like "[§1.1.1]" or "[§1.2, §2.3]" (section paths in the learner's document).
 * They become `cite:` links that the Markdown renderer shows as small source chips. Within a paragraph each
 * section is cited once, at its last mention, so grounding stays visible without cluttering the reading.
 */
const CITATION = /\s*\[§\s*([\w.-]+(?:\s*[,;]\s*§?\s*[\w.-]+)*)\]/g;

export function linkCitations(markdown: string): string {
  return markdown
    .split(/(\n{2,})/)
    .map((paragraph) => {
      const matches = [...paragraph.matchAll(CITATION)];
      if (matches.length === 0) return paragraph;
      const seen = new Set<string>();
      // Walk backwards so the last mention of a section keeps its chip.
      const replacements = matches
        .reverse()
        .map((m) => {
          const ids = (m[1] ?? "")
            .split(/[,;]/)
            .map((s) => s.replace("§", "").trim())
            .filter((id) => id && !seen.has(id));
          ids.forEach((id) => seen.add(id));
          return { m, text: ids.map((id) => ` [§${id}](cite:${id})`).join("") };
        })
        .reverse();
      let out = "";
      let at = 0;
      for (const { m, text } of replacements) {
        out += paragraph.slice(at, m.index) + text;
        at = m.index! + m[0].length;
      }
      return out + paragraph.slice(at);
    })
    .join("");
}
