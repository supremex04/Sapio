package com.sapio.visualization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What a protein structure (mmCIF) contains, read once from the file the viewer shows: each chain's residues by
 * number and name, the ligands, and what each chain is ("Hemoglobin subunit alpha"). Used to check what the
 * narration points at against the structure itself, so the viewer never highlights something other than what is
 * said.
 */
final class StructureIndex {

    /** A chain of the structure; {@code description}: the entity it belongs to, as the entry names it. */
    record Chain(String id, String description, boolean polymer) {
    }

    private final Map<String, TreeMap<Integer, String>> residues = new LinkedHashMap<>();
    private final Set<String> ligands = new TreeSet<>();
    private final Map<String, String> chainEntity = new LinkedHashMap<>();
    private final Map<String, String[]> entities = new HashMap<>(); // id -> {type, description}

    private StructureIndex() {
    }

    static StructureIndex parse(String cif) {
        StructureIndex index = new StructureIndex();
        for (Table table : tables(cif)) {
            if (table.category.equals("_atom_site")) {
                index.readAtoms(table);
            } else if (table.category.equals("_entity")) {
                for (Map<String, String> row : table.rows) {
                    index.entities.put(row.getOrDefault("id", ""), new String[] {row.getOrDefault("type", ""),
                            row.getOrDefault("pdbx_description", "")});
                }
            }
        }
        return index;
    }

    private void readAtoms(Table table) {
        for (Map<String, String> row : table.rows) {
            String chain = first(row, "auth_asym_id", "label_asym_id");
            String name = first(row, "auth_comp_id", "label_comp_id");
            String number = first(row, "auth_seq_id", "label_seq_id");
            if (chain == null || name == null || number == null || !number.matches("-?\\d+")) {
                continue;
            }
            residues.computeIfAbsent(chain, c -> new TreeMap<>()).putIfAbsent(Integer.parseInt(number), name);
            if ("HETATM".equals(row.get("group_PDB")) && !name.equals("HOH")) {
                ligands.add(name);
            }
            chainEntity.putIfAbsent(chain, row.getOrDefault("label_entity_id", ""));
        }
    }

    Optional<String> residue(String chain, int number) {
        TreeMap<Integer, String> chainResidues = residues.get(chain);
        return Optional.ofNullable(chainResidues == null ? null : chainResidues.get(number));
    }

    boolean hasChain(String chain) {
        return residues.containsKey(chain);
    }

    /** Whether any residue of the chain lies in [from, to]. */
    boolean hasResidues(String chain, int from, int to) {
        TreeMap<Integer, String> chainResidues = residues.get(chain);
        return chainResidues != null && !chainResidues.subMap(from, true, to, true).isEmpty();
    }

    boolean hasLigand(String name) {
        return ligands.contains(name);
    }

    /** Chains whose residue at {@code number} is {@code name}, in file order. */
    List<String> chainsWith(int number, String name) {
        List<String> out = new ArrayList<>();
        residues.forEach((chain, byNumber) -> {
            if (name.equals(byNumber.get(number))) {
                out.add(chain);
            }
        });
        return out;
    }

    List<Chain> chains() {
        List<Chain> out = new ArrayList<>();
        chainEntity.forEach((chain, entity) -> {
            String[] e = entities.getOrDefault(entity, new String[] {"", ""});
            if ("polymer".equals(e[0])) {
                out.add(new Chain(chain, e[1], true));
            }
        });
        return out;
    }

    String description(String chain) {
        String[] e = entities.get(chainEntity.getOrDefault(chain, ""));
        return e == null ? "" : e[1];
    }

    private static String first(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (value != null && !value.equals("?") && !value.equals(".")) {
                return value;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ mmCIF reading

    private record Table(String category, List<Map<String, String>> rows) {
    }

    /** The tables of the categories we read (loops, and single-row key-value blocks), tokenised as mmCIF says. */
    private static List<Table> tables(String cif) {
        List<Table> out = new ArrayList<>();
        String[] lines = cif.split("\r?\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].strip();
            if (line.equals("loop_")) {
                List<String> fields = new ArrayList<>();
                i++;
                while (i < lines.length && lines[i].startsWith("_")) {
                    fields.add(lines[i].strip());
                    i++;
                }
                List<String> tokens = new ArrayList<>();
                while (i < lines.length && !lines[i].startsWith("loop_") && !lines[i].startsWith("_")
                        && !lines[i].startsWith("#") && !lines[i].startsWith("data_")) {
                    i = tokenise(lines, i, tokens);
                }
                String category = fields.isEmpty() ? "" : fields.getFirst().substring(0, fields.getFirst().indexOf('.'));
                if (wanted(category)) {
                    List<Map<String, String>> rows = new ArrayList<>();
                    for (int t = 0; t + fields.size() <= tokens.size(); t += fields.size()) {
                        Map<String, String> row = new HashMap<>();
                        for (int f = 0; f < fields.size(); f++) {
                            row.put(fields.get(f).substring(fields.get(f).indexOf('.') + 1), tokens.get(t + f));
                        }
                        rows.add(row);
                    }
                    out.add(new Table(category, rows));
                }
            } else if (line.startsWith("_") && line.contains(".")) { // key-value: one row of a category
                String category = line.substring(0, line.indexOf('.'));
                Map<String, String> row = new HashMap<>();
                while (i < lines.length && lines[i].startsWith(category + ".")) {
                    List<String> tokens = new ArrayList<>();
                    String key = lines[i].strip().split("\\s+", 2)[0];
                    i = tokenise(lines, i, tokens);
                    if (tokens.size() < 2 && i < lines.length && lines[i].startsWith(";")) {
                        i = tokenise(lines, i, tokens); // the value is a text block on the next lines
                    }
                    row.put(key.substring(key.indexOf('.') + 1), tokens.size() > 1 ? tokens.get(1) : "");
                }
                if (wanted(category)) {
                    out.add(new Table(category, List.of(row)));
                }
            } else {
                i++;
            }
        }
        return out;
    }

    private static boolean wanted(String category) {
        return category.equals("_atom_site") || category.equals("_entity");
    }

    /** Tokens of line i (or of the ;-text block starting there) into {@code tokens}; returns the next line. */
    private static int tokenise(String[] lines, int i, List<String> tokens) {
        String line = lines[i];
        if (line.startsWith(";")) {
            StringBuilder text = new StringBuilder(line.substring(1));
            i++;
            while (i < lines.length && !lines[i].startsWith(";")) {
                text.append(' ').append(lines[i].strip());
                i++;
            }
            tokens.add(text.toString().strip());
            return i + 1;
        }
        int p = 0;
        int n = line.length();
        while (p < n) {
            char c = line.charAt(p);
            if (Character.isWhitespace(c)) {
                p++;
            } else if (c == '\'' || c == '"') {
                int end = p + 1;
                while (end < n && !(line.charAt(end) == c && (end + 1 == n
                        || Character.isWhitespace(line.charAt(end + 1))))) {
                    end++;
                }
                tokens.add(line.substring(p + 1, Math.min(end, n)));
                p = end + 1;
            } else {
                int end = p;
                while (end < n && !Character.isWhitespace(line.charAt(end))) {
                    end++;
                }
                tokens.add(line.substring(p, end));
                p = end;
            }
        }
        return i + 1;
    }
}
