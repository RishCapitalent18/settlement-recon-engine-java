#!/usr/bin/env bash
# Compile and run the reconciliation engine over the committed dataset.
set -e
mkdir -p out
javac -d out src/main/java/com/recon/*.java
java -cp out com.recon.App data
