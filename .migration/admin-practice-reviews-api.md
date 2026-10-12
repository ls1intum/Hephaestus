#### 🔴 Use the dedicated APIs instead of raw job output

Administrative job responses no longer return raw `output`. The nullable field remains in the schema, but its contents are withheld to keep private feedback outside administrative monitoring. Internal review results and delivery recovery are preserved.

**Operators:** Update custom clients that read job output to use the workspace's practice-review observation and feedback endpoints for the records they are permitted to read. Job responses still carry typed review outcomes, replacement references, phase timestamps and usage totals. The per-job usage endpoint carries recorded attempt usage and pricing state.
