#!/usr/bin/env python3
"""Static checks for the CubaPincha Android 2.10.0 Supabase migration."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main"
JAVA = APP / "java/com/cubapincha/job"
checks = []

def check(label, condition):
    checks.append((label, bool(condition)))

def read(path):
    return path.read_text(encoding="utf-8")

build = read(ROOT / "app/build.gradle")
manifest = read(APP / "AndroidManifest.xml")
repo = read(JAVA / "data/Repository.java")
session = read(JAVA / "data/SessionManager.java")
auth = read(JAVA / "data/SupabaseAuthRest.java")
api = read(JAVA / "data/SupabaseApiClient.java")
activity = read(JAVA / "ui/MainActivity.java")
push = read(JAVA / "util/CubaPinchaMessagingService.java")

check("versionCode 25 / versionName 2.10.0", "versionCode 25" in build and 'versionName "2.10.0"' in build)
check("Firebase Messaging retained; Firebase Auth removed", "firebase-messaging" in build and "firebase-auth" not in build and not list(APP.rglob("*FirebaseAuth*.java")))
check("Supabase email signup uses PKCE S256", '"code_challenge"' in auth and '"code_challenge_method", "s256"' in auth and '"/token?grant_type=pkce"' in auth)
check("Google ID token exchanges for a Supabase session", '"/token?grant_type=id_token"' in auth and 'body.put("id_token", googleIdToken)' in auth)
check("Auth REST sends publishable key and user bearer token", 'setRequestProperty("apikey", apiKey.trim())' in auth and '"Bearer " + accessToken' in auth)
check("Edge Function host is pinned and uses apikey plus Supabase bearer", 'ogimwehtnhegfoamiwzm.supabase.co' in api and 'conn.setRequestProperty("apikey", apiKey)' in api and '"Bearer " + accessToken' in api)
check("Supabase tokens and PKCE verifier use encrypted storage", "EncryptedSharedPreferences" in session and "KEY_REFRESH_TOKEN" in session and "KEY_PKCE_VERIFIER" in session)
check("Auth callback only accepts PKCE codes, not URL bearer tokens", 'exchangeAuthCode(code' in activity and "saveAuthTokens(access, refresh" not in activity and "getQueryParameter(\"access_token\")" not in activity)
check("Google profile registration reuses the Supabase session", "samePendingGoogle" in repo and "getPendingGoogleIdToken()" in repo and "tryRefreshSession()" in repo)
check("Demo mode and offline queue remain", "session.isDemoMode()" in repo and "db.enqueue(a)" in repo and "PendingAction" in repo)
check("syncActions payload stays nested with idempotency ID", 'o.put("clientActionId", a.clientActionId)' in api and 'o.put("actionType", a.actionType)' in api and 'o.put("payload", new JSONObject' in api)
check("JWT refresh is attempted in synchronous background sync", "ApiResult<String> result = doSyncNow()" in repo and '"AUTH_REQUIRED".equals(result.errorCode) && tryRefreshSession()' in repo)
check("FCM remains separate and local preferences suppress pushes", "firebase-messaging" in build and "!session.isLoggedIn() || !session.isNotificationsEnabled()" in push and ".remove(KEY_FCM_TOKEN)" not in session[session.index("public void logout()") :])
check("No service_role key or hardcoded JWT in Android source", not re.search(r"service_role|eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.", "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in APP.rglob("*.*") if p.is_file()), re.I))

failed = [name for name, ok in checks if not ok]
print("CubaPincha 2.10.0 — validación estática de migración Supabase")
for name, ok in checks:
    print(f"{'OK' if ok else 'FAIL'}  {name}")
print(f"Resultado: {len(checks)-len(failed)}/{len(checks)} checks aprobados")
if failed:
    sys.exit(1)
