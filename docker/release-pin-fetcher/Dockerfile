# Docker Hub mirror — cgr.dev removed free `:latest` access from its public
# catalog in May 2026 (anonymous pulls return 404 / 500). The Docker Hub mirror
# `chainguard/wolfi-base` is still publicly accessible. Same image content.
FROM chainguard/wolfi-base:latest

RUN apk add --no-cache cosign curl

ENTRYPOINT ["/bin/sh"]
