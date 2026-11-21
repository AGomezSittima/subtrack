import json
from datetime import date
from pathlib import Path
from typing import Any

import pytest

from subtrack.billing.interval import Interval, IntervalUnit

CASES_DIR = Path(__file__).resolve().parents[3] / "contract" / "cases"

type Json = dict[str, Any]


def load_cases(file_name: str) -> list[Any]:
    document = json.loads((CASES_DIR / file_name).read_text(encoding="utf-8"))
    return [
        pytest.param(case, id=f"{case['id']} {' '.join(case['rules'])}")
        for case in document["cases"]
    ]


def failure_message(case: Json) -> str:
    return f"case {case['id']} ({', '.join(case['rules'])})"


def parse_date(value: str) -> date:
    return date.fromisoformat(value)


def parse_optional_date(value: str | None) -> date | None:
    return None if value is None else date.fromisoformat(value)


def parse_interval(value: Json) -> Interval:
    return Interval(count=value["count"], unit=IntervalUnit(value["unit"]))


def without_none(values: Json) -> Json:
    return {key: value for key, value in values.items() if value is not None}
