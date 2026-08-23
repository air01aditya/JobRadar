from models import RawJob

# Software/full-stack + data/ML/analyst roles, per the confirmed role scope.
TECH_KEYWORDS = [
    "software engineer", "software developer", "software development engineer", "sde",
    "full stack", "full-stack", "fullstack",
    "backend developer", "back-end developer", "back end developer",
    "frontend developer", "front-end developer", "front end developer",
    "web developer", "application developer",
    "data analyst", "data scientist", "data engineer",
    "machine learning", "ml engineer", "ai engineer",
]

# Keywords that signal a job is explicitly NOT fresher/entry-level.
SENIOR_KEYWORDS = [
    "senior", "sr.", "sr ", "staff", "principal", "lead ", "director",
    "head of", "manager", "architect", "vp ", "vice president",
    "5+ year", "6+ year", "7+ year", "8+ year", "10+ year",
]

INDIA_LOCATIONS = [
    "india", "bangalore", "bengaluru", "hyderabad", "pune", "mumbai",
    "delhi", "gurgaon", "gurugram", "noida", "chennai", "kolkata",
    "ahmedabad", "jaipur", "kochi", "coimbatore",
]


def matches_role(job: RawJob) -> bool:
    text = f"{job.title} {job.description}".lower()
    return any(keyword in text for keyword in TECH_KEYWORDS)


def matches_experience(job: RawJob) -> bool:
    # Best-effort heuristic on free text, not a structured field — will have
    # some false positives/negatives. Biased toward inclusion (only rejects
    # postings that explicitly signal a senior level) since missing a
    # legitimate fresher posting is worse than seeing one extra irrelevant one.
    text = f"{job.title} {job.description}".lower()
    return not any(keyword in text for keyword in SENIOR_KEYWORDS)


def matches_location(job: RawJob) -> bool:
    if job.is_remote:
        return True
    text = job.location.lower()
    return any(keyword in text for keyword in INDIA_LOCATIONS)


def passes_all_filters(job: RawJob) -> bool:
    return matches_role(job) and matches_experience(job) and matches_location(job)
