#!/bin/bash
set -euo pipefail

# Script to update the version across multiple files to a single, consistent new version.
# Usage: ./update_version.sh [major|minor|patch]
#
# This script updates the version in:
#   - webapp/package.json & webapp/package-lock.json (for "hephaestus")
#   - Java source: server/application-server/src/main/java/de/tum/in/www1/hephaestus/OpenAPIConfiguration.java
#   - YAML config: server/application-server/src/main/resources/application.yml
#   - Python projects: server/intelligence-service/pyproject.toml & server/webhook-ingest/pyproject.toml
#   - Python app: server/intelligence-service/app/main.py
#   - Maven POM: server/application-server/pom.xml (preserving -SNAPSHOT)
#   - OpenAPI docs: server/application-server/openapi.yaml & server/intelligence-service/openapi.yaml
#   - All files containing "The version of the OpenAPI document:" anywhere in the file.

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 [major|minor|patch]"
    exit 1
fi

INCREMENT_TYPE=$1

if [[ "$INCREMENT_TYPE" != "major" && "$INCREMENT_TYPE" != "minor" && "$INCREMENT_TYPE" != "patch" ]]; then
    echo "Error: Invalid argument. Use one of [major, minor, patch]."
    exit 1
fi

# Ensure git working directory is clean
if ! git diff-index --quiet HEAD --; then
    echo "Error: Git working tree is not clean. Please commit or stash your changes before updating version."
    exit 1
fi

# Detect platform for in-place sed editing (macOS vs Linux)
if [[ "$OSTYPE" == "darwin"* ]]; then
    SED_INPLACE=("sed" "-i" "")
else
    SED_INPLACE=("sed" "-i")
fi

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
CURRENT_VERSION=$(awk '/"name": "hephaestus"/ {found=1} found && /"version":/ { match($0, /"version": "([^"]+)"/, arr); print arr[1]; exit}' webapp/package.json)
if [[ -z "$CURRENT_VERSION" ]]; then
    echo "Error: Could not determine current version from webapp/package.json"
    exit 1
fi

NEW_VERSION=$(increment_version "$CURRENT_VERSION" "$INCREMENT_TYPE")
echo "Updating version from $CURRENT_VERSION to $NEW_VERSION..."

# Update webapp/package.json
awk -v old_version="$CURRENT_VERSION" -v new_version="$NEW_VERSION" '
    BEGIN {found_name = 0}
    /"name": "hephaestus"/ {found_name = 1}
    found_name && /"version": ".*"/ {
        sub("\"version\": \"" old_version "\"", "\"version\": \"" new_version "\"")
        found_name = 0
    }
    {print}
' webapp/package.json > webapp/package.json.tmp && mv webapp/package.json.tmp webapp/package.json

# Update webapp/package-lock.json
awk -v old_version="$CURRENT_VERSION" -v new_version="$NEW_VERSION" '
    BEGIN {found_name = 0}
    /"name": "hephaestus"/ {found_name = 1}
    found_name && /"version": ".*"/ {
        sub("\"version\": \"" old_version "\"", "\"version\": \"" new_version "\"")
        found_name = 0
    }
    {print}
' webapp/package-lock.json > webapp/package-lock.json.tmp && mv webapp/package-lock.json.tmp webapp/package-lock.json

# Update Java source: OpenAPIConfiguration.java
${SED_INPLACE[@]} "s/\(version *= *\"\)[0-9]\+\.[0-9]\+\.[0-9]\+\"/\1$NEW_VERSION\"/" server/application-server/src/main/java/de/tum/in/www1/hephaestus/OpenAPIConfiguration.java

# Update application.yml (server configuration)
${SED_INPLACE[@]} "s/\(version: *\"\)[0-9]\+\.[0-9]\+\.[0-9]\+\"/\1$NEW_VERSION\"/" server/application-server/src/main/resources/application.yml

# Update server/intelligence-service/pyproject.toml
${SED_INPLACE[@]} "s/\(version *= *\"\)[0-9]\+\.[0-9]\+\.[0-9]\+\"/\1$NEW_VERSION\"/" server/intelligence-service/pyproject.toml

# Update server/webhook-ingest/pyproject.toml
${SED_INPLACE[@]} "s/\(version *= *\"\)[0-9]\+\.[0-9]\+\.[0-9]\+\"/\1$NEW_VERSION\"/" server/webhook-ingest/pyproject.toml

# Update server/intelligence-service/app/main.py
${SED_INPLACE[@]} "s/\(version *= *\"\)[0-9]\+\.[0-9]\+\.[0-9]\+\"/\1$NEW_VERSION\"/" server/intelligence-service/app/main.py

# Update Maven POM (preserving -SNAPSHOT suffix)
${SED_INPLACE[@]} "s/\(<version>\)[0-9]\+\.[0-9]\+\.[0-9]\+\(-SNAPSHOT\)\?\(<\/version>\)/\1$NEW_VERSION-SNAPSHOT\3/" server/application-server/pom.xml

# Update server/application-server/openapi.yaml
${SED_INPLACE[@]} "s/\(version:[[:space:]]*\)\(\"[0-9]\+\.[0-9]\+\.[0-9]\+\"\|\([0-9]\+\.[0-9]\+\.[0-9]\+\)\)/\1$NEW_VERSION/" server/application-server/openapi.yaml

# Update server/intelligence-service/openapi.yaml
${SED_INPLACE[@]} "s/\(version:[[:space:]]*\)\(\"[0-9]\+\.[0-9]\+\.[0-9]\+\"\|\([0-9]\+\.[0-9]\+\.[0-9]\+\)\)/\1$NEW_VERSION/" server/intelligence-service/openapi.yaml

# Update all files containing "The version of the OpenAPI document:" to use the same version
openapi_files=$(grep -rl "The version of the OpenAPI document:" .)
if [[ -n "$openapi_files" ]]; then
    for file in $openapi_files; do
        echo "Updating OpenAPI document version in $file"
        ${SED_INPLACE[@]} "s/\(The version of the OpenAPI document: \)[0-9]\+\.[0-9]\+\.[0-9]\+/\1$NEW_VERSION/g" "$file"
    done
fi

# Stage all changes and commit
echo "Staging changes for git..."
git add -A

echo "Creating git commit..."
# git commit -m "Release: Bump version to $NEW_VERSION ($INCREMENT_TYPE update)"

echo "Version update complete. New version: $NEW_VERSION"
