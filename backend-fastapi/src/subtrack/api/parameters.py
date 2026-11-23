import re

from subtrack.api.problems import (
    IssueCode,
    IssueLocation,
    ProblemError,
    ProblemType,
    ValidationIssue,
)

INT64_MAX = 2**63 - 1

_INTEGER = re.compile(r"(?P<sign>-?)(?P<digits>[0-9]+)")


def path_id(text: str) -> int:
    match = _INTEGER.fullmatch(text)
    if match is None:
        raise _invalid([ValidationIssue("path", "/id", IssueCode.TYPE)])
    digits = match["digits"].lstrip("0")
    if match["sign"] or not digits:
        raise _invalid([ValidationIssue("path", "/id", IssueCode.MINIMUM, {"minimum": 1})])
    if len(digits) > len(str(INT64_MAX)) or int(digits) > INT64_MAX:
        raise _invalid([ValidationIssue("path", "/id", IssueCode.MAXIMUM, {"maximum": INT64_MAX})])
    return int(digits)


class QueryParameters:
    def __init__(self) -> None:
        self._issues: list[ValidationIssue] = []

    def text(self, name: str, value: str | None, *, min_length: int, max_length: int) -> str | None:
        if value is not None and len(value) < min_length:
            self._issue(name, IssueCode.MIN_LENGTH, {"min_length": min_length})
        elif value is not None and len(value) > max_length:
            self._issue(name, IssueCode.MAX_LENGTH, {"max_length": max_length})
        return value

    def boolean(self, name: str, value: str | None, *, default: bool) -> bool:
        match value:
            case None:
                return default
            case "true":
                return True
            case "false":
                return False
            case _:
                self._issue(name, IssueCode.TYPE)
                return default

    def check(self) -> None:
        if self._issues:
            raise _invalid(self._issues)

    def _issue(self, name: str, code: IssueCode, params: dict[str, int] | None = None) -> None:
        location: IssueLocation = "query"
        self._issues.append(ValidationIssue(location, f"/{name}", code, params))


def _invalid(issues: list[ValidationIssue]) -> ProblemError:
    return ProblemError(ProblemType.VALIDATION_ERROR, errors=issues)
