"""Session cookie, background header and how session tokens are stored."""

import hashlib

SESSION_COOKIE = "subtrack_session"
BACKGROUND_HEADER = "x-subtrack-background"


def hash_token(token: str) -> str:
    """Lowercase hex SHA-256: the only form of a session token the database holds."""
    return hashlib.sha256(token.encode()).hexdigest()
