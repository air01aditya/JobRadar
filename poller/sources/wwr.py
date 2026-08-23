import feedparser

from models import RawJob

# We Work Remotely publishes a public RSS feed per category — no auth needed.
WWR_FEEDS = [
    "https://weworkremotely.com/categories/remote-programming-jobs.rss",
    "https://weworkremotely.com/categories/remote-full-stack-programming-jobs.rss",
    "https://weworkremotely.com/categories/remote-back-end-programming-jobs.rss",
    "https://weworkremotely.com/categories/remote-front-end-programming-jobs.rss",
]


def fetch_wwr():
    jobs = []
    for feed_url in WWR_FEEDS:
        parsed = feedparser.parse(feed_url)
        for entry in parsed.entries:
            # WWR titles are formatted "Company: Job Title".
            raw_title = entry.get("title", "") or ""
            if ":" in raw_title:
                company, title = raw_title.split(":", 1)
            else:
                company, title = "", raw_title

            jobs.append(RawJob(
                source="weworkremotely",
                source_id=entry.get("id", entry.get("link", "")) or entry.get("link", ""),
                title=title.strip(),
                company=company.strip(),
                location="Remote",
                url=entry.get("link", "") or "",
                description=entry.get("summary", "") or "",
                is_remote=True,
                posted_at=entry.get("published"),
            ))
    return jobs
