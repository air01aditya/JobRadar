import requests

from models import RawJob

REMOTIVE_URL = "https://remotive.com/api/remote-jobs"


def fetch_remotive():
    resp = requests.get(REMOTIVE_URL, timeout=15)
    resp.raise_for_status()
    data = resp.json()

    jobs = []
    for item in data.get("jobs", []):
        jobs.append(RawJob(
            source="remotive",
            source_id=str(item.get("id")),
            title=item.get("title", "") or "",
            company=item.get("company_name", "") or "",
            location=item.get("candidate_required_location", "") or "",
            url=item.get("url", "") or "",
            description=item.get("description", "") or "",
            is_remote=True,
            posted_at=item.get("publication_date"),
        ))
    return jobs
