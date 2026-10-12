/**
 * Document text can carry simple inline HTML from PDF extraction (<mark>, <u>, <b>, <br>…). The renderer never
 * executes HTML, so these are turned into Markdown emphasis or dropped. Code (fenced or inline) is left as is.
 */
const RULES: [RegExp, string][] = [
  // Words read inside a picture (diagram labels): kept for search, but the picture itself shows them.
  [/<!--\s*Start of picture text\s*-->[\s\S]*?<!--\s*End of picture text\s*-->/gi, ""],
  [/<!--[\s\S]*?-->/g, ""],
  [/<(mark|b|strong)>([\s\S]*?)<\/\1>/gi, "**$2**"],
  [/<(i|em)>([\s\S]*?)<\/\1>/gi, "*$2*"],
  [/<\/?(u|span|font|sup|sub)\b[^>]*>/gi, ""],
  [/<br\s*\/?>/gi, "\n"],
  // Enclosed-letter symbols (U+1F130 to U+1F189) are slide bullet glyphs, not text.
  [/[\u{1F130}-\u{1F189}]\uFE0F?\s?/gu, ""],
];
const CODE = /(```[\s\S]*?```|`[^`\n]*`)/;

export function inlineHtmlToMarkdown(text: string): string {
  return text
    .split(CODE)
    .map((part, i) => (i % 2 === 1 ? part : RULES.reduce((s, [pattern, repl]) => s.replace(pattern, repl), part)))
    .join("");
}
