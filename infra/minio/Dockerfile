# Build tagged upstream sources rather than depending on retired public MinIO images.
FROM golang:1.24-alpine AS source
RUN apk add --no-cache git
ENV CGO_ENABLED=0 GOMAXPROCS=2
ARG MINIO_VERSION=RELEASE.2025-09-07T16-13-09Z
ARG MC_VERSION=RELEASE.2025-08-13T08-35-41Z

FROM source AS server-build
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    go install -p=2 -ldflags="-s -w" github.com/minio/minio@${MINIO_VERSION}

FROM source AS client-build
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    go install -p=2 -ldflags="-s -w" github.com/minio/mc@${MC_VERSION}

FROM alpine:3.22 AS server
RUN apk add --no-cache ca-certificates && addgroup -g 10001 minio && adduser -D -u 10001 -G minio minio \
    && mkdir /data && chown minio:minio /data
COPY --from=server-build /go/bin/minio /usr/local/bin/minio
USER minio:minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]

FROM alpine:3.22 AS client
RUN apk add --no-cache ca-certificates
COPY --from=client-build /go/bin/mc /usr/local/bin/mc
ENTRYPOINT ["mc"]
