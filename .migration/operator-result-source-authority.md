#### 🔴 Handle unavailable operator result text

Result summaries in the operator review API are null when source access cannot be verified. This applies to result lists and details, results linked to feedback, and lineage records. The detail also withholds the explanation and evidence in that state.

**Operators:** Update external API consumers to handle a null summary. The bundled interface displays the unavailable state and retains the result’s outcome, practice, work and delivery facts. No manual database change is required.
