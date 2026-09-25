# JobRadar

An Android app that watches for entry-level tech job postings so I don't have to
keep refreshing five different sites. Everything runs on-device — no backend,
no cloud database, nothing to keep alive.

## What it actually does

- **Aggregates** postings from Adzuna, RemoteOK, WeWorkRemotely and Remotive,
  plus a direct scan of ~130 company career pages across 8 ATS platforms
  (Greenhouse, Lever, Workday, SmartRecruiters, Workable, BambooHR, Recruitee,
  Breezy).
- **Filters** to fresher/0-1yr analyst and software/QA roles in India or remote
  — this is a tool for one specific job search, not a general board.
- **Dedupes** across sources (the same posting often shows up on more than one
  board) and cleans up postings with clock-skewed future dates.
- **Polls in the background** via WorkManager — the board aggregators every 15
  minutes, the heavier company-page scan hourly — and fires a local
  notification when something new and matching shows up.
- **Tracks applications**: bookmark a posting, get a "did you apply?" nudge
  when you return to the app after opening the link, set a deadline reminder.

## Why on-device

The first version used a Python poller on a GitHub Actions cron, writing to
Firestore and pushing through FCM. It worked, but it meant keeping a service
account alive, paying attention to a cloud database for a single-user app, and
debugging notification delivery through a system I didn't fully control.
Rebuilt it fully on-device: Room for storage, WorkManager for scheduling,
local notifications. One less moving part, and the whole thing runs from the
phone with no server anywhere. `poller/` still has the original Python
version — kept for reference, not used by the app anymore.

## Built with

Kotlin, Jetpack Compose, Room, WorkManager, OkHttp.

## Status

Personal-use, not on the Play Store — built for my own job search, not to be
a product.
