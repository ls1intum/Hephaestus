#### 🔴 Handle unavailable operator observation text

Observation summaries in the operator review API are optional when source access cannot be verified. This applies to observation lists and details, observations linked to feedback, and lineage records. The detail also withholds the rationale and evidence in that state.

**Operators:** Update external API consumers to handle an absent summary. The bundled interface displays the unavailable state and retains the observation’s outcome, practice, work and delivery facts. No manual database change is required.
