FROM python:3.12 AS requirements-stage

WORKDIR /tmp

RUN pip install poetry==1.8.5
COPY ./pyproject.toml ./poetry.lock* /tmp/
RUN poetry export -f requirements.txt --output requirements.txt --without-hashes

FROM python:3.12

WORKDIR /code

COPY --from=requirements-stage /tmp/requirements.txt /code/requirements.txt
RUN pip install --no-cache-dir --upgrade -r /code/requirements.txt
COPY ./app /code/app

CMD ["fastapi", "run", "app/main.py", "--port", "5000"]
