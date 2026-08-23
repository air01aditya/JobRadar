import os
import sys
import traceback

from dotenv import load_dotenv

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# In GitHub Actions, real env vars (from secrets) are already set and there's
# no .env file — load_dotenv() just no-ops in that case.
load_dotenv(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env"))

import dedupe
import notify
from filters import passes_all_filters
from firebase_client import get_firestore
from sources import adzuna, remoteok, remotive, wwr

SOURCES = [
    ("adzuna", adzuna.fetch_adzuna),
    ("remotive", remotive.fetch_remotive),
    ("remoteok", remoteok.fetch_remoteok),
    ("weworkremotely", wwr.fetch_wwr),
]


def main():
    all_jobs = []
    for name, fetch_fn in SOURCES:
        try:
            jobs = fetch_fn()
            print(f"[{name}] fetched {len(jobs)} raw jobs")
            all_jobs.extend(jobs)
        except Exception as exc:
            print(f"[{name}] FAILED: {exc}")
            traceback.print_exc()

    # A single job can appear in more than one feed within the same source
    # (e.g. a WWR posting listed under both "programming" and "full-stack"),
    # so dedupe within this run before filtering/notifying.
    seen_in_run = set()
    deduped_jobs = []
    for job in all_jobs:
        key = (job.source, job.source_id)
        if key in seen_in_run:
            continue
        seen_in_run.add(key)
        deduped_jobs.append(job)

    matched = [job for job in deduped_jobs if passes_all_filters(job)]
    print(
        f"\n{len(matched)} / {len(deduped_jobs)} jobs matched filters "
        "(role=software/full-stack+data/ML, experience=fresher/entry, location=India/remote)\n"
    )

    db = get_firestore()
    first_run = dedupe.is_first_run(db)
    new_jobs = dedupe.get_new_jobs(db, matched)

    print(f"{len(new_jobs)} of those are new (not seen in a previous run)\n")
    for job_id, job in new_jobs:
        print(f"- [{job.source}] {job.title} @ {job.company} ({job.location}) -> {job.url}")

    dedupe.record_new_jobs(db, new_jobs)

    if first_run:
        print("\nFirst run detected (empty /seenJobIds) — recorded all matches but suppressed notifications.")
    else:
        notify.send_notifications(db, new_jobs)


if __name__ == "__main__":
    main()
