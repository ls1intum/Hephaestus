#### 🔴 Allow sandbox traffic on the new gateway port

Worker sandboxes now connect to `SANDBOX_API_PORT` (default `8081`) instead of the worker application port. Allow sandbox-to-worker traffic on this port and keep the worker application and management ports private. Set `SANDBOX_API_PORT` consistently in the worker and any network policy that restricts sandbox egress. A single-container install runs the worker role in the `application-server` container, so it opens this port too.

`SANDBOX_LLM_PROXY_PORT` is no longer read — set `SANDBOX_API_PORT` instead.
