from firebase_admin import messaging

GROUP_THRESHOLD = 5


def get_device_tokens(db):
    tokens = []
    for doc in db.collection("deviceTokens").stream():
        token = (doc.to_dict() or {}).get("token") or doc.id
        if token:
            tokens.append(token)
    return tokens


def send_notifications(db, new_jobs):
    if not new_jobs:
        return

    tokens = get_device_tokens(db)
    if not tokens:
        print("[notify] No device token registered yet (app not installed/opened) — skipping push.")
        return

    if len(new_jobs) > GROUP_THRESHOLD:
        _send_grouped(tokens, len(new_jobs))
    else:
        for job_id, job in new_jobs:
            _send_single(tokens, job_id, job)


def _send_single(tokens, job_id, job):
    for token in tokens:
        message = messaging.Message(
            token=token,
            notification=messaging.Notification(
                title=f"New: {job.title}",
                body=f"{job.company} — {job.location}",
            ),
            data={"jobId": job_id, "url": job.url},
            android=messaging.AndroidConfig(priority="high"),
        )
        _try_send(message, token)


def _send_grouped(tokens, count):
    for token in tokens:
        message = messaging.Message(
            token=token,
            notification=messaging.Notification(
                title="New job matches",
                body=f"{count} new postings match your filters — open JobRadar to see them.",
            ),
            android=messaging.AndroidConfig(priority="high"),
        )
        _try_send(message, token)


def _try_send(message, token):
    try:
        messaging.send(message)
    except Exception as exc:
        print(f"[notify] Failed to send to token ...{token[-6:]}: {exc}")
