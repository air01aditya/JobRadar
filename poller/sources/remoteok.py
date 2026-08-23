import requests

from models import RawJob

REMOTEOK_URL = "https://remoteok.com/api"
# RemoteOK 403s requests that look like a bare script — a browser-like UA avoids that.
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    )
}


def fetch_remoteok():
    resp = requests.get(REMOTEOK_URL, headers=HEADERS, timeout=15)
    resp.raise_for_status()
    data = resp.json()

    jobs = []
    for item in data:
        # Index 0 of RemoteOK's response is a legal notice, not a job — it
        # has no "id" field, so this also guards against any other junk rows.
        if not isinstance(item, dict) or "id" not in item:
            continue
        jobs.append(RawJob(
            source="remoteok",
            source_id=str(item.get("id")),
            title=item.get("position", "") or item.get("title", "") or "",
            company=item.get("company", "") or "",
            location=item.get("location", "") or "Remote",
            url=item.get("url", "") or f"https://remoteok.com/remote-jobs/{item.get('id')}",
            description=item.get("description", "") or "",
            is_remote=True,
            posted_at=item.get("date"),
        ))
    return jobs
