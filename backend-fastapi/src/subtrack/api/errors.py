import logging
import re
from collections.abc import Callable, Sequence

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.routing import iter_route_contexts
from pydantic_core import ErrorDetails
from starlette.exceptions import HTTPException
from starlette.routing import Match

from subtrack.api.problems import (
    IssueCode,
    IssueLocation,
    IssueParams,
    ProblemError,
    ProblemType,
    ValidationIssue,
    problem_response,
)
from subtrack.api.types import FORMAT_ERROR
from subtrack.service.errors import BuiltinNotDeletableError, InvalidFieldsError, NotFoundError

logger = logging.getLogger(__name__)

_JSON_MEDIA_TYPE = re.compile(r"application/([^;\s]+\+)?json(\s*;.*)?", re.IGNORECASE)

_HTTP_PROBLEMS = {
    400: ProblemType.MALFORMED_REQUEST,
    404: ProblemType.NOT_FOUND,
    405: ProblemType.METHOD_NOT_ALLOWED,
    415: ProblemType.UNSUPPORTED_MEDIA_TYPE,
}

_ISSUE_CODES = {
    "missing": IssueCode.REQUIRED,
    "extra_forbidden": IssueCode.UNKNOWN_PROPERTY,
    FORMAT_ERROR: IssueCode.FORMAT,
    "string_pattern_mismatch": IssueCode.PATTERN,
    "enum": IssueCode.ENUM,
    "literal_error": IssueCode.ENUM,
    "greater_than_equal": IssueCode.MINIMUM,
    "less_than_equal": IssueCode.MAXIMUM,
    "string_too_short": IssueCode.MIN_LENGTH,
    "too_short": IssueCode.MIN_LENGTH,
    "string_too_long": IssueCode.MAX_LENGTH,
    "too_long": IssueCode.MAX_LENGTH,
}

_PARAMS = {
    IssueCode.MINIMUM: ("ge", "minimum"),
    IssueCode.MAXIMUM: ("le", "maximum"),
    IssueCode.MIN_LENGTH: ("min_length", "min_length"),
    IssueCode.MAX_LENGTH: ("max_length", "max_length"),
}


def install_error_handlers(app: FastAPI) -> None:
    _register(app, ProblemError, _problem)
    _register(app, NotFoundError, _not_found)
    _register(app, BuiltinNotDeletableError, _builtin_not_deletable)
    _register(app, InvalidFieldsError, _invalid_fields)
    _register(app, HTTPException, _http)
    _register(app, RequestValidationError, _validation)
    _register(app, Exception, _unexpected)


def _register[E: Exception](
    app: FastAPI, kind: type[E], handle: Callable[[Request, E], JSONResponse]
) -> None:
    def handler(request: Request, exc: Exception) -> JSONResponse:
        if not isinstance(exc, kind):
            raise exc
        return handle(request, exc)

    app.add_exception_handler(kind, handler)


def _problem(_: Request, exc: ProblemError) -> JSONResponse:
    return problem_response(exc.type, detail=exc.detail, errors=exc.errors, headers=exc.headers)


def _not_found(_: Request, __: NotFoundError) -> JSONResponse:
    return problem_response(ProblemType.NOT_FOUND)


def _builtin_not_deletable(_: Request, __: BuiltinNotDeletableError) -> JSONResponse:
    return problem_response(ProblemType.BUILTIN_SERVICE_NOT_DELETABLE)


def _invalid_fields(_: Request, exc: InvalidFieldsError) -> JSONResponse:
    issues = [ValidationIssue("body", pointer, IssueCode.INVALID) for pointer in exc.pointers]
    return problem_response(ProblemType.VALIDATION_ERROR, errors=issues)


def _http(request: Request, exc: HTTPException) -> JSONResponse:
    problem = _HTTP_PROBLEMS.get(exc.status_code, ProblemType.INTERNAL_ERROR)
    allow = (exc.headers or {}).get("Allow")
    return problem_response(problem, headers={"Allow": _allow(request, allow)} if allow else None)


def _allow(request: Request, header: str) -> str:
    # Starlette's Allow header lists only the first route that matches the path.
    methods = {method.strip() for method in header.split(",")}
    for route in iter_route_contexts(request.app.router.routes):
        match, _ = route.matches(request.scope)
        if match is not Match.NONE:
            methods.update(route.methods or ())
    if "GET" in methods:
        methods.add("HEAD")
    return ", ".join(sorted(methods))


def _validation(request: Request, exc: RequestValidationError) -> JSONResponse:
    errors: Sequence[ErrorDetails] = exc.errors()
    if any(error["type"] == "json_invalid" for error in errors):
        return problem_response(ProblemType.MALFORMED_REQUEST)
    if any(tuple(error["loc"]) == ("body",) for error in errors) and _body_not_json(request):
        return problem_response(ProblemType.UNSUPPORTED_MEDIA_TYPE)
    return problem_response(
        ProblemType.VALIDATION_ERROR, errors=[_issue(error) for error in errors]
    )


def _unexpected(request: Request, exc: Exception) -> JSONResponse:
    logger.error("Unexpected error on %s %s", request.method, request.url.path, exc_info=exc)
    return problem_response(ProblemType.INTERNAL_ERROR)


def _body_not_json(request: Request) -> bool:
    content_type = request.headers.get("content-type")
    if content_type is None:
        return _has_body(request)
    return _JSON_MEDIA_TYPE.fullmatch(content_type) is None


def _has_body(request: Request) -> bool:
    length = request.headers.get("content-length")
    return "transfer-encoding" in request.headers or (length is not None and length != "0")


def _issue(error: ErrorDetails) -> ValidationIssue:
    location, *path = error["loc"]
    code = _ISSUE_CODES.get(error["type"], _fallback_code(error["type"]))
    return ValidationIssue(
        location=_location(location),
        pointer="".join(f"/{_escape(part)}" for part in path),
        code=code,
        params=_params(code, error),
    )


def _fallback_code(error_type: str) -> IssueCode:
    if error_type.endswith(("_type", "_parsing")) or error_type == "int_from_float":
        return IssueCode.TYPE
    return IssueCode.INVALID


def _location(value: int | str) -> IssueLocation:
    match value:
        case "body" | "query" | "path":
            return value
        case _:
            msg = f"unexpected validation location {value!r}"
            raise ValueError(msg)


def _escape(part: int | str) -> str:
    return str(part).replace("~", "~0").replace("/", "~1")


def _params(code: IssueCode, error: ErrorDetails) -> IssueParams | None:
    names = _PARAMS.get(code)
    context = error.get("ctx")
    if names is None or context is None:
        return None
    source, target = names
    value = context.get(source)
    return {target: value} if isinstance(value, int) else None
