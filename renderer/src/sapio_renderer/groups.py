"""Common chemical groups, as SMARTS that match only the group's own atoms (recursive SMARTS keep the neighbours
that define a group out of the highlight: "N-methyl" lights up the CH3, not the nitrogen).

Used two ways: a beat's focus may name one ("group:methyl"), and a caption that names one ("the three methyl
groups") rescues a focus whose SMARTS lit up most of the molecule or nothing at all.
"""

import re

#: name -> (words that name it in a caption, SMARTS). Order matters: the more specific first ("carboxylic acid"
#: before "hydroxyl", "ester" before "ether", "amide" before "amine", "aldehyde" and "ketone" before "carbonyl").
GROUPS: dict[str, tuple[str, str]] = {
    "carboxylic acid": (r"carboxylic acids?|carboxyl|acid groups?", "[CX3](=[OX1])[OX2H1]"),
    "carboxylate": (r"carboxylates?", "[CX3](=[OX1])[OX1-]"),
    "ester": (r"esters?", "[CX3;$([CX3](=O)[OX2][#6])](=[OX1])[OX2H0;$(O[#6])]"),
    "amide": (r"amides?|peptide bonds?", "[NX3][CX3](=[OX1])"),
    "aldehyde": (r"aldehydes?", "[CX3H1](=[OX1])"),
    "ketone": (r"ketones?", "[CX3;$([CX3]([#6])[#6])]=[OX1]"),
    "carbonyl": (r"carbonyls?|c\s*=\s*o|double[- ]bonded oxygens?|keto groups?", "[#6]=[OX1]"),
    "phenol": (r"phenols?", "c[OX2H1]"),
    "hydroxyl": (r"hydroxyls?|alcohols?|oh groups?", "[OX2H1;$(O[#6])]"),
    "ether": (r"ethers?", "[OD2;$(O([#6])[#6]);!$(O[#6]=O)]"),
    "n-methyl": (r"n-methyls?", "[CH3;$([CH3][#7])]"),
    "methyl": (r"methyls?", "[CH3]"),
    "amine": (r"amines?|amino groups?", "[NX3;!$(N[#6]=[O,S,N]);!$(N=*);!$(N#*);!$([N+])]"),
    "nitro": (r"nitro groups?", "[$([NX3](=O)=O),$([NX3+](=O)[O-])]~[OX1]"),
    "nitrile": (r"nitriles?|cyano groups?", "[CX2]#[NX1]"),
    "thiol": (r"thiols?|sulfhydryls?", "[SX2H1]"),
    "phosphate": (r"phosphates?", "[PX4](=[OX1])([OX2,OX1-])([OX2,OX1-])"),
    "halogen": (r"halogens?|fluorines?|chlorines?|bromines?|iodines?", "[F,Cl,Br,I]"),
    "purine": (r"purines?|purine rings?", "[#6]1~[#7]~[#6]~[#6]2~[#7]~[#6]~[#7]~[#6]~2~[#7]~1"),
    "imidazole": (r"imidazoles?|five-membered rings?", "[#6]1~[#7]~[#6]~[#7]~[#6]~1"),
    "pyrimidine": (r"pyrimidines?|six-membered rings?", "[#6]1~[#7]~[#6]~[#7]~[#6]~[#6]~1"),
    "benzene ring": (r"benzene rings?|phenyl groups?|phenyl rings?|aromatic rings?", "a1aaaaa1"),
    "alkene": (r"alkenes?|c\s*=\s*c|carbon-carbon double bonds?", "[CX3]=[CX3]"),
    "alkyne": (r"alkynes?|triple bonds?", "[CX2]#[CX2]"),
}

_CAPTION = [
    (name, re.compile(rf"(?<![a-z]){words}(?![a-z])", re.IGNORECASE)) for name, (words, _) in GROUPS.items()
]


def smarts(name: str) -> str | None:
    """The SMARTS of a group named in a focus ("group:methyl"), or None."""
    group = GROUPS.get(name.strip().lower())
    return group[1] if group else None


def named_in(caption: str) -> list[str]:
    """Groups a caption names, in the order it names them."""
    found = []
    for name, pattern in _CAPTION:
        m = pattern.search(caption or "")
        if m:
            found.append((m.start(), m.end(), name))
    # A name inside a longer one ("methyl" in "N-methyl", "ether" nowhere in "ester") is the longer one's.
    kept = [
        f
        for f in found
        if not any(o is not f and o[0] <= f[0] and f[1] <= o[1] and o[1] - o[0] > f[1] - f[0] for o in found)
    ]
    return [name for _, _, name in sorted(kept)]
