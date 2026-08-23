import os

import requests

from models import RawJob

ADZUNA_URL = "https://api.adzuna.com/v1/api/jobs/in/search/1"


def fetch_adzuna():
    app_id = os.environ.get("ADZUNA_APP_ID")
    app_key = os.environ.get("ADZUNA_APP_KEY")
    if not app_id or not app_key:
        print("[adzuna] Skipping: ADZUNA_APP_ID / ADZUNA_APP_KEY not set")
        return []

    # Adzuna's "what" param takes one query string, not a keyword OR-list, so
    # we pull the whole IT category sorted by newest and filter client-side
    # in filters.py — cheaper on quota and simpler than looping per keyword.
    params = {
        "app_id": app_id,
        "app_key": app_key,
        "results_per_page": 50,
        "content-type": "application/json",
        "category": "it-jobs",
        "sort_by": "date",
    }
    resp = requests.get(ADZUNA_URL, params=params, timeout=15)
    resp.raise_for_status()
    data = resp.json()

    jobs = []
    for item in data.get("results", []):
        jobs.append(RawJob(
            source="adzuna",
            source_id=str(item.get("id")),
            title=item.get("title", "") or "",
            company=(item.get("company") or {}).get("display_name", "") or "",
            location=(item.get("location") or {}).get("display_name", "") or "",
            url=item.get("redirect_url", "") or "",
            description=item.get("description", "") or "",
            is_remote=False,
            posted_at=item.get("created"),
        ))
    return jobs
