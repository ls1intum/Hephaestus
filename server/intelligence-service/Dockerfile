FROM python:3.13-alpine

ARG GIT_SHA=unknown
ARG IMAGE_TAG=unknown
ARG BUILD_DATE=unknown

ENV GIT_SHA=${GIT_SHA}
ENV IMAGE_TAG=${IMAGE_TAG}
ENV BUILD_DATE=${BUILD_DATE}

LABEL org.opencontainers.image.revision="${GIT_SHA}"
LABEL org.opencontainers.image.version="${IMAGE_TAG}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"

RUN apk update && \
    apk add --no-cache gcc g++ musl-dev postgresql-dev rust cargo curl

RUN pip install poetry==2.1.1

WORKDIR /app
COPY pyproject.toml poetry.lock ./
RUN poetry install --no-root

COPY app/ ./app

CMD ["poetry", "run",  "fastapi", "run", "app/main.py", "--port", "5000"]
