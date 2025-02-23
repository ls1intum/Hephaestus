FROM python:3.13-alpine

RUN pip install poetry==2.1.1

WORKDIR /app
COPY pyproject.toml poetry.lock ./
RUN poetry install --no-root

COPY app/ ./app

CMD ["poetry", "run",  "fastapi", "run", "app/main.py", "--port", "4200"]
