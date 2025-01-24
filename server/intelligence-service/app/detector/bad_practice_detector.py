from typing import List

class PullRequest:
    id: str
    title: str
    description: str

class Rule:
    name: str
    description: str
    bad_practice_id: str

class PullRequestWithBadPractices:
    pull_request_id: str
    bad_practice_ids: List[str]

def detectbadpractices(pull_requests: List[PullRequest], rules: List[Rule]) -> List[PullRequestWithBadPractices]:
    bad_practices = []
    for pull_request in pull_requests:
        bad_practice_ids = []
        for rule in rules:
            if rule.bad_practice_id in pull_request.description:
                bad_practice_ids.append(rule.bad_practice_id)
        bad_practices.append(PullRequestWithBadPractices(pull_request_id=pull_request.id, bad_practice_ids=bad_practice_ids))
    return bad_practices