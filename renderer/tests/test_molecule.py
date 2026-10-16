import pytest

pytest.importorskip("rdkit")  # installed in the Docker image

from sapio_renderer import molecule


def test_a_molecule_gets_3d_coordinates_facts_and_highlighted_atoms():
    built = molecule.build("CC(=O)Oc1ccccc1C(=O)O", ["C(=O)[OX2H1]", "c1ccccc1", ""])
    assert built.formula == "C9H8O4" and built.three_d
    assert built.png.startswith(b"\x89PNG")
    acid, ring, nothing = built.highlights
    assert len(acid) == 3 + 1 and len(ring) == 6 + 4 and nothing == []  # with their hydrogens
    assert "V2000" in built.mol_block
    assert len(built.drawings) == 3 and all(d.startswith(b"\x89PNG") for d in built.drawings)
    assert built.drawings[0] != built.drawings[2]  # the acid group is drawn highlighted


def test_invalid_or_huge_input_is_refused():
    with pytest.raises(molecule.Invalid):
        molecule.build("not-a-smiles", [])
    with pytest.raises(molecule.Invalid):
        molecule.build("C" * 200, [])


def test_a_pattern_written_without_aromaticity_still_finds_its_atoms():
    caffeine = "Cn1c(=O)c2c(ncn2C)n(C)c1=O"
    carbonyls = molecule.build(caffeine, ["C=O"]).highlights[0]
    assert len(carbonyls) == 4  # two C=O groups, though RDKit sees those carbons as aromatic


CAFFEINE = "Cn1c(=O)c2c(ncn2C)n(C)c1=O"


def _heavy(built, beat):
    return [i for i in built.highlights[beat] if i < built.heavy_atoms]


def test_a_named_group_lights_up_only_its_own_atoms_and_hydrogens():
    built = molecule.build(CAFFEINE, ["group:methyl", "group:n-methyl", "group:carbonyl"])
    methyls = built.highlights[0]
    assert len(_heavy(built, 0)) == 3 and len(methyls) == 3 * 4  # three CH3, each with its three H
    assert _heavy(built, 1) == _heavy(built, 0)  # all three sit on nitrogens; the nitrogens stay out
    assert len(_heavy(built, 2)) == 4


def test_a_caption_naming_a_group_rescues_a_focus_that_lights_up_everything():
    built = molecule.build(
        CAFFEINE,
        ["[#6][#7]", "[#6][#7]"],
        ["Three methyl groups occupy key positions.", "Look at the rings."],
    )
    assert len(_heavy(built, 0)) == 3  # the methyl carbons, not every C-N pair
    assert len(_heavy(built, 1)) > 0.6 * built.heavy_atoms  # no group named: the broad focus stays
