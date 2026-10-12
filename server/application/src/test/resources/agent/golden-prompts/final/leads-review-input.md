#### Leads from the practice script for `test-practice` — starting points to check against the criteria, not verdicts
- Check each added authentication call against the criteria.

**Leads:**
- An added call to insecure(); check whether a checked() call guards it.
  - at `src/Auth.java` [L10]: `insecure();` [guarded=false]
- The repository's authentication helper that the change could call instead.
  - at `src/Checked.java:2`: `checked(token);`
- The pull request record that names the change's intent.
  - at `evidence/metadata.json:1`: `{"basis":{"admission_fields":[],"other_fields":"CAPTURE"},"pr_number":3,"pr_url":"https://gitlab.example/group/repo/-/me`

**Facts:**
- insecureCalls = 1
- checkedCalls = 0
- Not rated: 2 added calls in generated files (too-large). Read those yourself.