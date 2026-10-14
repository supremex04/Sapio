package com.sapio.knowledge.edit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;

/**
 * Turns a section's edited Markdown back into the knowledge store's shape, changing as little as possible:
 * <ul>
 *   <li>{@link #parse} splits it into paragraph blocks (fenced code, display math and tables stay whole) and
 *       takes out the figures, {@code ![caption](figure:<id>)}, remembering where they were;</li>
 *   <li>{@link #plan} keeps every existing chunk whose paragraphs are all still there, unchanged and in order
 *       (with its id and embedding), and packs only the new or changed paragraphs into new chunks, the way the
 *       extraction pipeline does (inference {@code parsing/tree.py});</li>
 *   <li>{@link #changedChars} measures how much text actually changed, so small edits can be told apart from
 *       rewrites.</li>
 * </ul>
 * A typo fix therefore replaces one chunk, and only that chunk is embedded again.
 */
public final class SectionMarkdown {

    static final int TARGET_CHARS = 900;
    static final int MAX_CHARS = 1400;

    private static final Pattern FIGURE = Pattern.compile("!\\[[^\\]\\n]*]\\(figure:([0-9a-fA-F-]{36})\\)");
    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(`{3,}|~{3,})");

    private SectionMarkdown() {
    }

    /**
     * @param blocks  the text's paragraph blocks, in order
     * @param figures figure id → index of the block it follows (-1: before any text), in reading order
     */
    public record Parsed(List<String> blocks, Map<UUID, Integer> figures) {
    }

    /** An existing chunk of the section. */
    public record Existing(UUID id, String content) {
    }

    /** A chunk of the new text: an existing one kept as is, or a new one; it covers blocks [from, to]. */
    public record Planned(UUID id, boolean kept, String text, int from, int to) {
    }

    public static Parsed parse(String markdown, Set<UUID> ownFigures) {
        List<String> blocks = new ArrayList<>();
        Map<UUID, Integer> figures = new LinkedHashMap<>();
        for (String block : blocks(markdown.replace("\r\n", "\n").replace('\r', '\n'))) {
            Matcher m = FIGURE.matcher(block);
            StringBuilder text = new StringBuilder();
            int last = 0;
            List<UUID> placed = new ArrayList<>();
            while (m.find()) {
                text.append(block, last, m.start());
                last = m.end();
                UUID id = UUID.fromString(m.group(1));
                if (!ownFigures.contains(id)) {
                    throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                            "A figure in this text does not belong to this section");
                }
                placed.add(id);
            }
            text.append(block.substring(last));
            String clean = text.toString().strip();
            if (!clean.isEmpty()) {
                blocks.add(clean);
            }
            int after = blocks.size() - 1;
            placed.forEach(id -> figures.putIfAbsent(id, after));
        }
        return new Parsed(blocks, figures);
    }

    /** Keeps unchanged existing chunks (in order) and packs the remaining blocks into new chunks. */
    public static List<Planned> plan(List<String> blocks, List<Existing> existing) {
        List<List<String>> old = existing.stream().map(e -> textBlocks(e.content())).toList();
        Map<String, List<Integer>> byFirstBlock = new HashMap<>();
        for (int c = 0; c < old.size(); c++) {
            if (!old.get(c).isEmpty()) {
                byFirstBlock.computeIfAbsent(old.get(c).getFirst(), k -> new ArrayList<>()).add(c);
            }
        }
        List<Planned> planned = new ArrayList<>();
        int pendingFrom = 0;
        int nextOld = 0;
        int i = 0;
        while (i < blocks.size()) {
            int match = -1;
            for (int c : byFirstBlock.getOrDefault(blocks.get(i), List.of())) {
                List<String> candidate = old.get(c);
                if (c >= nextOld && i + candidate.size() <= blocks.size()
                        && blocks.subList(i, i + candidate.size()).equals(candidate)) {
                    match = c;
                    break;
                }
            }
            if (match < 0) {
                i++;
                continue;
            }
            pack(blocks, pendingFrom, i, planned);
            int to = i + old.get(match).size() - 1;
            planned.add(new Planned(existing.get(match).id(), true, existing.get(match).content(), i, to));
            i = to + 1;
            pendingFrom = i;
            nextOld = match + 1;
        }
        pack(blocks, pendingFrom, blocks.size(), planned);
        return planned;
    }

    /**
     * Characters changed between two versions of a section's text: blocks are aligned (longest common
     * subsequence); an edited block counts only its changed middle, an added or removed one its whole length.
     */
    public static int changedChars(List<String> before, List<String> after) {
        int n = before.size();
        int m = after.size();
        if ((long) n * m > 1_000_000) {
            return Math.max(length(before), length(after)); // huge rewrite: no need to be precise
        }
        int[][] lcs = new int[n + 1][m + 1];
        for (int a = n - 1; a >= 0; a--) {
            for (int b = m - 1; b >= 0; b--) {
                lcs[a][b] = before.get(a).equals(after.get(b)) ? lcs[a + 1][b + 1] + 1
                        : Math.max(lcs[a + 1][b], lcs[a][b + 1]);
            }
        }
        int changed = 0;
        List<String> removed = new ArrayList<>();
        List<String> added = new ArrayList<>();
        int a = 0;
        int b = 0;
        while (a < n || b < m) {
            if (a < n && b < m && before.get(a).equals(after.get(b))) {
                changed += gap(removed, added);
                a++;
                b++;
            } else if (b < m && (a == n || lcs[a][b + 1] >= lcs[a + 1][b])) {
                added.add(after.get(b++));
            } else {
                removed.add(before.get(a++));
            }
        }
        return changed + gap(removed, added);
    }

    /** A run of removed and added blocks between unchanged ones: pairs are edits, the rest whole blocks. */
    private static int gap(List<String> removed, List<String> added) {
        int changed = 0;
        for (int k = 0; k < Math.max(removed.size(), added.size()); k++) {
            String x = k < removed.size() ? removed.get(k) : "";
            String y = k < added.size() ? added.get(k) : "";
            changed += editedMiddle(x, y);
        }
        removed.clear();
        added.clear();
        return changed;
    }

    /** Length of the part of two strings that differs, after their common prefix and suffix. */
    public static int editedMiddle(String x, String y) {
        int prefix = 0;
        int max = Math.min(x.length(), y.length());
        while (prefix < max && x.charAt(prefix) == y.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < max - prefix && x.charAt(x.length() - 1 - suffix) == y.charAt(y.length() - 1 - suffix)) {
            suffix++;
        }
        return Math.max(x.length(), y.length()) - prefix - suffix;
    }

    /** The paragraph blocks of stored text (figures are not part of chunk text). */
    public static List<String> textBlocks(String text) {
        return blocks(text).stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** Packs blocks [from, to) into chunks: up to the target size, never over the maximum unless one block is. */
    private static void pack(List<String> blocks, int from, int to, List<Planned> out) {
        StringBuilder buffer = new StringBuilder();
        int start = from;
        for (int i = from; i < to; i++) {
            String block = blocks.get(i);
            boolean atomic = isAtomic(block);
            if (!buffer.isEmpty() && (buffer.length() + block.length() > MAX_CHARS
                    || (buffer.length() >= TARGET_CHARS && !atomic))) {
                out.add(new Planned(UUID.randomUUID(), false, buffer.toString(), start, i - 1));
                buffer.setLength(0);
                start = i;
            }
            if (!buffer.isEmpty()) {
                buffer.append("\n\n");
            }
            buffer.append(block);
        }
        if (!buffer.isEmpty()) {
            out.add(new Planned(UUID.randomUUID(), false, buffer.toString(), start, to - 1));
        }
    }

    /** Splits on blank lines, keeping fenced code and $$ display math whole. */
    static List<String> blocks(String markdown) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String fence = null;
        boolean math = false;
        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.strip();
            if (fence == null && !math && trimmed.isEmpty()) {
                if (!current.isEmpty()) {
                    blocks.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
            Matcher f = FENCE.matcher(line);
            if (!math && f.find()) {
                String marker = f.group(1);
                if (fence == null) {
                    fence = marker;
                } else if (marker.charAt(0) == fence.charAt(0) && marker.length() >= fence.length()) {
                    fence = null;
                }
            } else if (fence == null && countDisplayMath(trimmed) % 2 == 1) {
                math = !math;
            }
        }
        if (!current.isEmpty()) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    private static int countDisplayMath(String line) {
        int count = 0;
        for (int i = line.indexOf("$$"); i >= 0; i = line.indexOf("$$", i + 2)) {
            count++;
        }
        return count;
    }

    private static boolean isAtomic(String block) {
        return block.startsWith("|") || block.startsWith("$$") || FENCE.matcher(block).find();
    }

    private static int length(List<String> blocks) {
        return blocks.stream().mapToInt(String::length).sum();
    }
}
