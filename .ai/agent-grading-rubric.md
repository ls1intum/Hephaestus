# Agent Grading Rubric: AI-Powered Code Review Evaluation

**Version:** 1.0
**Date:** 2026-03-30
**Scope:** Evaluating Hephaestus AI code review agents (OpenCode/GPT-5.4-mini, Claude Code/Sonnet 4.6) against a known-defect SettingsView.swift MR
**Theoretical Framework:** Cognitive Apprenticeship (Collins, Brown & Newman, 1989)

---

## Ground Truth: Expected Findings

The SettingsView.swift MR contains the following intentionally planted defects. This is the oracle against which detection accuracy is measured.

| # | Defect | Expected Practice Slug | Expected Verdict | Expected Severity | Expected Confidence |
|---|--------|----------------------|-----------------|-------------------|-------------------|
| 1 | Hardcoded GitHub PAT (`ghp_...`) | `hardcoded-secrets` | NEGATIVE | CRITICAL | >= 0.95 |
| 2 | Hardcoded PostgreSQL credentials | `hardcoded-secrets` | NEGATIVE | CRITICAL | >= 0.95 |
| 3 | `try!` / force-unwrap crash paths | `fatal-error-crash` | NEGATIVE | MAJOR/CRITICAL | >= 0.85 |
| 4 | Empty catch block (deletion errors) | `silent-failure-patterns` | NEGATIVE | MAJOR | >= 0.90 |
| 5 | Networking/JSON/persistence in View | `view-logic-separation` | NEGATIVE | MAJOR | >= 0.85 |
| 6 | Missing error state display | `error-state-handling` | NEGATIVE | MAJOR/MINOR | >= 0.80 |
| 7 | No `#Preview` block | `preview-quality` | NEGATIVE | MINOR | >= 0.85 |
| 8 | Debug `print` left in code | `code-hygiene` | NEGATIVE | MINOR | >= 0.85 |
| 9 | `@State` properties not private | `state-ownership-misuse` | NEGATIVE | INFO | >= 0.70 |
| 10 | UserDefaults storing auth token | `hardcoded-secrets` or `silent-failure-patterns` | NEGATIVE | MAJOR | >= 0.75 |

**Core defect count:** 10 (some may legitimately map to multiple practices)
**Hard-stop rule defects:** 2 (items 1-2, non-negotiable CRITICAL)

---

## Dimension Weights

| # | Dimension | Weight | Rationale |
|---|-----------|--------|-----------|
| 1 | Detection Accuracy | 25% | The foundation -- everything else is worthless if you miss the defect |
| 2 | Severity Calibration | 10% | Severity directly drives student attention allocation |
| 3 | Evidence Quality | 12% | Fabricated evidence destroys trust; correct evidence enables learning |
| 4 | Reasoning Depth | 10% | The "why" is the pedagogical payload |
| 5 | Guidance Actionability | 12% | Students must be able to act on feedback or it is noise |
| 6 | Pedagogical Method | 8% | Correct CA method matching accelerates skill acquisition |
| 7 | Inline Comments Quality | 8% | Contextual placement reduces cognitive load (visual-contextual feedback) |
| 8 | Output Integrity | 5% | Schema violations break the pipeline; duplicates waste attention |
| 9 | Efficiency | 5% | Cost and time matter at scale (1,071 MRs in the dataset) |
| 10 | MR Summary Quality | 5% | The entry point -- if this fails, students may not read further |
| | **Total** | **100%** | |

---

## Dimension 1: Detection Accuracy (25%)

**What:** Did the agent find what it should find, avoid what it should not, and correctly match findings to practice slugs?

**Metrics:**
- **Recall** = (True Positives) / (Ground Truth Defects) -- did it find the planted defects?
- **Precision** = (True Positives) / (All Reported Negatives) -- are reported issues real?
- **F1** = Harmonic mean of precision and recall
- **Hard-stop recall** = Did it catch ALL CRITICAL/hard-stop items (hardcoded secrets)?
- **Slug accuracy** = Did each finding map to the correct practice slug?

### Grade Criteria

**A+ (Exceptional -- nearly impossible)**
- Recall >= 90% on ground truth defects (9-10 of 10 found)
- Hard-stop recall = 100% (both hardcoded secrets found, both rated CRITICAL)
- Precision >= 90% (zero or one false positive across all findings)
- F1 >= 0.90
- Every finding maps to the correct practice slug with zero misattribution
- No hallucinated defects (invented problems not present in the code)
- Correctly produces NOT_APPLICABLE for practices irrelevant to this MR (e.g., commit-discipline if not applicable)

**A (Excellent)**
- Recall >= 80% (8+ of 10)
- Hard-stop recall = 100%
- Precision >= 80% (at most 2 false positives)
- F1 >= 0.80
- At most 1 slug misattribution (finding is real but mapped to wrong practice)
- No hallucinated defects

**B (Good)**
- Recall >= 70% (7+ of 10)
- Hard-stop recall = 100%
- Precision >= 70% (at most 3 false positives)
- F1 >= 0.70
- At most 2 slug misattributions
- At most 1 minor hallucinated defect

**C (Adequate)**
- Recall >= 50% (5+ of 10)
- Hard-stop recall >= 50% (at least 1 of 2 secrets found)
- Precision >= 60%
- F1 >= 0.55
- Slug misattributions <= 3

**D (Poor)**
- Recall 30-49% (3-4 of 10)
- Hard-stop recall >= 50%
- Precision 40-59%
- Multiple false positives (4+) creating noise
- Several slug misattributions

**F (Failing)**
- Recall < 30% (missed majority of planted defects)
- Hard-stop recall = 0% (missed ALL hardcoded secrets -- disqualifying)
- OR precision < 40% (more noise than signal)
- OR hallucinated >= 3 defects that do not exist in the code
- OR produced zero findings (empty output)

**Automatic F override:** Missing both hardcoded secrets is an automatic F regardless of other scores. These are the hard-stop rules -- an agent that cannot detect `ghp_` tokens in source code is not fit for purpose.

---

## Dimension 2: Severity Calibration (10%)

**What:** Are severity ratings (CRITICAL/MAJOR/MINOR/INFO) correctly calibrated per the practice catalogue definitions?

**Reference calibration (from practices.json):**
- CRITICAL: Only hardcoded-secrets and fatal-error-crash in certain contexts
- MAJOR: Silent failure patterns (Tier 1), view-logic-separation (networking in view), error-state-handling (both loading+error missing)
- MINOR: Code hygiene (debug prints), preview-quality, state-ownership-misuse (non-private @State), naming issues
- INFO: Positive findings only; non-private @State is borderline INFO/MINOR

### Grade Criteria

**A+ (Exceptional)**
- Every finding's severity exactly matches the practice catalogue specification
- Hardcoded secrets are CRITICAL (not MAJOR or lower)
- Empty catch block is MAJOR (not MINOR)
- Debug print is MINOR (not MAJOR)
- Non-private @State is INFO (not MAJOR)
- POSITIVE findings (if any) are correctly rated INFO
- Zero over-escalations (nothing is CRITICAL that should be MINOR)
- Zero under-escalations (nothing is INFO that should be MAJOR)

**A (Excellent)**
- At most 1 severity off by one level (e.g., MAJOR rated as MINOR)
- No severity off by two or more levels
- Hard-stop items correctly rated CRITICAL
- No CRITICAL ratings for non-critical issues

**B (Good)**
- At most 2 severities off by one level
- No severity off by two or more levels
- Hard-stop items correctly rated CRITICAL
- General trend is correct (security > crashes > logic > style)

**C (Adequate)**
- At most 3 severities off by one level
- At most 1 severity off by two levels
- Hard-stop items rated at least MAJOR

**D (Poor)**
- 4+ severity miscalibrations
- OR hard-stop items rated below MAJOR
- OR CRITICAL assigned to style/naming issues (crying wolf)

**F (Failing)**
- Hard-stop secrets rated MINOR or INFO
- OR systematic inversion (style issues rated higher than security issues)
- OR all findings rated the same severity (no differentiation)
- OR severity field missing from multiple findings

---

## Dimension 3: Evidence Quality (12%)

**What:** Does the evidence contain correct file paths, accurate line numbers, real code snippets from the actual diff, and no fabricated content?

**Schema reference:** `evidence: { locations: [{path, startLine, endLine}], snippets: [string] }`

### Grade Criteria

**A+ (Exceptional)**
- Every finding with a NEGATIVE verdict includes evidence with both locations and snippets
- All file paths are correct (match actual file in the MR diff)
- All line numbers are accurate to within +/- 0 lines of the actual defect location
- All code snippets are verbatim extracts from the actual diff (character-for-character match)
- Evidence spans are appropriately scoped (not too narrow, not entire-file)
- No evidence provided for findings where evidence is genuinely unavailable (e.g., missing-preview is about absence)

**A (Excellent)**
- >= 90% of NEGATIVE findings include evidence with locations and snippets
- All file paths correct
- Line numbers accurate to within +/- 2 lines
- Snippets are real code from the diff (minor whitespace differences tolerated)
- At most 1 finding with missing evidence that should have it

**B (Good)**
- >= 75% of NEGATIVE findings include evidence
- File paths correct
- Line numbers accurate to within +/- 5 lines
- Snippets recognizably from the diff (not fabricated)
- At most 2 findings with missing evidence

**C (Adequate)**
- >= 50% of NEGATIVE findings include evidence
- File paths mostly correct (at most 1 wrong)
- Line numbers in the right ballpark (within +/- 10 lines)
- Snippets mostly real (at most 1 that looks paraphrased)

**D (Poor)**
- < 50% of findings include evidence
- OR 2+ incorrect file paths
- OR line numbers frequently off by > 10 lines
- OR 2+ fabricated/hallucinated code snippets

**F (Failing)**
- Evidence systematically fabricated (code snippets that do not exist in the diff)
- OR line numbers are invented (pointing to lines that contain different code)
- OR file paths reference files not in the MR
- OR evidence field missing entirely from all findings
- OR evidence contains code from a different project/language

**Fabrication detection protocol:** For each snippet, verify it exists in the actual diff. A single fabricated snippet is a serious trust violation. Two or more fabricated snippets across different findings is an automatic cap at D.

---

## Dimension 4: Reasoning Depth (10%)

**What:** Does the reasoning field explain WHY the detected pattern is problematic, not just WHAT was detected? Does it connect to real-world consequences?

**Quality hierarchy (ascending):**
1. **Tautological:** "This is bad because it is a bad practice" -- zero information
2. **Descriptive:** "This code has an empty catch block" -- restates the finding title
3. **Explanatory:** "Empty catch blocks swallow exceptions, making debugging difficult" -- explains mechanism
4. **Consequential:** "This empty catch block silently discards the file deletion error. If deletion fails due to permissions, the user sees a success state while the file remains, leading to data inconsistency" -- explains real-world user impact
5. **Contextual:** Level 4 + connects to THIS specific code's context (e.g., "Since this is a settings view handling auth tokens, a failed save could leave stale credentials cached")

### Grade Criteria

**A+ (Exceptional)**
- >= 80% of findings reach Level 4 (consequential) or Level 5 (contextual)
- Zero findings at Level 1 (tautological)
- Reasoning connects to user-facing impact for UI-related issues
- Reasoning connects to security impact for secret/credential issues
- Reasoning demonstrates understanding of the specific code context, not generic boilerplate
- Each reasoning is distinct (no copy-paste across findings with different defects)
- Reasoning for POSITIVE findings explains what the student did well specifically

**A (Excellent)**
- >= 60% of findings reach Level 4+
- Zero findings at Level 1
- At most 20% at Level 2
- Reasoning is specific to the actual code, not generic descriptions

**B (Good)**
- >= 40% of findings reach Level 4+
- At most 1 finding at Level 1
- Majority (>50%) at Level 3+
- Some specificity to the actual code

**C (Adequate)**
- Majority at Level 3 (explanatory)
- At most 2 at Level 1
- Some generic reasoning tolerated if technically correct

**D (Poor)**
- Majority at Level 2 (descriptive) -- restates the detection
- OR 3+ findings at Level 1
- OR reasoning frequently incorrect (wrong explanation of why something is bad)

**F (Failing)**
- Majority at Level 1 (tautological)
- OR reasoning contains factual errors about Swift/SwiftUI behavior
- OR reasoning contradicts the verdict (says "this is fine" but verdict is NEGATIVE)
- OR reasoning field missing from majority of findings
- OR reasoning is clearly generic template text not related to the actual code

---

## Dimension 5: Guidance Actionability (12%)

**What:** Can a student reading only the guidance field perform the fix without additional research? Is it specific enough to act on, vague enough to still require thinking?

**Actionability spectrum:**
1. **Useless:** "Fix this" / "This should be improved" -- no direction whatsoever
2. **Vague:** "Use better error handling" -- direction but no specifics
3. **Directional:** "Replace the empty catch block with error logging" -- what to do, not how
4. **Actionable:** "In the catch block, set `self.errorMessage = error.localizedDescription` and display it with `.alert`" -- specific steps
5. **Pedagogical:** Level 4 + explains the principle behind the fix, enabling transfer to future situations ("SwiftUI's `.alert` modifier can bind to an optional error state. When you set the state, the alert presents automatically. This pattern works for any async operation that can fail.")

### Grade Criteria

**A+ (Exceptional)**
- >= 70% of NEGATIVE findings reach Level 5 (pedagogical)
- Zero findings at Level 1 or 2
- Guidance includes specific code patterns/snippets where appropriate
- Guidance teaches transferable principles, not just point fixes
- For hardcoded-secrets: guidance mentions BOTH immediate fix (move to .xcconfig/Keychain) AND git history implications (secret is already in history even after removal)
- Guidance never suggests solutions that introduce new problems
- Guidance is appropriately scoped (does not suggest rewriting the entire file for a MINOR issue)

**A (Excellent)**
- >= 50% at Level 5
- Zero at Level 1
- At most 10% at Level 2
- Guidance is specific to the student's code, not generic boilerplate
- Code examples in guidance are syntactically valid Swift

**B (Good)**
- >= 60% at Level 4+
- At most 1 at Level 1
- At most 20% at Level 2
- Most guidance is specific enough to act on within 15 minutes

**C (Adequate)**
- >= 50% at Level 3+ (directional)
- At most 2 at Level 1
- Guidance points in the right direction even if lacking specifics

**D (Poor)**
- Majority at Level 2 (vague)
- OR guidance frequently suggests incorrect fixes
- OR guidance is inconsistent with the detected issue

**F (Failing)**
- Majority at Level 1 or 2
- OR guidance suggests fixes that would break the code
- OR guidance suggests security anti-patterns (e.g., "store the token in UserDefaults instead")
- OR guidance field missing from majority of NEGATIVE findings
- OR guidance is copy-pasted identically across unrelated findings

---

## Dimension 6: Pedagogical Method (8%)

**What:** Is the `guidanceMethod` (from Collins & Brown's Cognitive Apprenticeship framework) appropriate for the finding type, the defect severity, and the assumed skill level?

**Method-to-context mapping (from practices.json and CA theory):**

| Method | When Appropriate | When Inappropriate |
|--------|-----------------|-------------------|
| **MODELING** | Student has never seen the correct pattern; show a complete worked example | Student clearly knows the pattern but made a mistake |
| **COACHING** | Student is practicing; needs hints, not full solutions; already has partial knowledge | First encounter with a concept (they need MODELING first) |
| **SCAFFOLDING** | Student needs structured support: templates, decision trees, partial solutions to complete | Trivial issues where a hint suffices |
| **ARTICULATION** | Student should explain their own reasoning; good for borderline/subjective issues | Clear-cut violations (nothing to articulate -- it is wrong) |
| **REFLECTION** | Student has history; compare current work to their previous work or expert practice | First-time detection; no history to reflect on |
| **EXPLORATION** | Student is advanced; push them to discover deeper patterns independently | Beginner struggling with basics |

**Practice-specific expected methods (from detection prompts):**
- `hardcoded-secrets`: SCAFFOLDING (provide .xcconfig/Keychain migration template)
- `silent-failure-patterns` Tier 1: SCAFFOLDING (provide do/catch template)
- `view-logic-separation`: SCAFFOLDING (show @Observable extraction pattern)
- `fatal-error-crash`: SCAFFOLDING (ModelContainer) or COACHING (Secrets loader)
- `error-state-handling`: SCAFFOLDING (LoadingState enum template)
- `preview-quality`: MODELING (show what good previews look like)
- `code-hygiene`: COACHING (default) or REFLECTION (for repeated patterns)
- `state-ownership-misuse`: MODELING (Pattern A) or SCAFFOLDING (Pattern B/C)

### Grade Criteria

**A+ (Exceptional)**
- Every finding's guidanceMethod matches the practice catalogue specification
- Method selection demonstrates understanding of CA progression (MODELING for novel concepts, SCAFFOLDING for known-but-not-mastered, COACHING for refinement)
- REFLECTION is never used without contributor history context (cannot reflect without history)
- EXPLORATION is never used for security/crash issues (too dangerous for self-discovery)
- ARTICULATION is used for subjective/borderline findings where student reasoning matters
- Method choice is consistent with the guidance content (if method is MODELING, the guidance actually shows a model)

**A (Excellent)**
- >= 80% of methods match expected specification
- No fundamentally inappropriate methods (e.g., EXPLORATION for hardcoded secrets)
- Method and guidance content are aligned (SCAFFOLDING guidance actually scaffolds)

**B (Good)**
- >= 60% of methods match expected specification
- No dangerous mismatches (wrong method on CRITICAL findings)
- General trend is appropriate (more supportive methods for harder issues)

**C (Adequate)**
- >= 40% match expected specification
- SCAFFOLDING or COACHING used as reasonable defaults even if not optimal
- No systematically wrong pattern

**D (Poor)**
- < 40% match
- OR EXPLORATION used for security issues
- OR REFLECTION used with no contributor history
- OR method is always the same regardless of finding type (no differentiation)

**F (Failing)**
- guidanceMethod missing from majority of findings
- OR method is systematically inverted (MODELING for advanced, EXPLORATION for beginners)
- OR method field contains invalid values (not in CaMethod enum)
- OR all findings use the same method with no variation

---

## Dimension 7: Inline Comments Quality (8%)

**What:** Are `diffNotes` targeted to specific code lines, actionable, well-placed in the diff, and complementary to (not duplicative of) the mrNote summary?

**Schema:** `diffNotes: [{ filePath, startLine, endLine?, body }]`
**Constraints:** Max 10 diffNotes per job, body max 2,000 chars each

### Grade Criteria

**A+ (Exceptional)**
- diffNotes present for >= 80% of line-specific NEGATIVE findings
- Every diffNote targets the correct file and line (within +/- 1 line of the defect)
- diffNote body is concise (< 500 chars), actionable, and non-redundant with mrNote
- diffNotes for CRITICAL findings (hardcoded secrets) are present and mark the exact line
- No diffNotes on lines not in the diff (only comments on changed lines)
- diffNotes are ordered by severity (most critical first if near the limit)
- Each diffNote adds value beyond what mrNote says (e.g., specific fix suggestion for THIS line)
- Zero orphan diffNotes (every note ties to a finding in the findings array)

**A (Excellent)**
- diffNotes present for >= 60% of line-specific findings
- File paths and line numbers correct
- Bodies are actionable and concise
- CRITICAL findings have diffNotes
- At most 1 misplaced note

**B (Good)**
- diffNotes present for >= 40% of line-specific findings
- Mostly correct placements
- Bodies are at least directional
- At most 2 misplaced notes

**C (Adequate)**
- diffNotes present (at least 3)
- Some placement issues (2-3 wrong lines)
- Bodies are somewhat useful even if generic
- No diffNotes on completely wrong files

**D (Poor)**
- Fewer than 3 diffNotes for a 10-defect MR
- OR multiple diffNotes on wrong lines
- OR bodies are just the finding title repeated
- OR diffNotes are entirely duplicative of mrNote text

**F (Failing)**
- Zero diffNotes (delivery.diffNotes is empty or missing)
- OR diffNotes reference files not in the MR
- OR diffNote line numbers are fabricated (pointing at code that does not match)
- OR all diffNote bodies are identical copy-paste

---

## Dimension 8: Output Integrity (5%)

**What:** Does the agent output conform to the expected JSON schema, avoid duplicates, include all required fields, and stay within size/count limits?

**Schema requirements (from PracticeDetectionResultParser):**
- Top-level: `{ findings: [...], delivery: { mrNote, diffNotes: [...] } }`
- Each finding requires: `practiceSlug` (string), `title` (string, <=255), `verdict` (POSITIVE|NEGATIVE), `severity` (CRITICAL|MAJOR|MINOR|INFO), `confidence` (float 0-1)
- Optional but expected: `evidence`, `reasoning` (<=10,000), `guidance` (<=5,000), `guidanceMethod` (valid CaMethod)
- Evidence: <=64KB serialized
- Deduplication: one finding per practiceSlug (parser keeps highest confidence)
- Delivery: mrNote <=60,000 chars, diffNotes max 10, each body <=2,000 chars

### Grade Criteria

**A+ (Exceptional)**
- Valid JSON, parseable on first attempt (no phase markers or wrapper text)
- All required fields present on every finding
- All optional fields present on every finding (reasoning, guidance, guidanceMethod, evidence)
- Zero duplicate practiceSlug entries (no deduplication needed)
- Confidence values within [0.0, 1.0] as decimals (not percentages)
- All string fields within length limits
- Verdict and severity use exact enum values (no casing issues beyond what parser normalizes)
- delivery object present with both mrNote and diffNotes
- No extraneous fields that could confuse consumers

**A (Excellent)**
- Valid JSON
- All required fields present
- At most 1 missing optional field across all findings
- At most 1 duplicate slug (parser deduplicates gracefully)
- Confidence values correct format

**B (Good)**
- Valid JSON (possibly after phase-marker extraction)
- All required fields present
- At most 2-3 missing optional fields
- At most 2 duplicates
- Minor format issues (confidence as percentage, casing)

**C (Adequate)**
- Parseable JSON (possibly with extraction needed)
- Required fields mostly present (at most 1 finding with missing required field, which gets discarded)
- Several missing optional fields
- Duplicates present but manageable

**D (Poor)**
- JSON requires significant cleanup to parse
- OR multiple findings discarded due to missing required fields
- OR > 3 duplicate slugs
- OR confidence values out of range

**F (Failing)**
- Output is not valid JSON
- OR output is empty / null
- OR findings array is missing
- OR majority of findings fail validation (discarded > valid)
- OR output exceeds size limits causing truncation of critical content

---

## Dimension 9: Efficiency (5%)

**What:** Time to completion, token usage, API cost, and quality-per-dollar relative to the other agent.

**This is a comparative dimension** -- agents are graded relative to each other and to acceptable operational thresholds for the Hephaestus system reviewing 1,071 MRs.

**Operational thresholds:**
- Time budget per MR: < 120 seconds is excellent, < 300 seconds is acceptable, > 600 seconds is problematic
- Cost per MR: < $0.10 is excellent, < $0.50 is acceptable, > $1.00 is problematic at scale
- Quality-adjusted cost: (overall grade) / (cost per MR) -- higher is better

### Grade Criteria

**A+ (Exceptional)**
- Completes in < 60 seconds
- Cost < $0.05 per MR
- Quality-adjusted efficiency is highest among all tested agents
- No wasted tokens on irrelevant analysis (does not analyze files not in the diff)
- No retry loops or repeated analysis passes

**A (Excellent)**
- Completes in < 120 seconds
- Cost < $0.10 per MR
- No significant wasted computation

**B (Good)**
- Completes in < 180 seconds
- Cost < $0.25 per MR
- Some minor inefficiency (analyzed irrelevant files, verbose output)

**C (Adequate)**
- Completes in < 300 seconds
- Cost < $0.50 per MR
- Notable inefficiency but still within operational budget

**D (Poor)**
- Completes in 300-600 seconds
- OR cost $0.50-$1.00 per MR
- OR required retries due to malformed output

**F (Failing)**
- Exceeds 600 seconds (timeout risk)
- OR cost > $1.00 per MR
- OR failed to complete (timeout, crash, empty output)
- OR required 3+ retries

---

## Dimension 10: MR Summary Quality (5%)

**What:** Is the `delivery.mrNote` markdown summary well-structured, correctly prioritized, constructive in tone, and useful as the student's entry point into the feedback?

**The mrNote is the first thing the student reads.** If it is overwhelming, punitive, or disorganized, the student may disengage from all feedback.

### Grade Criteria

**A+ (Exceptional)**
- Opens with 1-2 sentence overall assessment (not a wall of findings)
- Findings are organized by severity (CRITICAL first, then MAJOR, MINOR, INFO)
- CRITICAL findings (hardcoded secrets) are visually prominent (bold, warning emoji, or header)
- POSITIVE findings are included to balance negative ones (sandwich feedback)
- Each finding in the summary links to or references the inline comment where applicable
- Total length is appropriate: 500-2,000 words (not a 50-word dismissal, not a 5,000-word essay)
- Tone is constructive and educational throughout (no condescension, no "you should know better")
- Closing section offers encouragement or a prioritized action plan ("Fix the secrets first, then...")
- Markdown renders correctly (no broken formatting, proper headers, code blocks)
- Summary does not repeat every detail from inline comments verbatim

**A (Excellent)**
- Well-structured with clear severity ordering
- CRITICAL items prominent
- Constructive tone
- Appropriate length
- Markdown renders correctly
- At most 1 structural issue

**B (Good)**
- Organized by some logical structure (not necessarily severity)
- CRITICAL items mentioned prominently
- Generally constructive tone with minor lapses
- Reasonable length
- Markdown mostly correct

**C (Adequate)**
- All major findings mentioned
- Some organizational structure
- Tone is neutral (not actively discouraging)
- May be too long or too short
- Minor markdown issues

**D (Poor)**
- Missing CRITICAL findings from summary
- OR no organizational structure (random list)
- OR tone is punitive ("terrible code", "you obviously did not...")
- OR excessively long (> 4,000 words) or short (< 200 words)

**F (Failing)**
- mrNote is missing entirely
- OR mrNote contains only the word "LGTM" or equivalent dismissal
- OR mrNote is a raw JSON dump of findings (not human-readable)
- OR mrNote tone is hostile or demoralizing
- OR mrNote contains factually incorrect statements about the code

---

## Composite Scoring

### Weighted Grade Calculation

Each letter grade maps to a numeric value:

| Grade | Points |
|-------|--------|
| A+ | 4.3 |
| A | 4.0 |
| B | 3.0 |
| C | 2.0 |
| D | 1.0 |
| F | 0.0 |

**Composite Score** = Sum of (dimension_weight * dimension_points)

**Maximum possible:** 4.3 (straight A+ across all dimensions)

### Overall Grade Thresholds

| Composite Score | Overall Grade | Interpretation |
|----------------|---------------|----------------|
| >= 4.0 | **A+** | Publication-quality agent; suitable for unsupervised deployment |
| 3.5 - 3.99 | **A** | Excellent agent; minor improvements possible |
| 3.0 - 3.49 | **B** | Good agent; reliable for supervised deployment |
| 2.5 - 2.99 | **C+** | Adequate; usable with human review of output |
| 2.0 - 2.49 | **C** | Borderline; significant improvement needed |
| 1.5 - 1.99 | **D** | Poor; not recommended for student-facing use |
| < 1.5 | **F** | Failing; fundamental issues must be addressed |

### Automatic Grade Caps (Hard Ceilings)

These override the composite calculation:

1. **Detection Accuracy = F** caps overall at **F** (nothing else matters if you miss everything)
2. **Detection Accuracy = D** caps overall at **C** (cannot be "good" while missing most defects)
3. **Evidence contains fabricated snippets** caps overall at **D** (trust destruction)
4. **Guidance suggests security anti-patterns** caps overall at **D** (actively harmful)
5. **Output Integrity = F** caps overall at **F** (pipeline cannot process the output)
6. **Hard-stop secrets both missed** caps overall at **F** (non-negotiable)

---

## Evaluation Protocol

### Step 1: Prepare Ground Truth
- Annotate every defect in SettingsView.swift with expected practice, severity, and line range
- Create a mapping table of defect_id -> { practice_slug, severity, startLine, endLine }

### Step 2: Run Both Agents
- Run each agent against the same MR with identical configurations
- Record: wall clock time, token counts (input + output), estimated cost
- Capture full raw output before any parsing

### Step 3: Parse and Validate
- Run output through PracticeDetectionResultParser
- Record discarded entries and deduplication events
- These feed into Dimension 8 (Output Integrity)

### Step 4: Score Each Dimension
- For each dimension, independently assign a letter grade using the criteria above
- Document specific evidence for each grade decision (quote findings, cite line numbers)
- Two evaluators should grade independently, then reconcile disagreements

### Step 5: Check Automatic Caps
- Apply hard ceiling rules before computing composite

### Step 6: Compute Composite
- Convert letter grades to points
- Apply weights
- Determine overall grade

### Step 7: Comparative Analysis
- Side-by-side the two agents on each dimension
- Identify dimensions where one agent clearly dominates
- Note qualitative differences (e.g., "Agent A's reasoning is more contextual but Agent B is faster")

---

## Appendix A: Inter-Rater Reliability Protocol

Because several dimensions involve subjective judgment (Reasoning Depth, Guidance Actionability, MR Summary Quality), use the following protocol:

1. Two evaluators grade independently using this rubric
2. Compute Cohen's kappa for each subjective dimension
3. Kappa >= 0.80: Accept the grades
4. Kappa 0.60-0.79: Discuss disagreements, re-grade disputed items
5. Kappa < 0.60: Rubric criteria need refinement for that dimension -- the criteria are too ambiguous

## Appendix B: Cognitive Apprenticeship Method Reference

From Collins, Brown & Newman (1989):

| Method | Definition | Appropriate When | Observable in Guidance |
|--------|-----------|-----------------|----------------------|
| MODELING | Expert demonstrates the complete process, making thinking visible | Learner encountering concept for first time | Guidance contains a complete worked example or corrected code |
| COACHING | Observer watches learner perform, offers hints, feedback, reminders | Learner is practicing; has seen the concept before | Guidance asks questions, gives hints, points at specific issues |
| SCAFFOLDING | Teacher provides structural support, executes parts learner cannot yet manage | Learner knows the goal but needs templates or frameworks | Guidance provides partial solution, template, or decision tree |
| ARTICULATION | Prompts learner to explain their own knowledge and reasoning | Issue is subjective or learner has partial understanding | Guidance asks "why did you..." or "what would happen if..." |
| REFLECTION | Enables learner to compare their work to expert performance or their own past work | Learner has history; can compare before/after | Guidance references previous work or expert comparison |
| EXPLORATION | Gives learner room to solve independently; withdraws support | Learner is advanced, building transfer skills | Guidance poses an open-ended challenge without providing the answer |

## Appendix C: False Positive Classification

Not all "extra" findings are equal. Classify each non-ground-truth NEGATIVE finding:

| Classification | Definition | Impact on Precision |
|---------------|-----------|-------------------|
| **True False Positive** | Finding describes a problem that does not exist in the code | Counts against precision |
| **Hallucinated Defect** | Finding describes code or behavior not present in the diff | Counts against precision; also hits Evidence Quality |
| **Severity-Only Error** | Real issue, but severity is wrong | Does NOT count against precision; hits Dimension 2 |
| **Slug Misattribution** | Real issue, correct severity, wrong practice slug | Does NOT count against precision; hits Dimension 1 slug accuracy |
| **Legitimate Extra** | Real issue not in ground truth (evaluator missed it) | Does NOT count against precision; update ground truth |
| **Nit/Style Finding** | Real but extremely minor observation | Counts against precision only if verdict is NEGATIVE with severity >= MINOR |

---

## Sources

- [CR-Bench: Evaluating the Real-World Utility of AI Code Review Agents](https://arxiv.org/abs/2603.11078) -- Benchmark methodology for usefulness rate and signal-to-noise ratio
- [AI Code Review Benchmark 2026: Precision, Recall, and F1 Results](https://www.codeant.ai/blogs/ai-code-review-benchmark-results-from-200-000-real-pull-requests) -- Industry benchmarks from 200K PRs
- [Enhancing Code Quality at Scale with AI-Powered Code Reviews (Microsoft)](https://devblogs.microsoft.com/engineering-at-microsoft/enhancing-code-quality-at-scale-with-ai-powered-code-reviews/) -- Microsoft's 600K PRs/month system
- [AI-assisted Assessment of Coding Practices in Industrial Code Review (Google)](https://research.google/pubs/ai-assisted-assessment-of-coding-practices-in-industrial-code-review/) -- Google's approach to practice-based assessment
- [How Many False Positives Are Too Many in AI Code Review](https://www.codeant.ai/blogs/ai-code-review-false-positives) -- Industry FPR thresholds (<10% baseline, <5% target)
- [Cognitive Apprenticeship (Collins, Brown & Holum, 1991)](https://www.aft.org/ae/winter1991/collins_brown_holum) -- Original CA framework paper
- [Cognitive Apprenticeship - ISLS](https://www.isls.org/research-topics/cognitive-apprenticeship/) -- Research topic overview
- [Automated Grading and Feedback Tools for Programming Education: A Systematic Review](https://dl.acm.org/doi/10.1145/3636515) -- 121-paper systematic review of feedback quality
- [A Systematic Literature Review of Automated Feedback Generation for Programming Exercises](https://dl.acm.org/doi/10.1145/3231711) -- Feedback actionability and learning outcomes
- [Enhancing Asynchronous Code Review: Visual-Contextual Feedback Systems](https://www.researchgate.net/publication/402555928_Enhancing_Asynchronous_Code_Review_in_Programming_Education_A_Systematic_Literature_Review_of_Visual-Contextual_Feedback_Systems) -- Visual-contextual feedback reduces cognitive load
- [Code Review Agent Benchmark](https://arxiv.org/html/2603.23448) -- Independent evaluation framework for code review agents
- [Expected false-positive rate from AI code review tools](https://graphite.com/guides/ai-code-review-false-positives) -- FPR scaling guidance
