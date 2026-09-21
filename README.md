# JobRadar

Job boards expect you to come to them. JobRadar inverts that: a scheduled poller watches five sources on your behalf and pushes a notification to your phone the moment something matching turns up.

It's two halves — a Python ingestion service that runs on a cron, and an Android client that reads what it produces.

## How it works

```
┌─────────────────────────────────────────────────────┐
│  GitHub Actions — every 15 minutes                  │
│                                                     │
│  Adzuna ─┐                                          │
│  Remotive┤                                          │
│  RemoteOK├──▶ normalise ──▶ filter ──▶ dedupe ──┐   │
│  WWR    ─┤    (RawJob)      role/exp/loc        │   │
│  Telegram┘                                      │   │
└─────────────────────────────────────────────────┼───┘
                                                  ▼
                                    ┌──────────────────────┐
                                    │  Firestore           │
                                    │  /jobs               │
                                    │  /seenJobIds         │
                                    │  /deviceTokens       │
                                    └──────┬────────┬──────┘
                                           │        │
                                      FCM push   read feed
                                           │        │
                                    ┌──────▼────────▼──────┐
                                    │  Android (Compose)   │
                                    │  Feed · Tracker      │
                                    │  Room · WorkManager  │
                                    └──────────────────────┘
```

## The backend

`poller/` is the interesting half. Five adapters each return a common `RawJob`, and the pipeline does the rest.

**Sources are isolated.** Each adapter runs in its own `try/except`. One source changing its HTML or rate-limiting you doesn't take down the run — it logs the failure and the other four still deliver.

**Deduplication is two-layered.** Within a single run, a posting can legitimately appear twice (WeWorkRemotely lists the same job under both *programming* and *full-stack*), so identical `(source, source_id)` pairs collapse first. Across runs, each job gets a stable ID — `sha256(source:source_id)` truncated to 24 chars — checked against a `/seenJobIds` collection. Content-derived rather than random, so the same posting always produces the same ID and can never be notified twice.

**Writes are batched.** New jobs go into `/jobs` and `/seenJobIds` in a single Firestore batch commit, so a crash mid-write can't leave a job recorded as "seen" without ever having been stored.

**The first run is silent.** An empty `/seenJobIds` means cold start — every match would look new, and you'd get fifty notifications at once. The first run records everything and suppresses all pushes.

**Notifications collapse above a threshold.** More than five new matches sends one grouped push instead of five separate ones.

**Filtering is a documented heuristic, not a promise.** Postings expose experience level as free text, not a structured field, so `filters.py` matches on keywords and is deliberately biased toward inclusion — it only rejects a job that explicitly signals seniority. Missing a real fresher posting costs more than showing one irrelevant result.

### Firestore rules

The security model is deliberately asymmetric, and each rule in `firestore.rules` says why:

| Collection | Client read | Client write | Reasoning |
| :--- | :--- | :--- | :--- |
| `/jobs` | ✅ | ❌ | The feed reads it; only the poller's Admin SDK writes it |
| `/seenJobIds` | ❌ | ❌ | Internal dedupe state, no client business touching it |
| `/deviceTokens` | ❌ | ✅ | The app registers its FCM token; only the poller reads them back |

The poller uses the Firebase Admin SDK, which bypasses these rules entirely — they exist to constrain the *client*, which is the only untrusted party.

## The Android client

Kotlin and Jetpack Compose. A **Feed** of matched postings, and a **Tracker** for applications you've actually sent — with stages, deadlines and manual entry for jobs found elsewhere. Room backs local storage, and WorkManager drives a background check plus deadline reminders so the tracker nags you before a closing date rather than after.

## Running it

### Poller

```bash
pip install -r poller/requirements.txt
```

Create a `.env` in the project root:

```
ADZUNA_APP_ID=your_id
ADZUNA_APP_KEY=your_key
FIREBASE_SERVICE_ACCOUNT_FILE=your-service-account.json
```

Then:

```bash
python poller/poll.py
```

Adzuna is optional — without those two variables that source is skipped with a log line and the other sources still run.

In GitHub Actions the same values come from repository secrets, with the whole service-account JSON passed as `FIREBASE_SERVICE_ACCOUNT_JSON`. No `.env` is needed there; `load_dotenv()` simply no-ops.

### Android

Put your `google-services.json` in `app/`, and add your Adzuna credentials to `local.properties`:

```properties
adzuna.appId=your_id
adzuna.appKey=your_key
```

They're injected as `BuildConfig` fields at compile time. Then build as usual.

> `.env`, `google-services.json`, the service-account JSON and `local.properties` are all gitignored. No credential belongs in this repository.

## Limitations

- Experience-level filtering is keyword-based and will let some senior roles through.
- The Telegram source scrapes the public `t.me/s/` web preview, so it breaks if Telegram changes that markup.
- Location filtering matches a fixed list of Indian cities plus anything flagged remote.

## Stack

Kotlin · Jetpack Compose · Room · WorkManager · Python · Firebase Firestore · FCM · GitHub Actions
