## How report_observation takes observations
The arguments are one complete observation object for this session's practice, not a list and not a JSON-encoded string. Each block below is a whole alternative call, with an illustrative outcome, not evidence from this work:
```json
{
 "revises": null,
 "practiceSlug": "<the practice's slug>",
 "summary": "Added test asserts that malformed date input is rejected",
 "outcome": "MET",
 "severity": null,
 "evidenceRationale": "The added assertion passes \"not-a-date\" to DateParser.parse and expects an error.",
 "evidence": {
  "citations": [
   {
    "sourceKind": "scm.pull-request.diff",
    "artifactPath": "evidence/change.json",
    "path": "Tests/DateParserTests.swift",
    "side": "NEW",
    "startLine": 12,
    "endLine": 12,
    "quote": "XCTAssertThrowsError(try DateParser.parse(\"not-a-date\"))"
   }
  ]
 }
}
```

```json
{
 "revises": null,
 "practiceSlug": "<the practice's slug>",
 "summary": "New error branch of the parser ships without a test",
 "outcome": "NOT_MET",
 "severity": "MINOR",
 "evidenceRationale": "The change adds a branch that rejects an empty string; no added test feeds the parser one.",
 "evidence": {
  "citations": [
   {
    "sourceKind": "scm.pull-request.diff",
    "artifactPath": "evidence/change.json",
    "path": "App/DateParser.swift",
    "side": "NEW",
    "startLine": 30,
    "endLine": 30,
    "quote": "guard !text.isEmpty else { throw ParseError.empty }"
   }
  ],
  "search": {
   "consulted": [
    "scm.pull-request.diff"
   ],
   "lookedFor": "an added test that passes an empty string to the parser",
   "boundary": "the added test files of this change; tests outside the change were not read"
  }
 }
}
```

```json
{
 "revises": null,
 "practiceSlug": "<the practice's slug>",
 "summary": "No completed merge is recorded",
 "outcome": "NOT_APPLICABLE",
 "severity": null,
 "evidenceRationale": "The captured provider record states that this work has not merged.",
 "evidence": {
  "citations": [
   {
    "sourceKind": "scm.pull-request.core",
    "artifactPath": "evidence/metadata.json",
    "path": "evidence/metadata.json",
    "startLine": 3,
    "endLine": 3,
    "quote": "\"is_merged\": false"
   }
  ],
  "inapplicability": {
   "consulted": [
    "scm.pull-request.core"
   ],
   "subject": "work that has merged",
   "ruledOutBy": "the provider records is_merged as false"
  }
 }
}
```