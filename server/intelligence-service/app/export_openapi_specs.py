from app.settings import settings

settings.IS_GENERATING_OPENAPI = True

import yaml
from fastapi.openapi.utils import get_openapi
from app import main


def get_openapi_specs():
    openapi_json = get_openapi(
        title=main.app.title,
        version=main.app.version,
        description=main.app.description,
        contact=main.app.contact,
        routes=main.app.routes,
    )
    openapi_yaml = yaml.dump(openapi_json, allow_unicode=True)
    return openapi_yaml


def run():
    try:
        yaml_spec = get_openapi_specs()
        with open("./openapi.yaml", "w") as f:
            f.write(yaml_spec)
        print("OpenAPI YAML specification generated successfully.")
    except Exception as e:
        print(f"Error generating OpenAPI specs: {e}")
        exit(1)
