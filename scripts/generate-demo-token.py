#!/usr/bin/env python3
"""
Generate a signed demo token for TTS Lab showcase access.

Interactive usage (prompts for all inputs):
    python scripts/generate-demo-token.py

Non-interactive usage:
    python scripts/generate-demo-token.py <name> <hours> [jti] [env-or-url]

Environment shortcuts:
    main          -> https://tts-lab.served-by-c.de
    dev / develop -> https://dev.tts-lab.served-by-c.de
    <other>       -> https://<other>.tts-lab.served-by-c.de
    <full-url>    -> used as-is

Output: tokens/<jti>/url.txt, token.txt, qr.png

Requires: pip install pyjwt qrcode[pil]
The signing secret is read from TTS_LAB_DEMO_TOKEN_SIGNING_SECRET.
"""

import sys
import os
import datetime
import urllib.parse
import pathlib
import jwt          # pip install pyjwt
import qrcode       # pip install qrcode[pil]


def resolve_base_url(env: str) -> str:
    env = env.strip().rstrip("/")
    if env in ("main",):
        return "https://tts-lab.served-by-c.de"
    if env in ("dev", "develop"):
        return "https://dev.tts-lab.served-by-c.de"
    if env.startswith("http://") or env.startswith("https://"):
        return env
    return f"https://{env}.tts-lab.served-by-c.de"


def ask(prompt: str, default: str = "") -> str:
    suffix = f" [{default}]" if default else ""
    value = input(f"{prompt}{suffix}: ").strip()
    return value if value else default


def main():
    # --- collect inputs ---
    if len(sys.argv) >= 3:
        name     = sys.argv[1]
        hours    = int(sys.argv[2])
        jti      = sys.argv[3] if len(sys.argv) > 3 else None
        base_url = resolve_base_url(sys.argv[4]) if len(sys.argv) > 4 else "https://dev.tts-lab.served-by-c.de"
    else:
        print("=== TTS Lab Demo Token Generator ===\n")
        name     = ask("Display name (shown in app)")
        hours    = int(ask("Valid for how many hours", "48"))
        env      = ask("Environment (main / dev / feature-slug)", "dev")
        base_url = resolve_base_url(env)
        jti      = ask(f"Token ID (jti)", f"demo-{name.lower().replace(' ', '-')}") or None

    if not name:
        print("Error: name is required", file=sys.stderr)
        sys.exit(1)

    if jti is None:
        jti = f"demo-{name.lower().replace(' ', '-')}"

    secret = os.environ.get("TTS_LAB_DEMO_TOKEN_SIGNING_SECRET")
    if not secret:
        print("Error: TTS_LAB_DEMO_TOKEN_SIGNING_SECRET environment variable is not set", file=sys.stderr)
        sys.exit(1)

    # --- generate token ---
    expiry = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=hours)
    payload = {
        "jti":  jti,
        "name": name,
        "exp":  expiry,
    }
    token = jwt.encode(payload, secret, algorithm="HS256")
    url   = f"{base_url}/api/demo/activate?token={urllib.parse.quote(token)}"

    # --- save outputs ---
    out_dir = pathlib.Path(__file__).parent / "tokens" / jti
    out_dir.mkdir(parents=True, exist_ok=True)

    (out_dir / "token.txt").write_text(token, encoding="utf-8")
    (out_dir / "url.txt").write_text(url, encoding="utf-8")

    qr = qrcode.make(url)
    qr.save(out_dir / "qr.png")

    # --- print summary ---
    print(f"\nName    : {name}")
    print(f"JTI     : {jti}")
    print(f"Expires : {expiry.strftime('%Y-%m-%d %H:%M UTC')}  ({hours}h)")
    print(f"URL     : {url}")
    print(f"\nSaved to: {out_dir.resolve()}")
    print(f"  token.txt  — raw JWT")
    print(f"  url.txt    — activation URL")
    print(f"  qr.png     — QR code")


if __name__ == "__main__":
    main()
