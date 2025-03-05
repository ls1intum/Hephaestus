FROM python:3.13-alpine

RUN apk update && \
    apk add --no-cache gcc g++ musl-dev postgresql-dev rust cargo curl

RUN pip install poetry==2.1.1

WORKDIR /app
COPY pyproject.toml poetry.lock ./
RUN poetry install --no-root

COPY app/ ./app

CMD ["poetry", "run",  "fastapi", "run", "app/main.py", "--port", "5000"]
