package com.sapio.visualization;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the engine from the request itself when that is obvious, without the AI: pasted code, a SMILES string, a
 * Protein Data Bank ID. Everything else is left to the storyboard call, which chooses as part of planning (no extra
 * call). A SMILES candidate is only a candidate: RDKit confirms it before it is used.
 */
final class EngineRouter {

    /** What the rules found, with the learner's own data (their program, SMILES or PDB ID) when they gave it. */
    record Routed(VisualizationEngine engine, String data) {
    }

    private static final Pattern CODE_LINE = Pattern.compile(
            "^\\s*(def |class |for |while |if |elif |else\\s*:|return\\b|import |from \\S+ import |print\\(|try\\s*:"
                    + "|except\\b|with |#include|public |private |function |const |let |var |int |#)"
                    + "|[:;{}]\\s*$|^\\s*}");
    private static final Pattern SMILES = Pattern.compile("^[A-Za-z0-9@+\\-\\[\\]()=#$/\\\\%.:*]{2,300}$");
    private static final Pattern SMILES_SIGNAL = Pattern.compile("[()=#\\[\\]@]|[a-z][0-9]|[A-Z][0-9]");
    private static final Pattern PDB_ID = Pattern.compile("(?i)(?<![A-Za-z0-9])([1-9][A-Za-z0-9]{3})(?![A-Za-z0-9])");
    private static final Pattern BIOLOGY = Pattern.compile("(?i)\\b(pdb|protein|enzyme|structure|dna|rna|antibody"
            + "|receptor|haemoglobin|hemoglobin|complex|virus|capsid|channel|kinase|ribosome)\\b");

    private EngineRouter() {
    }

    static Optional<Routed> route(String request) {
        String text = request.strip();
        if (looksLikeCode(text)) {
            return Optional.of(new Routed(VisualizationEngine.CODE, stripFence(text)));
        }
        Optional<String> pdb = pdbId(text);
        if (pdb.isPresent()) {
            return Optional.of(new Routed(VisualizationEngine.PROTEIN, pdb.get()));
        }
        if (SMILES.matcher(text).matches() && SMILES_SIGNAL.matcher(text).find()) {
            return Optional.of(new Routed(VisualizationEngine.MOLECULE, text));
        }
        return Optional.empty();
    }

    static boolean looksLikeCode(String text) {
        if (text.contains("```")) {
            return true;
        }
        List<String> lines = text.lines().filter(l -> !l.isBlank()).toList();
        long code = lines.stream().filter(l -> CODE_LINE.matcher(l).find()).count();
        return lines.size() >= 2 && code >= 2 && code * 2 >= lines.size();
    }

    /** A 4-character ID with a letter in it, on its own or next to a word like "protein" or "PDB". */
    static Optional<String> pdbId(String text) {
        Matcher m = PDB_ID.matcher(text);
        while (m.find()) {
            String id = m.group(1);
            boolean hasLetter = id.chars().anyMatch(Character::isLetter);
            boolean alone = text.split("\\s+").length <= 3;
            if (hasLetter && (alone || BIOLOGY.matcher(text).find())) {
                return Optional.of(id.toUpperCase(Locale.ROOT));
            }
        }
        return Optional.empty();
    }

    static String stripFence(String text) {
        return text.replaceAll("(?s)^```[a-zA-Z0-9]*\\s*\\n", "").replaceAll("(?s)\\n```\\s*$", "").strip() + "\n";
    }
}
