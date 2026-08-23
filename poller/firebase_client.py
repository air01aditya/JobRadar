import json
import os

import firebase_admin
from firebase_admin import credentials, firestore, messaging

_app = None


def _project_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _load_credentials():
    # GitHub Actions supplies the whole service-account JSON as a secret value.
    raw_json = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON")
    if raw_json:
        # Strip a possible leading UTF-8 BOM (can sneak in via PowerShell/editor saves).
        return credentials.Certificate(json.loads(raw_json.lstrip("﻿")))

    # Local dev instead points at the downloaded file sitting in the project root.
    file_name = os.environ.get("FIREBASE_SERVICE_ACCOUNT_FILE")
    if not file_name:
        raise RuntimeError(
            "Set FIREBASE_SERVICE_ACCOUNT_JSON (raw JSON, for GitHub Actions) or "
            "FIREBASE_SERVICE_ACCOUNT_FILE (a file name in the project root, for local runs) in .env"
        )
    return credentials.Certificate(os.path.join(_project_root(), file_name))


def get_app():
    global _app
    if _app is None:
        _app = firebase_admin.initialize_app(_load_credentials())
    return _app


def get_firestore():
    get_app()
    return firestore.client()


def get_messaging():
    get_app()
    return messaging
