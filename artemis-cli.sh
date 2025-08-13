#!/bin/bash
set -e

# Find the script's directory to locate other files relative to it.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"

# Find the application JAR file.
JAR_FILE=$(find "$SCRIPT_DIR" -maxdepth 1 -name "artemis-cli-*.jar" | head -n 1)
if [ -z "$JAR_FILE" ]; then
  echo "Error: Could not find artemis-cli-*.jar in the script directory."
  exit 1
fi

# Define paths to the configuration files.
APP_CONFIG="$SCRIPT_DIR/config/application.conf"
LOG_CONFIG="$SCRIPT_DIR/config/logback.xml"

# Check if the configuration files exist.
if [ ! -f "$APP_CONFIG" ]; then
  echo "Error: Application config not found at $APP_CONFIG"
  exit 1
fi
if [ ! -f "$LOG_CONFIG" ]; then
  echo "Error: Logback config not found at $LOG_CONFIG"
  exit 1
fi

# Execute the Java application, passing through all script arguments.
# The wrapper automatically adds the required config file paths.
# The global --config option must come BEFORE the subcommand.
java -Dlogback.configurationFile="$LOG_CONFIG" \
  -jar "$JAR_FILE" \
  --config "$APP_CONFIG" \
  "$@"
