#!/usr/bin/env python3
"""
Generate a signed demo token for TTS Lab showcase access.

Usage:
    python scripts/generate-demo-token.py <name> <hours> [jti] [base-url]

Examples:
    python scripts/generate-demo-token.py "Hackathon-Besucher" 48
    python scripts/generate-demo-token.py "Konferenz-Gast" 24 demo-conf-2026
    python scripts/generate-demo-token.py "Test" 1 test https://f-demo.tts-lab.served-by-c.de

The signing secret is read from TTS_LAB_DEMO_TOKEN_SIGNING_SECRET.
Default base URL: https://dev.tts-lab.served-by-c.de
"""

import sys
import os
import datetime
import urllib.parse
import jwt  # pip install pyjwt

DEFAULT_BASE_URL = "https://dev.tts-lab.served-by-c.de"

def main():
    if len(sys.argv) < 3:
        print(f"Usage: {sys.argv[0]} <name> <hours> [jti] [base-url]", file=sys.stderr)
        sys.exit(1)

    name = sys.argv[1]
    hours = int(sys.argv[2])
    jti = sys.argv[3] if len(sys.argv) > 3 else f"demo-{name.lower().replace(' ', '-')}"
    base_url = sys.argv[4].rstrip("/") if len(sys.argv) > 4 else DEFAULT_BASE_URL

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
    url = f"{base_url}/api/demo/activate?token={urllib.parse.quote(token)}"
    print(url)

if __name__ == "__main__":
    main()
