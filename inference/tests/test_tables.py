from sapio_inference.parsing.layout import inline_html_to_markdown
from sapio_inference.parsing.tables import repair_tables, split_row


def test_cell_line_breaks_stay_inside_the_row():
    md = "|Strategy|How the boundary is<br>chosen|\n|---|---|\n|By business<br>capability|What the business<br>_does_|"
    assert inline_html_to_markdown(md) == (
        "|Strategy|How the boundary is chosen|\n|---|---|\n|By business capability|What the business _does_|"
    )


def test_wrapped_cell_tails_merge_into_their_row():
    md = (
        "|Node|What it does|Good when|Warning sign|\n"
        "|---|---|---|---|\n"
        "|HashAggregate /|GROUP BY via|—|Disk spill; huge|\n"
        "|GroupAggregate|hash or sorted<br>input||group counts|\n"
        "|Memoize (PG14+)|Caches inner results|Repeated inner lookups|Low hit rate|"
    )
    assert repair_tables(md).split("\n")[2:] == [
        "|HashAggregate / GroupAggregate|GROUP BY via hash or sorted input|—|Disk spill; huge group counts|",
        "|Memoize (PG14+)|Caches inner results|Repeated inner lookups|Low hit rate|",
    ]


def test_tables_with_real_empty_cells_and_code_blocks_are_untouched():
    sparse = "|A|B|\n|---|---|\n|x||\n|y||"
    fenced = "```\n|a|b|\n|---|---|\n|c||\n```"
    assert repair_tables(sparse) == sparse
    assert repair_tables(fenced) == fenced


def test_a_table_continued_after_a_page_break_is_joined():
    md = "|A|B|\n|---|---|\n|1|2|\n\n---\n\n|A|B|\n|---|---|\n|3|4|\n\nAfter."
    assert repair_tables(md) == "|A|B|\n|---|---|\n|1|2|\n|3|4|\n\nAfter."
    different = "|A|B|\n|---|---|\n|1|2|\n\n|C|D|\n|---|---|\n|3|4|"
    assert repair_tables(different) == different


def test_pipes_inside_inline_code_do_not_split_cells():
    assert split_row("|`a|b`|c|") == ["`a|b`", "c"]
