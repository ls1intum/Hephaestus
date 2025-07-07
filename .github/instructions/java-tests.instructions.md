---
applyTo: "server/application-server/src/test/**/*.java"
---

Follow this mantra for high-value test cases:
* Single responsibility: one behavior/assertion per test
* Clear & concise: state the objective simply
* Independent: no hidden dependencies
* Traceable: link directly to requirements
* Repeatable: consistent setup and data
* Maintainable: easy to update when things change
* Focus on risk: cover critical flows first
* Minimal setup: avoid unnecessary steps
* Fast execution: fit seamlessly into CI pipelines
* Realistic data: use representative scenarios
* Concise expected results: one clear outcome per test
* Arrange-Act-Assert (AAA): keep structure clear
* Tests may run in parallel, avoid required cleanup and assume that there might be data from previous tests in the database