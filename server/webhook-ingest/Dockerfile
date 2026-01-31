# syntax=docker/dockerfile:1.4

FROM python:3.13-alpine

# For healthcheck
RUN apk add --no-cache curl

RUN pip install poetry==2.1.1

# Configure Poetry to not create virtualenv (use system Python in container)
ENV POETRY_VIRTUALENVS_CREATE=false

WORKDIR /app
COPY pyproject.toml poetry.lock ./

# Use BuildKit cache mounts for pip and Poetry caches to speed up rebuilds
RUN --mount=type=cache,target=/root/.cache/pip \
    --mount=type=cache,target=/root/.cache/pypoetry \
    poetry install --no-root --no-interaction

COPY app/ ./app

CMD ["fastapi", "run", "app/main.py", "--port", "4200"]
