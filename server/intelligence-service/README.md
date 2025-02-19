# Intelligence Service

## Overview

A FastAPI service for interfacing with machine learning models.

## Setup

### Prerequisites

- **Python 3.12**
- **Poetry** for dependency management
- **Docker** for containerization

### Installation

Install dependencies using Poetry:

```bash
pip install poetry
poetry install
```
If you have poetry < 2.0.0 installed, please run
```bash
poetry self update
```

## Running the Service

### Development

```bash
fastapi dev
```

### Production

```bash
fastapi run
```

## Usage

After running the application, you can access the FastAPI API documentation at `http://127.0.0.1:8000/docs` or `http://127.0.0.1:8000/redoc`.

