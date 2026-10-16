"""Molecules with RDKit: a SMILES string in; a 3D structure, a 2D drawing, facts and highlighted atoms out.

No code is generated or run: the AI (or the learner) only supplies a SMILES string and SMARTS patterns, which RDKit
parses. Sizes are capped so a huge or pathological input cannot tie the renderer up.
"""

import contextlib
from dataclasses import dataclass

from sapio_renderer import groups

MAX_SMILES = 500
MAX_HEAVY_ATOMS = 120
MAX_PATTERN = 200
EMBED_TIMEOUT_SECONDS = 10
HIGHLIGHT = (0.98, 0.8, 0.08)  # the viewer's yellow


@dataclass
class Invalid(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass
class Built:
    canonical_smiles: str
    formula: str
    weight: float
    heavy_atoms: int
    mol_block: str  # with hydrogens and 3D coordinates (2D when embedding fails), for the 3D viewer
    three_d: bool
    png: bytes  # 2D drawing on white
    highlights: list[list[int]]  # per beat: atom indices in mol_block (heavy atoms, then their hydrogens)
    drawings: list[
        bytes
    ]  # per pattern: the 2D drawing with those atoms highlighted (for browsers without WebGL)


#: A focus lighting up more than this share of the heavy atoms says little ("[#6][#7]" in caffeine).
BROAD = 0.6


def _focus(mol, focus: str, caption: str) -> list[int]:
    """The heavy atoms a beat points at. A focus is a SMARTS pattern or a named group ("group:methyl"). When it
    lights up nothing, or most of the molecule, a group the caption names wins ("the three methyl groups")."""
    focus = (focus or "").strip()
    if not focus or len(focus) > MAX_PATTERN:
        return []
    heavy = mol.GetNumHeavyAtoms()

    def broad(atoms: list[int]) -> bool:
        return heavy > 6 and len(atoms) > BROAD * heavy

    named = groups.smarts(focus[6:]) if focus.lower().startswith("group:") else None
    atoms = _matches(mol, named) if named else _matches(mol, focus, loose=False)
    if atoms and not broad(atoms):
        return atoms
    for name in groups.named_in(caption):
        found = _matches(mol, groups.smarts(name), loose=False)
        if found and not broad(found):
            return found
    if not atoms and not named:
        atoms = _matches(mol, focus)
    return atoms


def _matches(mol, pattern: str, loose: bool = True) -> list[int]:
    """Atoms matching a SMARTS pattern. When it matches nothing, again by element and connectivity only: a pattern
    written as C=O or CN misses the aromatic carbons and nitrogens RDKit perceives in caffeine, say."""
    from rdkit import Chem

    for query in (Chem.MolFromSmarts(pattern), _loose(pattern) if loose else None):
        if query is None:
            continue
        atoms = {i for match in mol.GetSubstructMatches(query, maxMatches=20) for i in match}
        if atoms:
            return sorted(atoms)
    return []


def _loose(pattern: str):
    from rdkit import Chem
    from rdkit.Chem import rdqueries

    query = Chem.MolFromSmarts(pattern)
    if query is None or any(a.GetAtomicNum() == 0 for a in query.GetAtoms()):
        return None
    loose = Chem.RWMol()
    for atom in query.GetAtoms():
        loose.AddAtom(rdqueries.AtomNumEqualsQueryAtom(atom.GetAtomicNum()))
    for bond in query.GetBonds():
        loose.AddBond(bond.GetBeginAtomIdx(), bond.GetEndAtomIdx(), Chem.BondType.SINGLE)
        loose.ReplaceBond(loose.GetNumBonds() - 1, Chem.BondFromSmarts("~"))  # any bond
    return loose.GetMol()


def build(smiles: str, patterns: list[str], captions: list[str] | None = None) -> Built:
    """``patterns``: each beat's focus; ``captions``: each beat's narration (to recognise a group it names)."""
    from rdkit import Chem, RDLogger
    from rdkit.Chem import AllChem, Descriptors, rdMolDescriptors
    from rdkit.Chem.Draw import rdMolDraw2D

    RDLogger.DisableLog("rdApp.*")
    smiles = smiles.strip()
    if not smiles or len(smiles) > MAX_SMILES:
        raise Invalid("Give a SMILES string of at most 500 characters.")
    mol = Chem.MolFromSmiles(smiles)
    if mol is None:
        raise Invalid(f"'{smiles[:80]}' is not a valid SMILES string.")
    if mol.GetNumHeavyAtoms() > MAX_HEAVY_ATOMS:
        raise Invalid(f"The molecule is too large to show here (over {MAX_HEAVY_ATOMS} heavy atoms).")

    captions = captions or []
    heavy_highlights = [
        _focus(mol, p, captions[i] if i < len(captions) else "") for i, p in enumerate(patterns)
    ]

    with_h = Chem.AddHs(mol)
    params = AllChem.ETKDGv3()
    params.randomSeed = 0x5A9  # the same molecule always gets the same shape
    params.timeout = EMBED_TIMEOUT_SECONDS
    three_d = AllChem.EmbedMolecule(with_h, params) == 0
    if three_d:
        # Some atoms lack MMFF parameters; the embedded shape is still fine then.
        with contextlib.suppress(Exception):
            AllChem.MMFFOptimizeMolecule(with_h, maxIters=500)
    else:
        AllChem.Compute2DCoords(with_h)
    # In 3D a group includes its hydrogens (a methyl is its carbon and three H); they follow the heavy atoms.
    highlights = [
        sorted(
            set(atoms)
            | {
                n.GetIdx()
                for i in atoms
                for n in with_h.GetAtomWithIdx(i).GetNeighbors()
                if n.GetAtomicNum() == 1
            }
        )
        for atoms in heavy_highlights
    ]

    def draw(atoms: list[int]) -> bytes:
        drawer = rdMolDraw2D.MolDraw2DCairo(640, 420)
        drawer.drawOptions().addStereoAnnotation = True
        bonds = [
            b.GetIdx() for b in mol.GetBonds() if b.GetBeginAtomIdx() in atoms and b.GetEndAtomIdx() in atoms
        ]
        rdMolDraw2D.PrepareAndDrawMolecule(
            drawer,
            mol,
            highlightAtoms=atoms,
            highlightBonds=bonds,
            highlightAtomColors=dict.fromkeys(atoms, HIGHLIGHT),
            highlightBondColors=dict.fromkeys(bonds, HIGHLIGHT),
        )
        drawer.FinishDrawing()
        return drawer.GetDrawingText()

    return Built(
        canonical_smiles=Chem.MolToSmiles(mol),
        formula=rdMolDescriptors.CalcMolFormula(mol),
        weight=round(Descriptors.MolWt(mol), 2),
        heavy_atoms=mol.GetNumHeavyAtoms(),
        mol_block=Chem.MolToMolBlock(with_h),
        three_d=three_d,
        png=draw([]),
        highlights=highlights,
        drawings=[draw(atoms) for atoms in heavy_highlights],
    )
