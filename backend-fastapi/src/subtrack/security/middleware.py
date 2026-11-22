"""Lock enforcement for every request under the API prefix, before routing and body parsing."""

from starlette.concurrency import run_in_threadpool
from starlette.requests import Request
from starlette.types import ASGIApp, Receive, Scope, Send

from subtrack.api.problems import ProblemType, problem_response
from subtrack.security.lock import LockEnforcer
from subtrack.security.tokens import BACKGROUND_HEADER, SESSION_COOKIE

PUBLIC_PATHS = frozenset({"/health", "/auth/status", "/auth/unlock"})


class LockMiddleware:
    def __init__(self, app: ASGIApp, *, enforcer: LockEnforcer, prefix: str) -> None:
        self._app = app
        self._enforcer = enforcer
        self._prefix = prefix

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or not self._is_protected(scope["path"]):
            await self._app(scope, receive, send)
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

    def _is_protected(self, path: str) -> bool:
        if path != self._prefix and not path.startswith(f"{self._prefix}/"):
            return False
        return path.removeprefix(self._prefix) not in PUBLIC_PATHS
