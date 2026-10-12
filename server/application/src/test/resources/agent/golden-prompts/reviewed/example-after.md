## How submit_result takes a result
The arguments are one complete result object for this session's practice, not a list and not a JSON-encoded string. Each block below is a whole alternative call, with an illustrative outcome, not evidence from this work:
```json
{
 "outcome": "MET",
 "summary": "Added test asserts that malformed date input is rejected",
 "explanation": "The added assertion passes \"not-a-date\" to DateParser.parse and expects an error.",
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
 ],
 "evidence": {}
}
```

```json
{
 "outcome": "NOT_MET",
 "summary": "New error branch of the parser ships without a test",
 "explanation": "The change adds a branch that rejects an empty string; no added test feeds the parser one.",
 "citations": [],
 "problems": [
  {
   "severity": "MINOR",
   "summary": "Empty-string branch of the parser has no test",
   "explanation": "The change adds a guard that throws for an empty string; no added test passes one.",
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
   "basis": {
    "type": "introduced",
    "head": 0,
    "explanation": "The change adds this branch on the new side."
   }
  }
 ],
 "evidence": {
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
 "outcome": "NOT_APPLICABLE",
 "summary": "No completed merge is recorded",
 "explanation": "The captured provider record states that this work has not merged.",
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
 "evidence": {
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