#!/bin/bash

DURATION=\${1:-600}
OUTPUT_FILE="benchmark_results.csv"

echo "Time,Component,CPU,Memory" > \$OUTPUT_FILE

echo "Starting resource monitoring for \$DURATION seconds..."

for ((i=0; i<\$DURATION; i+=5)); do
    TIMESTAMP=\$(date +%s)
    # Get stats for palamedes and themis
    STATS=\$(kubectl top pods -n amocna --no-headers | grep -E "palamedes|themis")
    while read -r line; do
        if [ -n "\$line" ]; then
            NAME=\$(echo \$line | awk '{print \$1}')
            CPU=\$(echo \$line | awk '{print \$2}')
            MEM=\$(echo \$line | awk '{print \$3}')
            echo "\$TIMESTAMP,\$NAME,\$CPU,\$MEM" >> \$OUTPUT_FILE
        fi
    done <<< "\$STATS"
    sleep 5
done

echo "Monitoring complete. Results saved to \$OUTPUT_FILE"
