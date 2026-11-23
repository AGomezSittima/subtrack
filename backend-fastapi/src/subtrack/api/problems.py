from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field
from enum import Enum, StrEnum
from typing import Literal

from fastapi.responses import JSONResponse

PROBLEM_MEDIA_TYPE = "application/problem+json"


class ProblemType(Enum):
    MALFORMED_REQUEST = ("malformed-request", 400, "Malformed request")
    UNAUTHORIZED = ("unauthorized", 401, "Unauthorized")
    INVALID_PASSCODE = ("invalid-passcode", 401, "Invalid passcode")
    NOT_FOUND = ("not-found", 404, "Not found")
    METHOD_NOT_ALLOWED = ("method-not-allowed", 405, "Method not allowed")
    PASSCODE_NOT_SET = ("passcode-not-set", 409, "Passcode not set")
    INVALID_STATUS_TRANSITION = ("invalid-status-transition", 409, "Invalid status transition")
    PAYMENT_NOT_EXPECTED = ("payment-not-expected", 409, "Payment not expected")
    PAYMENT_NOT_PAID = ("payment-not-paid", 409, "Payment not paid")
    PRICE_CHANGE_EXISTS = ("price-change-exists", 409, "Price change exists")
    BUILTIN_SERVICE_NOT_DELETABLE = (
        "builtin-service-not-deletable",
        409,
        "Built-in service not deletable",
    )
    UNSUPPORTED_MEDIA_TYPE = ("unsupported-media-type", 415, "Unsupported media type")
    VALIDATION_ERROR = ("validation-error", 422, "Validation error")
    UNSUPPORTED_FORMAT_VERSION = ("unsupported-format-version", 422, "Unsupported format version")
    TOO_MANY_ATTEMPTS = ("too-many-attempts", 429, "Too many attempts")
    INTERNAL_ERROR = ("internal-error", 500, "Internal error")

    def __init__(self, code: str, status: int, title: str) -> None:
        self.code = code
        self.status = status
        self.title = title

    @property
    def uri(self) -> str:
        return f"urn:subtrack:problem:{self.code}"


class IssueCode(StrEnum):
    REQUIRED = "required"
    UNKNOWN_PROPERTY = "unknown_property"
    TYPE = "type"
    FORMAT = "format"
    PATTERN = "pattern"
    ENUM = "enum"
    MINIMUM = "minimum"
    MAXIMUM = "maximum"
    MIN_LENGTH = "min_length"
    MAX_LENGTH = "max_length"
    INVALID = "invalid"


type IssueLocation = Literal["body", "query", "path"]
type IssueParams = Mapping[str, int | Sequence[str]]


@dataclass(frozen=True)
class ValidationIssue:
    location: IssueLocation
    pointer: str
    code: IssueCode
    params: IssueParams | None = None

    def to_json(self) -> dict[str, object]:
        issue: dict[str, object] = {
            "location": self.location,
            "pointer": self.pointer,
            "code": self.code.value,
        }
        if self.params:
            issue["params"] = dict(self.params)
        return issue


@dataclass
class ProblemError(Exception):
    type: ProblemType
    detail: str | None = None
    errors: Sequence[ValidationIssue] = ()
    headers: Mapping[str, str] = field(default_factory=dict[str, str])


def problem_response(
    problem: ProblemType,
    *,
    detail: str | None = None,
    errors: Sequence[ValidationIssue] = (),
    headers: Mapping[str, str] | None = None,
) -> JSONResponse:
    body: dict[str, object] = {
        "type": problem.uri,
        "title": problem.title,
        "status": problem.status,
    }
    if detail is not None:
        body["detail"] = detail
    if errors:
        body["errors"] = [issue.to_json() for issue in sorted(errors, key=_issue_order)]
    return JSONResponse(
        body, status_code=problem.status, headers=headers, media_type=PROBLEM_MEDIA_TYPE
    )


def _issue_order(issue: ValidationIssue) -> tuple[str, str, str]:
    return (issue.location, issue.pointer, issue.code.value)
