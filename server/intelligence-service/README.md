# Intelligence Service

This is a FastAPI service for interfacing with LangChain and other machine learning services.

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
- [Project Structure](#project-structure)
- [Testing](#testing)

## Installation

To set up the project locally, follow these steps:

1. **Install dependencies:**
    The project uses `poetry` for dependency management. Install the dependencies by running:

    ```bash
    poetry install
    ```

2. **Run the application:**
    You can start the FastAPI application with Uvicorn:

    ```bash
    poetry run uvicorn src.main:app --reload
    ```

## Usage

After running the application, you can access the FastAPI API documentation at `http://127.0.0.1:8000/docs` or `http://127.0.0.1:8000/redoc`.

## Project Structure

The project is organized as follows:

```
intelligence-service/
├── pyproject.toml     
├── README.md            
├── poetry.lock           
├── .pytest_cache/        
├── tests/                
│   ├── __init__.py
│   └── test_hello.py
├── src/               
│   ├── __init__.py   
│   ├── config.py   
│   ├── langchain_client.py
│   ├── main.py          
│   └── auth/            
│       └── router.py    
└── ...
```

## Testing

The project includes a set of unit tests to ensure that the core functionalities work as expected. These tests are located in the `tests/` directory.

### Running Tests

To run all tests, use the following command:

```bash
poetry run pytest
