#!/bin/sh
set -eu
attempt=0
until mc alias set parcel-local http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null 2>&1 && mc ready parcel-local >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "MinIO did not become ready" >&2
    exit 1
  fi
  sleep 2
done
mc mb --ignore-existing "parcel-local/$MINIO_PROOF_BUCKET"
mc anonymous set none "parcel-local/$MINIO_PROOF_BUCKET"
