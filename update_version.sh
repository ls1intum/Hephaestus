#!/bin/bash
set -euo pipefail

# Script to update the version across multiple files to a single, consistent new version.
# Usage: ./update_version.sh [major|minor|patch] OR ./update_version.sh <version>
# 
# Version can be a semantic version with optional pre-release/build metadata:
#   Examples: 1.2.3, 1.0.0-alpha.1, 2.0.0-beta.1, 1.2.3+build.123
#
# This script updates the version in:
#   - webapp/package.json & webapp/package-lock.json (for "hephaestus")
#   - root package-lock.json (top-level version)
#   - Java source: server/application-server/src/main/java/de/tum/in/www1/hephaestus/OpenAPIConfiguration.java
#   - YAML config: server/application-server/src/main/resources/application.yml
#   - Python projects: server/intelligence-service/pyproject.toml & server/webhook-ingest/pyproject.toml
#   - Python app: server/intelligence-service/app/main.py
#   - Maven POM: server/application-server/pom.xml (preserving -SNAPSHOT)
#   - OpenAPI docs: server/application-server/openapi.yaml & server/intelligence-service/openapi.yaml
#   - All files containing "The version of the OpenAPI document:" (only in these directories):
#         server/application-server/src/main/java/de/tum/in/www1/hephaestus/intelligenceservice
#         server/application-server/src/main/java/de/tum/in/www1/hephaestus/intelligenceservice


if [[ $# -ne 1 ]]; then
    echo "Usage: $0 [major|minor|patch] OR $0 <version>"
    exit 1
fi

PARAM=$1

# Check if parameter is a semantic version format (X.Y.Z with optional pre-release and build metadata)
# Supports formats like: 1.2.3, 1.2.3-alpha, 1.2.3-alpha.1, 1.2.3-0.3.7, 1.2.3-x.7.z.92, 1.2.3+20130313144700, 1.2.3-beta+exp.sha.5114f85
if [[ "$PARAM" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$ ]]; then
    # Direct version specified (used by semantic-release)
    NEW_VERSION="$PARAM"
    # Extract the current version from webapp/package.json (the authoritative version for "hephaestus")
    CURRENT_VERSION=$(awk '/"name": "hephaestus"/ {found=1} found && /"version":/ { sub(/.*"version": "/, ""); sub(/".*/, ""); print; exit }' webapp/package.json)
    if [[ -z "$CURRENT_VERSION" ]]; then
        echo "Error: Could not determine current version from webapp/package.json"
        exit 1
    fi
    echo "Updating version from $CURRENT_VERSION to $NEW_VERSION..."
elif [[ "$PARAM" == "major" || "$PARAM" == "minor" || "$PARAM" == "patch" ]]; then
    # Increment type specified (manual usage)
    INCREMENT_TYPE=$PARAM
    
    # Function to increment a semantic version number (format: X.Y.Z)
    increment_version() {
        local version=$1
        local type=$2
        IFS='.' read -r major minor patch <<< "$version"
        case $type in
            major)
                major=$((major + 1))
                minor=0
                patch=0
                ;;
            minor)
                minor=$((minor + 1))
                patch=0
                ;;
            patch)
                patch=$((patch + 1))
                ;;
        esac
        echo "$major.$minor.$patch"
    }

    # Extract the current version from webapp/package.json (the authoritative version for "hephaestus")
    # This uses awk to strip everything before/after the version string.
    CURRENT_VERSION=$(awk '/"name": "hephaestus"/ {found=1} found && /"version":/ { sub(/.*"version": "/, ""); sub(/".*/, ""); print; exit }' webapp/package.json)
    if [[ -z "$CURRENT_VERSION" ]]; then
        echo "Error: Could not determine current version from webapp/package.json"
        exit 1
    fi

    NEW_VERSION=$(increment_version "$CURRENT_VERSION" "$INCREMENT_TYPE")
    echo "Updating version from $CURRENT_VERSION to $NEW_VERSION..."
else
    echo "Error: Invalid argument. Use one of [major, minor, patch] or provide a semantic version (e.g., 1.2.3, 1.0.0-alpha.1, 2.0.0-beta.1)."
    exit 1
fi


# Update webapp/package.json
awk -v old_version="$CURRENT_VERSION" -v new_version="$NEW_VERSION" '
    BEGIN {found_name = 0}
    /"name": "hephaestus"/ {found_name = 1}
    found_name && /"version":/ {
        sub("\"version\": \"" old_version "\"", "\"version\": \"" new_version "\"")
        found_name = 0
    }
    {print}
' webapp/package.json > webapp/package.json.tmp && mv webapp/package.json.tmp webapp/package.json

# Update webapp/package-lock.json (if it exists)
if [[ -f webapp/package-lock.json ]]; then
    awk -v old_version="$CURRENT_VERSION" -v new_version="$NEW_VERSION" '
        BEGIN {found_name = 0}
        /"name": "hephaestus"/ {found_name = 1}
        found_name && /"version":/ {
            sub("\"version\": \"" old_version "\"", "\"version\": \"" new_version "\"")
            found_name = 0
        }
        {print}
    ' webapp/package-lock.json > webapp/package-lock.json.tmp && mv webapp/package-lock.json.tmp webapp/package-lock.json
fi

# Update root package-lock.json (if it exists)
# 1) Update the top-level version field (before the packages block)
# 2) Update the first hephaestus entry's immediate version (mirrors webapp logic)
if [[ -f package-lock.json ]]; then
    # 1) Top-level version (only within header before "packages":)
    awk -v old_version="$CURRENT_VERSION" -v new_version="$NEW_VERSION" '
        BEGIN {in_header = 1; updated = 0}
        in_header && /"packages"[[:space:]]*:/ { in_header = 0 }
        in_header && !updated && /"version":/ {
            sub("\"version\": \"" old_version "\"", "\"version\": \"" new_version "\"")
            updated = 1
        }
        { print }
    ' package-lock.json > package-lock.json.tmp && mv package-lock.json.tmp package-lock.json

    # 2) First hephaestus name+version pair (e.g., packages[""] or packages["webapp"]) — matches the pattern you provided
    awk -v old_version="$CURRENT_VERSION" -v new_version="$NEW_VERSION" '
        BEGIN {found_name = 0}
        /"name": "hephaestus"/ {found_name = 1}
        found_name && /"version":/ {
            sub("\"version\": \"" old_version "\"", "\"version\": \"" new_version "\"")
            found_name = 0
        }
        {print}
    ' package-lock.json > package-lock.json.tmp && mv package-lock.json.tmp package-lock.json
fi

# Function to perform cross-platform sed in-place editing
sed_inplace() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -E -i '' "$@"
    else
        # Linux/Ubuntu
        sed -E -i "$@"
    fi
}

# Update Java source: OpenAPIConfiguration.java (if it exists)
if [[ -f server/application-server/src/main/java/de/tum/in/www1/hephaestus/OpenAPIConfiguration.java ]]; then
    sed_inplace "s#(version *= *\")[0-9]+(\.[0-9]+){2}(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?\"#\\1${NEW_VERSION}\"#" server/application-server/src/main/java/de/tum/in/www1/hephaestus/OpenAPIConfiguration.java
fi

# Update application.yml (server configuration) (if it exists)
if [[ -f server/application-server/src/main/resources/application.yml ]]; then
    sed_inplace "s#(version: *\")[0-9]+(\.[0-9]+){2}(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?\"#\1${NEW_VERSION}\"#" server/application-server/src/main/resources/application.yml
fi

# Update server/intelligence-service/pyproject.toml
if [[ -f server/intelligence-service/pyproject.toml ]]; then
    sed_inplace "s#(version *= *\")[0-9]+(\.[0-9]+){2}(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?\"#\1${NEW_VERSION}\"#" server/intelligence-service/pyproject.toml
fi

# Update server/webhook-ingest/pyproject.toml
if [[ -f server/webhook-ingest/pyproject.toml ]]; then
    sed_inplace "s#(version *= *\")[0-9]+(\.[0-9]+){2}(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?\"#\1${NEW_VERSION}\"#" server/webhook-ingest/pyproject.toml
fi

# Update server/intelligence-service/app/main.py (if it exists)
if [[ -f server/intelligence-service/app/main.py ]]; then
    sed_inplace "s#(version *= *\")[0-9]+(\.[0-9]+){2}(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?\"#\1${NEW_VERSION}\"#" server/intelligence-service/app/main.py
fi

# Update Maven POM (only update the project version for hephaestus) (if it exists)
if [[ -f server/application-server/pom.xml ]]; then
    # This awk script looks for the line with <artifactId>hephaestus</artifactId>,
    # then updates the next <version> element.
    awk -v new_version="${NEW_VERSION}" '
        /<artifactId>hephaestus<\/artifactId>/ { print; getline; if ($0 ~ /<version>/) { sub(/<version>[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?(-SNAPSHOT)?<\/version>/, "<version>" new_version "-SNAPSHOT</version>") } }
        { print }
    ' server/application-server/pom.xml > server/application-server/pom.xml.tmp && mv server/application-server/pom.xml.tmp server/application-server/pom.xml
fi

# Update server/application-server/openapi.yaml (non-quoted version) (if it exists)
if [[ -f server/application-server/openapi.yaml ]]; then
    sed_inplace "s#(version:[[:space:]]*)[0-9]+(\.[0-9]+){2}(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?#\1${NEW_VERSION}#" server/application-server/openapi.yaml
fi

# Update server/intelligence-service/openapi.yaml (non-quoted version) (if it exists)
if [[ -f server/intelligence-service/openapi.yaml ]]; then
    sed_inplace "s#(version:[[:space:]]*)[0-9]+(\.[0-9]+){2}(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?#\1${NEW_VERSION}#" server/intelligence-service/openapi.yaml
fi

# Update all files containing "The version of the OpenAPI document:" to use the new version,
# limited to the specified directories.
openapi_files=""
if [[ -d server/application-server/src/main/java/de/tum/in/www1/hephaestus/intelligenceservice ]]; then
    openapi_files+=" $(grep -rl "The version of the OpenAPI document:" server/application-server/src/main/java/de/tum/in/www1/hephaestus/intelligenceservice 2>/dev/null || true)"
fi

if [[ -n "$openapi_files" ]]; then
    for file in $openapi_files; do
        if [[ -f "$file" ]]; then
            echo "Updating OpenAPI document version in $file"
            sed_inplace "s#(The version of the OpenAPI document: )[0-9]+(\.[0-9]+){2}(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?#\\1${NEW_VERSION}#g" "$file"
        fi
    done
fi

echo "Version update complete. New version: ${NEW_VERSION}"
