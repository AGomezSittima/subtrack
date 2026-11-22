import hashlib

SESSION_COOKIE = "subtrack_session"
BACKGROUND_HEADER = "x-subtrack-background"


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()
