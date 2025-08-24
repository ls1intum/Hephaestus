from app.settings import settings

settings.IS_GENERATING_OPENAPI = True

import yaml
import inspect
from fastapi.openapi.utils import get_openapi
from app import main


def auto_discover_tool_schemas():
    """Automatically discover all tool input/output models using base class inspection."""
    from app.mentor.models import ToolInputBase, ToolOutputBase

    # Import the models module to ensure all classes are loaded
    import app.mentor.models as models_module

    tool_models = []

    # Get all classes from the models module
    for name, obj in inspect.getmembers(models_module, inspect.isclass):
        # Check if the class is a subclass of our base classes (but not the base class itself)
        if (issubclass(obj, ToolInputBase) and obj is not ToolInputBase) or (
            issubclass(obj, ToolOutputBase) and obj is not ToolOutputBase
        ):
            tool_models.append(obj)

    return tool_models


def auto_discover_data_schemas():
    """Automatically discover all data payload models using base class inspection."""
    from app.mentor.models import DataBase

    # Ensure models are imported so classes are loaded
    import app.mentor.models as models_module

    data_models = []
    for name, obj in inspect.getmembers(models_module, inspect.isclass):
        if issubclass(obj, DataBase) and obj is not DataBase:
            data_models.append(obj)

    return data_models


def get_openapi_specs():
    """Generate OpenAPI spec with auto-discovered tool schemas."""
    # Generate the base OpenAPI schema
    openapi_schema = get_openapi(
        title=main.app.title,
        version=main.app.version,
        description=main.app.description,
        contact=main.app.contact,
        routes=main.app.routes,
    )

    # Auto-discover tool and data models
    tool_models = auto_discover_tool_schemas()
    data_models = auto_discover_data_schemas()

    if not tool_models and not data_models:
        print("⚠️  Warning: No tool or data models discovered")
        return yaml.dump(openapi_schema, allow_unicode=True)

    if tool_models:
        print(f"🔍 Auto-discovered {len(tool_models)} tool models:")
        for model in tool_models:
            print(f"  - {model.__name__}")
    if data_models:
        print(f"🔍 Auto-discovered {len(data_models)} data models:")
        for model in data_models:
            print(f"  - {model.__name__}")

    # Ensure components.schemas exists
    if "components" not in openapi_schema:
        openapi_schema["components"] = {}
    if "schemas" not in openapi_schema["components"]:
        openapi_schema["components"]["schemas"] = {}

    def fix_schema_refs(schema, def_map):
        """Recursively fix $defs references to point to components/schemas."""
        if isinstance(schema, dict):
            for key, value in schema.items():
                if (
                    key == "$ref"
                    and isinstance(value, str)
                    and value.startswith("#/$defs/")
                ):
                    # Change #/$defs/WeatherCurrent to #/components/schemas/WeatherCurrent
                    def_name = value.replace("#/$defs/", "")
                    schema[key] = f"#/components/schemas/{def_name}"
                elif isinstance(value, (dict, list)):
                    fix_schema_refs(value, def_map)
        elif isinstance(schema, list):
            for item in schema:
                fix_schema_refs(item, def_map)

    # Helper to add model schemas and resolve $defs
    def add_model_schema(model):
        model_schema = model.model_json_schema()
        schema_name = model.__name__

        # Tag with custom extension for easy identification
        model_schema.setdefault("x-hephaestus", {})
        if (
            "Tool" in schema_name
            or schema_name.endswith("Input")
            or schema_name.endswith("Output")
        ):
            model_schema["x-hephaestus"]["toolModel"] = True
        else:
            model_schema["x-hephaestus"]["dataModel"] = True

        # Handle nested schemas from $defs
        if "$defs" in model_schema:
            for def_name, def_schema in model_schema["$defs"].items():
                openapi_schema["components"]["schemas"][def_name] = def_schema

            # Fix all references in the main schema
            fix_schema_refs(model_schema, model_schema["$defs"])

            # Remove $defs from the main schema as we've moved them to components
            del model_schema["$defs"]
        openapi_schema["components"]["schemas"][schema_name] = model_schema

    # Add each tool model schema and resolve $defs
    for model in tool_models:
        add_model_schema(model)

    # Add each data model schema and resolve $defs
    for model in data_models:
        add_model_schema(model)

    print(
        f"✅ Added {len(tool_models)} tool schemas and {len(data_models)} data schemas to OpenAPI spec"
    )

    openapi_yaml = yaml.dump(openapi_schema, allow_unicode=True)
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
