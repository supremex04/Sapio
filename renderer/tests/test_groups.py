from sapio_renderer import groups


def test_captions_name_groups_in_order_and_prefer_the_specific():
    assert groups.named_in("The N-methyl groups and a carbonyl") == ["n-methyl", "carbonyl"]
    assert groups.named_in("an ester, not an ether") == ["ester", "ether"]
    assert groups.named_in("This is aspirin.") == []
