from starlette.concurrency import run_in_threadpool
from starlette.requests import Request
from starlette.types import ASGIApp, Receive, Scope, Send

from subtrack.api.problems import ProblemType, problem_response
from subtrack.security.lock import LockEnforcer
from subtrack.security.tokens import BACKGROUND_HEADER, SESSION_COOKIE

PUBLIC_PATHS = frozenset({"/health", "/auth/status", "/auth/unlock"})
TEST_MODE_PATHS = frozenset({"/admin/clock", "/admin/reset"})


class LockMiddleware:
    def __init__(
        self, app: ASGIApp, *, enforcer: LockEnforcer, prefix: str, test_mode: bool
    ) -> None:
        self._app = app
        self._enforcer = enforcer
        self._prefix = prefix
        self._test_mode = test_mode
        self._public = PUBLIC_PATHS | TEST_MODE_PATHS if test_mode else PUBLIC_PATHS

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        path = self._api_path(scope)
        if path is None or path in self._public:
            await self._app(scope, receive, send)
            return
        if path in TEST_MODE_PATHS:
            await problem_response(ProblemType.NOT_FOUND)(scope, receive, send)
            return
        request = Request(scope)
        allowed = await run_in_threadpool(
            self._enforcer.allows,
            request.cookies.get(SESSION_COOKIE),
            background=request.headers.get(BACKGROUND_HEADER) == "1",
        )
        if allowed:
            await self._app(scope, receive, send)
            return
        await problem_response(ProblemType.UNAUTHORIZED)(scope, receive, send)

    def _api_path(self, scope: Scope) -> str | None:
        if scope["type"] != "http":
            return None
        path: str = scope["path"]
        if path != self._prefix and not path.startswith(f"{self._prefix}/"):
            return None
        return path.removeprefix(self._prefix)
