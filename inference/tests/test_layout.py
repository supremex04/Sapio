def test_inline_html_becomes_markdown_but_code_is_untouched():
    from ved_inference.parsing.layout import inline_html_to_markdown

    text = "<mark>Learn First</mark> and <u>this</u><br>`<b>keep</b>`\n```\n<i>code</i>\n```"
    assert inline_html_to_markdown(text) == "**Learn First** and this\n`<b>keep</b>`\n```\n<i>code</i>\n```"
    assert inline_html_to_markdown("- \U0001f183 Load Balancing") == "- Load Balancing"
