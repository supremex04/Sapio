package com.sapio.visualization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks what each beat points at against what is actually shown, so the viewer never highlights something other
 * than what the narration says. The AI picks a protein residue or a line of code from memory and is sometimes off
 * (alpha residue 92 for the proximal histidine, which is beta 92 or alpha 87); a pointer that cannot be confirmed
 * is corrected when that is unambiguous, and dropped otherwise.
 */
final class SceneChecks {

    /** A checked protein focus: {@code focus} for the viewer ("B:92", "ligand:HEM" or ""), {@code label} to show. */
    record ProteinFocus(String focus, String label) {
    }

    static final Map<String, String> AMINO_ACIDS = Map.ofEntries(
            Map.entry("ALA", "alanine"), Map.entry("ARG", "arginine"), Map.entry("ASN", "asparagine"),
            Map.entry("ASP", "aspartate|aspartic acid"), Map.entry("CYS", "cysteine"),
            Map.entry("GLN", "glutamine"), Map.entry("GLU", "glutamate|glutamic acid"), Map.entry("GLY", "glycine"),
            Map.entry("HIS", "histidine"), Map.entry("ILE", "isoleucine"), Map.entry("LEU", "leucine"),
            Map.entry("LYS", "lysine"), Map.entry("MET", "methionine"), Map.entry("PHE", "phenylalanine"),
            Map.entry("PRO", "proline"), Map.entry("SER", "serine"), Map.entry("THR", "threonine"),
            Map.entry("TRP", "tryptophan"), Map.entry("TYR", "tyrosine"), Map.entry("VAL", "valine"));

    /** chain, residues (87, 87-92, 87,90), and an optional residue name before or after the number. */
    private static final Pattern RESIDUES = Pattern.compile(
            "^([A-Za-z0-9]{1,4})(?:\\s*:\\s*(?:([A-Za-z]{3})\\s*)?([\\d,\\s-]+?)(?:\\s*:?\\s*([A-Za-z]{3}))?)?$");
    private static final Pattern LIGAND = Pattern.compile("(?i)^ligand\\s*:\\s*([A-Za-z0-9]{1,3})$");
    private static final Pattern RANGE = Pattern.compile("^\\s*(\\d+)\\s*(?:-\\s*(\\d+))?\\s*$");

    private SceneChecks() {
    }

    static ProteinFocus protein(StructureIndex structure, String focus, String caption) {
        String f = focus == null ? "" : focus.strip();
        if (f.isEmpty()) {
            return new ProteinFocus("", null);
        }
        Matcher ligand = LIGAND.matcher(f);
        if (ligand.matches()) {
            String name = ligand.group(1).toUpperCase(Locale.ROOT);
            return structure.hasLigand(name) ? new ProteinFocus("ligand:" + name, "the " + name + " group")
                    : new ProteinFocus("", null);
        }
        Matcher m = RESIDUES.matcher(f);
        if (!m.matches()) {
            return new ProteinFocus("", null);
        }
        String chain = m.group(1).toUpperCase(Locale.ROOT);
        if (!structure.hasChain(chain)) {
            return new ProteinFocus("", null);
        }
        String residues = m.group(3) == null ? null : m.group(3).replaceAll("\\s+", "");
        if (residues == null || residues.isEmpty()) {
            return new ProteinFocus(chain, chainLabel(structure, chain));
        }
        Optional<Integer> single = residues.matches("\\d+") ? Optional.of(Integer.parseInt(residues))
                : Optional.empty();
        String named = Optional.ofNullable(m.group(2)).or(() -> Optional.ofNullable(m.group(4)))
                .map(s -> s.toUpperCase(Locale.ROOT)).filter(AMINO_ACIDS::containsKey)
                .orElse(single.isPresent() ? aminoAcidIn(caption) : null);

        if (single.isPresent()) {
            int number = single.get();
            Optional<String> actual = structure.residue(chain, number);
            if (actual.isPresent() && (named == null || named.equals(actual.get()))) {
                return residue(structure, chain, number, actual.get());
            }
            if (named != null) { // the right residue, another chain: the usual slip (alpha vs beta numbering)
                List<String> chains = structure.chainsWith(number, named);
                if (!chains.isEmpty()) {
                    return residue(structure, chains.getFirst(), number, named);
                }
            }
            return new ProteinFocus("", null);
        }
        for (String part : residues.split(",")) {
            Matcher r = RANGE.matcher(part);
            if (r.matches()) {
                int from = Integer.parseInt(r.group(1));
                int to = r.group(2) == null ? from : Integer.parseInt(r.group(2));
                if (structure.hasResidues(chain, Math.min(from, to), Math.max(from, to))) {
                    return new ProteinFocus(chain + ":" + residues,
                            "chain " + chain + ", residues " + residues.replace(",", ", "));
                }
            }
        }
        return new ProteinFocus("", null);
    }

    private static ProteinFocus residue(StructureIndex structure, String chain, int number, String name) {
        String pretty = name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
        String label = pretty + number + ", chain " + chain;
        String description = structure.description(chain);
        return new ProteinFocus(chain + ":" + number, description.isBlank() ? label : label + " (" + description
                .toLowerCase(Locale.ROOT) + ")");
    }

    private static String chainLabel(StructureIndex structure, String chain) {
        String description = structure.description(chain);
        return description.isBlank() ? "chain " + chain : "chain " + chain + " (" + description.toLowerCase(Locale.ROOT)
                + ")";
    }

    /** The three-letter code of the amino acid a caption names first, if it names one. */
    static String aminoAcidIn(String caption) {
        if (caption == null) {
            return null;
        }
        String text = caption.toLowerCase(Locale.ROOT);
        String first = null;
        int at = Integer.MAX_VALUE;
        for (Map.Entry<String, String> e : AMINO_ACIDS.entrySet()) {
            Matcher m = Pattern.compile("\\b(" + e.getValue() + ")s?\\b").matcher(text);
            if (m.find() && m.start() < at) {
                at = m.start();
                first = e.getKey();
            }
        }
        return first;
    }

    /**
     * The lines a beat explains, as "a-b" in the program actually traced, or "" when they cannot be found. A focus
     * is the text of a line (or the first and last of a block, joined with " | "), found in the program, or a line
     * range kept only when it falls on code.
     */
    static String lines(String program, String focus) {
        String f = focus == null ? "" : focus.strip();
        List<String> code = program.lines().toList();
        if (f.isEmpty()) {
            return "";
        }
        Matcher range = RANGE.matcher(f);
        int from;
        int to;
        if (range.matches()) {
            from = Integer.parseInt(range.group(1));
            to = range.group(2) == null ? from : Integer.parseInt(range.group(2));
        } else {
            List<Integer> found = new ArrayList<>();
            for (String part : f.split("\\s*\\|\\s*")) {
                String wanted = squash(part);
                if (wanted.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < code.size(); i++) {
                    if (squash(code.get(i)).contains(wanted)) {
                        found.add(i + 1);
                        break;
                    }
                }
            }
            if (found.isEmpty()) {
                return "";
            }
            from = found.stream().min(Integer::compare).orElseThrow();
            to = found.stream().max(Integer::compare).orElseThrow();
        }
        from = Math.max(1, Math.min(from, to));
        to = Math.min(code.size(), Math.max(from, to));
        while (from <= to && code.get(from - 1).isBlank()) {
            from++;
        }
        while (to >= from && code.get(to - 1).isBlank()) {
            to--;
        }
        return from <= to ? from + "-" + to : "";
    }

    private static String squash(String s) {
        return s.replace("`", "").replaceAll("\\s+", "");
    }
}
