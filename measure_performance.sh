#!/bin/bash

# --- Performance Measurement Script ---
# Runs a command multiple times and calculates the average 'user' time,
# which is the most accurate measure of CPU work done by the application.

set -e

# --- Configuration ---
ITERATIONS=100
# Command for the standard JVM run
COMMAND_STD="./artemis-cli -c config/application.conf subscribe --name client1 --topic DurableNewsTopic"
# Command for the AppCDS-optimized run
COMMAND_APPCDS="./artemis-cli2 -c config/application.conf subscribe --name client1 --topic DurableNewsTopic"


# --- Function to run and measure a command ---
# Takes two arguments: the command string and a description.
measure_performance() {
    local command_to_run="$1"
    local description="$2"
    local total_user_time=0.0

    echo "--- Measuring performance for: $description ---"

    for i in $(seq 1 $ITERATIONS); do
        # Execute the command, redirecting its stdout to /dev/null to keep the output clean.
        # The subshell `(time ...)` is necessary to capture the timing information from stderr.
        user_time_str=$({ time $command_to_run > /dev/null; } 2>&1 | grep 'user' | awk '{print $2}')

        # Convert the time format (e.g., "0m3.141s") to total seconds.
        # sed replaces 'm' with a space and removes 's', turning "0m3.141s" into "0 3.141".
        # awk then calculates the total seconds (minutes * 60 + seconds).
        user_time_seconds=$(echo "$user_time_str" | sed 's/m/ /; s/s//' | awk '{print $1 * 60 + $2}')

        # Add the result to the running total using 'bc' for floating-point arithmetic.
        total_user_time=$(echo "$total_user_time + $user_time_seconds" | bc)

        echo "Run $i: ${user_time_seconds}s"
    done

    # Calculate the average, using 'bc' for floating-point division.
    average_user_time=$(echo "scale=3; $total_user_time / $ITERATIONS" | bc)

    echo "-------------------------------------------------"
    echo "Average 'user' time over $ITERATIONS runs: $average_user_time seconds"
    echo "-------------------------------------------------"
    echo
}

# --- Main Execution ---
# Run the benchmark for both the standard and AppCDS versions.
measure_performance "$COMMAND_STD" "Standard JVM Startup"
measure_performance "$COMMAND_APPCDS" "AppCDS Optimized Startup"

