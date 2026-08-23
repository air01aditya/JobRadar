from dataclasses import dataclass
from typing import Optional


@dataclass
class RawJob:
    source: str
    source_id: str
    title: str
    company: str
    location: str
    url: str
    description: str
    is_remote: bool
    posted_at: Optional[str] = None
