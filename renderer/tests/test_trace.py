import pytest

from sapio_renderer import safety, trace

FACTORIAL = """def fact(n):
    if n <= 1:
        return 1
    return n * fact(n - 1)

total = fact(3)
print("3! =", total)
"""


def test_a_program_is_traced_line_by_line_with_its_call_stack():
    result = trace.trace(FACTORIAL, [])
    assert result["error"] is None and not result["truncated"]
    assert result["output"] == "3! = 6\n"
    deepest = max(result["steps"], key=lambda s: len(s["stack"]))
    assert [f["name"] for f in deepest["stack"]] == ["main", "fact", "fact", "fact"]
    assert [f["vars"]["n"] for f in deepest["stack"][1:]] == ["3", "2", "1"]
    returns = [
        s["value"] for s in result["steps"] if s["event"] == "return" and s["stack"][-1]["name"] == "fact"
    ]
    assert returns == ["1", "2", "6"]
    end = result["steps"][-1]
    assert end["event"] == "end" and end["output"] == "3! = 6\n"  # the final print shows
    assert end["stack"][0]["vars"]["total"] == "6"


def test_endless_loops_stop_at_the_step_limit():
    result = trace.trace("while True:\n    x = 1\n", [])
    assert result["truncated"] and len(result["steps"]) == 400  # no end step: it did not end


def test_a_swallowing_except_cannot_escape_the_limit():
    code = "while True:\n    try:\n        x = 1\n    except BaseException:\n        pass\n"
    assert trace.trace(code, [])["truncated"]


def test_the_programs_own_error_is_part_of_the_result():
    result = trace.trace("xs = [3, 1]\nprint(xs[5])\n", [])
    assert result["error"].startswith("IndexError") and "line 2" in result["error"]


def test_input_reads_the_given_lines():
    result = trace.trace('name = input("Name? ")\nprint("hi", name)\n', ["Asha"])
    assert result["output"] == "Name? Asha\nhi Asha\n"


@pytest.mark.parametrize(
    "code",
    [
        "import os",
        "open('/etc/passwd')",
        "__builtins__['op' + 'en']",
        "print((1).__class__)",
        "x = '{0.__class__}'.format(1)",
        "def __getattribute__(self, name): pass",
    ],
)
def test_programs_cannot_reach_files_processes_or_internals(code):
    with pytest.raises(safety.Rejected):
        trace.trace(code, [])


def test_teaching_python_is_accepted():
    code = (
        "from collections import deque\n"
        "class Stack:\n"
        "    def __init__(self):\n"
        "        self.items = deque()\n"
        "    def __len__(self):\n"
        "        return len(self.items)\n"
        "if __name__ == '__main__':\n"
        "    s = Stack()\n"
        "    print(len(s))\n"
    )
    assert trace.trace(code, [])["output"] == "0\n"
