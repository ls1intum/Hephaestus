#### 🔴 Update custom pinned agent images

The application requires agent runtime contract 7 for incremental Problem drafts. Runtime 6 images do not provide the required toolkit export.

**Operators:** If you pin a custom agent image, rebuild it from the matching source and update its immutable reference with the application. Verify that its `hephaestus.agent.runtime-contract` label is `7`. Verified release locks already select a matching application and agent pair. The image contract check reports mismatches; it does not stop execution. Practice inputs and candidates remain on script contract 3.
