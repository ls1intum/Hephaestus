from . import main
from fastapi.openapi.utils import get_openapi
import yaml


def get_openapi_specs():
    openapi_json = get_openapi(
        title=main.app.title,
        version=main.app.version,
        description=main.app.description,
        contact=main.app.contact,
        routes=main.app.routes,
    )
    openapi_yaml = yaml.dump(openapi_json)
    return openapi_yaml


def convert():
    try:
        yaml_spec = get_openapi_specs()
        with open("server/intelligence-service/openapi.yaml", "w") as f:
            f.write(yaml_spec)
        print("OpenAPI YAML specification generated successfully.")
    except Exception as e:
        print(f"Error generating OpenAPI specs: {e}")
        exit(1)


if __name__ == "__main__":
    convert()
