#!/usr/bin/env python3
"""
Scans built APK/AAB files for server secrets that must never ship to phones:
  * Supabase secret keys (sb_secret_...)
  * JWTs whose payload has "role": "service_role"
  * private keys / Firebase service-account JSON
Exit code 1 if anything is found. Usage: scripts/check_apk_secrets.py app/build/outputs/apk/**/*.apk
"""
import base64, json, re, sys, zipfile

JWT = re.compile(rb"eyJ[A-Za-z0-9_-]{8,}\.(eyJ[A-Za-z0-9_-]{8,})\.[A-Za-z0-9_-]{8,}")
PATTERNS = {
    "Supabase secret key": re.compile(rb"sb_secret_[A-Za-z0-9_-]{10,}"),
    "private key": re.compile(rb"-----BEGIN (RSA |EC )?PRIVATE KEY-----"),
    "service account JSON": re.compile(rb'"type"\s*:\s*"service_account"'),
}

def b64url(segment: bytes) -> bytes:
    return base64.urlsafe_b64decode(segment + b"=" * (-len(segment) % 4))

def scan(path: str) -> list[str]:
    findings = []
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            data = z.read(name)
            for label, rx in PATTERNS.items():
                if rx.search(data):
                    findings.append(f"{path}!{name}: {label}")
            for m in JWT.finditer(data):
                try:
                    payload = json.loads(b64url(m.group(1)))
                except Exception:
                    continue
                if payload.get("role") == "service_role":
                    findings.append(f"{path}!{name}: Supabase service_role JWT")
    return findings

if __name__ == "__main__":
    files = sys.argv[1:]
    if not files:
        sys.exit("usage: check_apk_secrets.py <apk|aab>...")
    found = [f for p in files for f in scan(p)]
    for f in found:
        print("SECRET FOUND:", f)
    print(f"scanned {len(files)} file(s): {'FAILED' if found else 'no server secrets found'}")
    sys.exit(1 if found else 0)
