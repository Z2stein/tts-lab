#!/usr/bin/env python3
"""
Generate a signed demo token for TTS Lab showcase access.

Usage:
    python scripts/generate-demo-token.py <name> <hours> <jti>

Example:
    python scripts/generate-demo-token.py "Hackathon-Besucher" 48 demo-hackathon-2026

The signing secret is read from the environment variable TTS_LAB_DEMO_TOKEN_SIGNING_SECRET.
"""

import sys
import os
import datetime
import jwt  # pip install pyjwt

def main():
    if len(sys.argv) < 3:
        print(f"Usage: {sys.argv[0]} <name> <hours> [jti]", file=sys.stderr)
        sys.exit(1)

    name = sys.argv[1]
    hours = int(sys.argv[2])
    jti = sys.argv[3] if len(sys.argv) > 3 else f"demo-{name.lower().replace(' ', '-')}"

    secret = os.environ.get("TTS_LAB_DEMO_TOKEN_SIGNING_SECRET")
    if not secret:
        print("Error: TTS_LAB_DEMO_TOKEN_SIGNING_SECRET environment variable is not set", file=sys.stderr)
        sys.exit(1)

    payload = {
        "jti": jti,
        "name": name,
        "exp": datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=hours),
    }

    token = jwt.encode(payload, secret, algorithm="HS256")
    print(token)

if __name__ == "__main__":
    main()
