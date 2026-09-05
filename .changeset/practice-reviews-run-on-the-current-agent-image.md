---
"hephaestus": patch
---

Practice reviews run again. On the current agent image every review died inside the sandbox before
its first model call, because the agent SDK now looks for skills and context files in directories
the sandbox does not let it read; the runner no longer asks it to look there, and the image build
proves that path under the same permissions so the next SDK update cannot bring it back.
