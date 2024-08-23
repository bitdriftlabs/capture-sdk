#!/bin/bash

set -euo pipefail

# A simple script which returns true if success or skipped is provided as the first argument,
# false otherwise. This is used to simplify checking the GA job status.

case $1 in
  "success")
    exit 0
  ;;

  "skipped")
    exit 0
  ;;
  *)
  echo "result $1"
  exit 1
esac

