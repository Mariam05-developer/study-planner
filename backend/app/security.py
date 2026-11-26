import base64
import hashlib
import hmac
import secrets
import time


def _scrypt(password, salt):
    return hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1, dklen=32)


def hash_password(password):
    salt = secrets.token_bytes(16)
    parts = [salt, _scrypt(password, salt)]
    return "$".join(base64.b64encode(p).decode() for p in parts)


def verify_password(password, stored):
    # the salt and hash are the last two parts
    salt, expected = [base64.b64decode(p) for p in stored.split("$")[-2:]]
    return hmac.compare_digest(_scrypt(password, salt), expected)


def new_token():
    return secrets.token_urlsafe(32)


def hash_token(token):
    return hashlib.sha256(token.encode()).hexdigest()


class LoginThrottle:
    """Locks a username for a few minutes after too many wrong passwords."""

    MAX_TRIES = 5
    LOCK_SECONDS = 300

    def __init__(self):
        self.failures = {}  # username -> times of failed logins

    def retry_after(self, username):
        """Seconds the user still has to wait, 0 if they can try now."""
        now = time.monotonic()
        recent = [t for t in self.failures.get(username, []) if now - t < self.LOCK_SECONDS]
        self.failures[username] = recent
        if len(recent) < self.MAX_TRIES:
            return 0
        return int(recent[0] + self.LOCK_SECONDS - now) + 1

    def record_failure(self, username):
        self.failures.setdefault(username, []).append(time.monotonic())

    def reset(self, username):
        self.failures.pop(username, None)
