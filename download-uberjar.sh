#!/usr/bin/env bash

set -eu

test -f /mnt/spark-clj-sandbox-0.1.0-SNAPSHOT-standalone.jar && rm -f /mnt/spark-clj-sandbox-0.1.0-SNAPSHOT-standalone.jar

/usr/bin/hdfs dfs -get s3://com.relaynetwork/jc/spark-clj-sandbox-0.1.0-SNAPSHOT-standalone.jar /mnt/
