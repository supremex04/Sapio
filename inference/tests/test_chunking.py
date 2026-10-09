from ved_inference.study.chunking import MAX_CHARS, PageText, chunk_pages


def test_headings_start_new_chunks_and_pages_are_tracked():
    unit = "Sentence about the topic. " * 80  # ~2000 chars
    pages = [
        PageText(1, 1, f"# Unit 1\n\n{unit}"),
        PageText(2, 2, f"# Unit 2\n\n{unit}"),
    ]
    chunks = chunk_pages(pages)
    assert [c.heading for c in chunks] == ["Unit 1", "Unit 2"]
    assert [(c.page_from, c.page_to) for c in chunks] == [(1, 1), (2, 2)]
    assert [c.position for c in chunks] == [1, 2]


def test_long_paragraphs_are_split_without_exceeding_the_max():
    text = "This is a long sentence that keeps going. " * 600  # ~25k chars, one paragraph
    chunks = chunk_pages([PageText(1, 3, text)])
    assert len(chunks) > 3
    assert all(len(c.content) <= MAX_CHARS for c in chunks)


def test_small_documents_become_one_chunk():
    chunks = chunk_pages([PageText(1, 1, "Short note.\n\nAnother paragraph.")])
    assert len(chunks) == 1
    assert chunks[0].content == "Short note.\n\nAnother paragraph."
