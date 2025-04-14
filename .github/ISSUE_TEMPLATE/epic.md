---
name: Thesis Epic
about: This epic captures a core thesis objective, guiding you from identifying the problem through requirements into an architecture.
title: "[Imperative Verb Phrase]: [Short Description]"
labels: epic
assignees: 
---

<!--
This template is designed to help you structure your thesis work from problem identification to solution.

You will:
- Formulate and continuously update the Problem Statement.
- Extract and refine INVEST-compliant user stories.
- Build a clear terminology table (glossary) for consistent domain modeling.
- Create an Analysis Object Model (UML Class Diagram) to illustrate key domain entities
  (and, optionally, a Dynamic Model for causality).
- Transform user stories into backlog items with detailed Given-When-Then acceptance criteria.
- Produce UI mockups (start low-fidelity; later upgrade to high-fidelity).
- Specify Quality Attributes and External Constraints (non-functional requirements).
- Transition to the solution domain with Architecture artifacts:
   - Design Goals (what qualities to optimize)
   - Subsystem Decomposition (key components and their interfaces)
   - Hardware–Software Mapping (deployment model)
   - Data Management (persistent data strategy)
   - Access Control (user roles and security policies)
   - Boundary Conditions (system initialization, termination, failure handling)

Follow the instructions but keep your entries as concise as possible.
You can add/adjust sections as needed, but ensure the document remains clear and focused.
-->

## 1. Problem Statement

<!-- Define and maintain a clear, evolving problem statement. Explain the underlying challenge and its impact. -->

[Enter a precise description of the problem, why it matters, and the user pain points.]

---

## 2. Requirements

### 2.1 User Stories
<!-- List actionable, testable user stories in the format:
     "As a [role], I want [goal] so that [benefit]."

Ensure each story is INVEST compliant:
  - Independent: Can be developed and tested in isolation.
  - Negotiable: Open to discussion and refinement.
  - Valuable: Provides clear value to the user.
  - Estimable: Can be estimated for effort.
  - Small: Can be completed within a single iteration.
  - Testable: Has clear acceptance criteria.
-->

- **Story 1:** As a [role], I want [goal] so that [benefit].
- **Story 2:** As a [role], I want [goal] so that [benefit].
- **Story 3:** As a [role], I want [goal] so that [benefit].

<!-- For backlog transformation use the format below

1. Write down the user stories as task list in the format "- [ ] <Imperative Verb Phrase>: <Short Goal Description>", example:

- [ ] Monitor supplies: I want to monitor and refill water and food supplies in each backyard
- [ ] Analyze data: I want to analyze user feedback to improve product features

2. Press the three dots in the GitHub UI and select "Convert to sub-issue" to create a new issue for each item.
3. Add the full user story to the sub-issue description.
4. Add the acceptance criteria in the sub-issue description using the format "Given-When-Then" (**Given** [precondition], **when** [action occurs], **then** [expected outcome].)
5. Ensure that each sub-issue is linked back to the epic for traceability.
 -->

### 2.2 Terminology / Glossary
<!-- Provide a table of domain-specific terms to ensure consistent modeling. -->

| Term       | Definition                                  |
|------------|---------------------------------------------|
| Term1      | [Definition of Term1]                       |
| Term2      | [Definition of Term2]                       |
| Term3      | [Definition of Term3]                       |

### 2.3 Analysis Object Model (AOM)
<!-- Develop a UML Class Diagram that captures the core domain:
     - Use nouns from your glossary as classes.
     - Extract attributes/methods from your problem description.
     - Define associations and multiplicities. -->

**UML Class Diagram:**  
[Attach or link your diagram here]  

*Key Elements:*  

- Core Classes: [List major classes]
- Relationships: [Summary of associations]

### 2.4 Dynamic Model (Optional)
<!-- If causality or process flows improve understanding, include a UML Activity Diagram. -->

**Dynamic Model:**  
[Attach or link the diagram]  

*Explanation:* [Briefly state what processes or flows are modeled.]

### 2.5 UI Mockups & Prototyping
<!-- Begin with low-fidelity sketches for early ideas, then refine with high-fidelity prototypes if needed. -->

**Low-Fidelity Mockups:**  
[Attach or link your initial sketches/wireframes]

**High-Fidelity Prototypes (Optional):**  
[Attach or link your detailed prototypes]

### 2.6 Quality Attributes & External Constraints
<!-- List measurable quality attributes (usability, performance, etc.) and external constraints (platform, regulatory). -->

- **Quality Attributes:**
  - *Usability:* [E.g., "Intuitive interface; primary actions within 3 taps."]
  - *Performance:* [E.g., "Response time under 2 seconds."]
  - *Reliability:* [E.g., "Graceful error handling and offline support."]
  - *Security:* [E.g., "Compliant with GDPR."]
- **External Constraints:**  
  [List technical, legal, or other limitations.]

---

## 3. Architecture

### 3.1 Design Goals
<!-- Define the desired system qualities and optimization targets. -->

- **Goal 1:** [E.g., "High scalability."]
- **Goal 2:** [E.g., "Maintainable modularity."]
- **Goal 3:** [E.g., "Robust security."]

### 3.2 Subsystem Decomposition
<!-- Outline logical subsystems/components including their services and interfaces. -->

**Subsystems Overview:**  

- **Subsystem A:** [Core functionality and responsibilities]
- **Subsystem B:** [Supporting services]
- **Subsystem C:** [Auxiliary functions]  

[Attach a subsystem/component diagram if available.]

### 3.3 Hardware–Software Mapping
<!-- Map subsystems to physical or virtual nodes for deployment. -->

**Deployment Model:**  

- **Node 1:** [E.g., "Backend services on cloud/in-house server"]
- **Node 2:** [E.g., "Mobile client (iOS/Android)"]
- [Add further nodes as required.]

### 3.4 Data Management
<!-- Specify which data is persistent, how it is stored, and how data integrity is ensured. -->

**Persistent Data:**  
[List data entities and storage methods, e.g., SQL/NoSQL databases]

**Strategy Rationale:**  
[Explain selection based on performance, scalability, and integrity.]

### 3.5 Access Control
<!-- Define user roles, authentication, and permissions. -->

**User Roles:**  
[Define roles such as Admin, User, Guest with corresponding permissions]

**Mechanisms:**  
[E.g., "JWT-based authentication, OAuth2, role-based access control"]

### 3.6 Boundary Conditions
<!-- Describe how the system initializes, shuts down, and recovers from failures. -->

- **Initialization:** [Startup processes and checks]
- **Termination:** [Graceful shutdown process]
- **Failure Handling:** [Fallback procedures and error recovery mechanisms]

---

## 4. Progress Log & Additional Notes
<!-- Maintain a log of updates, decisions, and ongoing refinements. -->

**Progress Log:**  

- [Date] – [Key update or decision]
- [Date] – [Key update or decision]

**Additional Notes:**  
[Record reflections, insights, or links to related documentation.]
