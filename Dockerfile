FROM eclipse-temurin:17-jre-alpine@sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57
# Upgrade the OpenSSL runtime packages to the CVE-2026-14456 fixed build.
RUN apk add --no-cache --upgrade \
    libcrypto3=3.5.8-r0 \
    libssl3=3.5.8-r0 \
    openssl=3.5.8-r0

WORKDIR /app

RUN apk add --no-cache --upgrade \
        libexpat=2.8.3-r0 \
        p11-kit=0.26.2-r0 \
        p11-kit-trust=0.26.2-r0 \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app

COPY --chown=10001:10001 target/system-data-service-1.0.0.jar app.jar
COPY --chown=10001:10001 fixtures/datasets fixtures/datasets
COPY --chown=10001:10001 scripts/container-healthcheck.sh /usr/local/bin/container-healthcheck

EXPOSE 8103

USER 10001:10001

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD ["/usr/local/bin/container-healthcheck"]

ENTRYPOINT ["java", "-jar", "app.jar"]
