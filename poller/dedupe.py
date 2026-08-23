import hashlib

from firebase_admin import firestore as fb_firestore

from models import RawJob


def make_job_id(job: RawJob) -> str:
    raw = f"{job.source}:{job.source_id or job.url}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:24]


def is_first_run(db) -> bool:
    return len(list(db.collection("seenJobIds").limit(1).stream())) == 0


def get_new_jobs(db, jobs):
    """Return (job_id, job) pairs for jobs not already present in /seenJobIds."""
    new_jobs = []
    seen_ref = db.collection("seenJobIds")
    for job in jobs:
        job_id = make_job_id(job)
        if seen_ref.document(job_id).get().exists:
            continue
        new_jobs.append((job_id, job))
    return new_jobs


def record_new_jobs(db, new_jobs):
    """Write each new job to /jobs and mark it seen in /seenJobIds, in one batch."""
    if not new_jobs:
        return
    batch = db.batch()
    for job_id, job in new_jobs:
        batch.set(db.collection("jobs").document(job_id), {
            "source": job.source,
            "title": job.title,
            "company": job.company,
            "location": job.location,
            "url": job.url,
            "isRemote": job.is_remote,
            "postedAt": job.posted_at,
            "firstSeenAt": fb_firestore.SERVER_TIMESTAMP,
        })
        batch.set(db.collection("seenJobIds").document(job_id), {
            "firstSeenAt": fb_firestore.SERVER_TIMESTAMP,
        })
    batch.commit()
