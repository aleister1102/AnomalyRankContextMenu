#!/bin/bash

# release.sh - Bump version, build, and release to GitHub

set -e

# Ensure we are in the project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
cd "$PROJECT_ROOT"

# Check if gh cli is installed
if ! command -v gh &> /dev/null; then
    echo "Error: gh cli is not installed. Please install it to proceed."
    exit 1
fi

# Function to get current version from build.gradle.kts
get_current_version() {
    grep 'version = "' build.gradle.kts | sed -E 's/.*version = "([^"]+)".*/\1/'
}

# Function to calculate new version
bump_version() {
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
        *)
            echo "Error: Invalid bump type. Use patch, minor, or major."
            exit 1
            ;;
    esac
    
    echo "$major.$minor.$patch"
}

# 1. Get User Input
if [ -n "$1" ]; then
    BUMP_TYPE=$1
else
    echo "Current version: $(get_current_version)"
    echo "Enter bump type (patch, minor, major):"
    read BUMP_TYPE
fi

# Validate input
if [[ ! "$BUMP_TYPE" =~ ^(patch|minor|major)$ ]]; then
    echo "Error: Invalid bump type '$BUMP_TYPE'. Must be patch, minor, or major."
    exit 1
fi

# 2. Bump Version
CURRENT_VERSION=$(get_current_version)
NEW_VERSION=$(bump_version "$CURRENT_VERSION" "$BUMP_TYPE")

echo "Bumping version: $CURRENT_VERSION -> $NEW_VERSION"

# Update build.gradle.kts (macOS sed syntax)
sed -i '' "s/version = \"$CURRENT_VERSION\"/version = \"$NEW_VERSION\"/" build.gradle.kts

# 3. Build Jar
echo "Building project..."
./gradlew clean build

# Identify the built jar
# Assuming shadowJar produces -all.jar based on standard behavior and build.gradle.kts
JAR_FILE="build/libs/ApplyAnomalyRank-$NEW_VERSION-all.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: Expected jar file not found at $JAR_FILE"
    echo "Checking build/libs/..."
    if [ -d "build/libs" ]; then
        ls -l build/libs/
    else
        echo "build/libs directory does not exist."
    fi
    exit 1
fi

echo "Build successful. Jar: $JAR_FILE"

# 4. Git Commit and Tag
echo "Committing version bump..."
git add build.gradle.kts
git commit -m "Bump version to $NEW_VERSION"

echo "Tagging release v$NEW_VERSION..."
git tag "v$NEW_VERSION"

echo "Pushing changes and tags..."
# Push current branch
git push origin HEAD
# Push tag
git push origin "v$NEW_VERSION"

# 5. Publish to GitHub Release
echo "Creating GitHub Release..."
gh release create "v$NEW_VERSION" "$JAR_FILE" --generate-notes --title "v$NEW_VERSION"

echo "Release v$NEW_VERSION published successfully!"
