FROM golang:1.27.4-alpine3.24@sha256:0a5f7fe796599ff487605987432e7619ae45beeb2fba4c2ce0b907879932aebf AS go-tools
ENV CGO_ENABLED=0 GOTOOLCHAIN=local
ADD --checksum=sha256:207a2045e890d3ca3326027c5f585733085960b401a49dbc00e3bb628c054cf8 https://codeload.github.com/tianon/gosu/tar.gz/refs/tags/1.20 /tmp/gosu.tar.gz
ADD --checksum=sha256:8753af202acbfbd9c4f14b2fb85e2171c4cf053b836ccab8d78faa1a45d77fbb https://codeload.github.com/testcontainers/moby-ryuk/tar.gz/refs/tags/0.14.0 /tmp/ryuk.tar.gz
RUN mkdir /src /out && tar -xzf /tmp/gosu.tar.gz -C /src && tar -xzf /tmp/ryuk.tar.gz -C /src
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    cd /src/gosu-1.20 && go build -mod=readonly -trimpath -buildvcs=false -ldflags='-w' -o /out/gosu . \
    && go version -m /out/gosu && /out/gosu --version && /out/gosu nobody true
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    cd /src/moby-ryuk-0.14.0 && go build -mod=readonly -trimpath -buildvcs=false -ldflags='-w' -o /out/ryuk . \
    && go version -m /out/ryuk

FROM maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS maven
ARG RECIPE_SHA=unverified
LABEL io.scarletnexus.recipe.sha256=$RECIPE_SHA io.scarletnexus.tool=maven
RUN apt-get update && apt-get install -y --no-install-recommends --only-upgrade \
    apt=2.8.4ubuntu0.1 libapt-pkg6.0t64=2.8.4ubuntu0.1 libbrotli1=1.1.0-2ubuntu0.1 \
    libssl3t64=3.0.13-0ubuntu3.16 openssl=3.0.13-0ubuntu3.16 \
    && rm -rf /var/lib/apt/lists/*
ADD --checksum=sha256:b530942257fb935f8b2cfaa5f8eb5bd59c555fd8e8d01b8ce98912e077ea606c https://repo.maven.apache.org/maven2/com/google/guava/guava/33.7.2-jre/guava-33.7.2-jre.jar /usr/share/maven/lib/guava-33.7.2-jre.jar
RUN test -f /usr/share/maven/lib/guava-33.6.0-jre.jar && rm /usr/share/maven/lib/guava-33.6.0-jre.jar && mvn --version

FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS node
ARG RECIPE_SHA=unverified
LABEL io.scarletnexus.recipe.sha256=$RECIPE_SHA io.scarletnexus.tool=node
ADD --checksum=sha256:8ad8563b81e71b90ef405b140bccf3ba9b5e7884ab210bd49ee4e2f4bc6f61f5 https://registry.npmjs.org/npm/-/npm-11.21.2.tgz /tmp/npm.tgz
RUN npm install --global /tmp/npm.tgz --ignore-scripts --no-audit --no-fund \
    && rm /tmp/npm.tgz && test "$(npm --version)" = 11.21.2

FROM postgres:17.12-alpine3.24@sha256:a77aa70e641402a21411b92b721c58d10f23b50ae5fdd2333efd22d166e1e5f7 AS postgres
ARG RECIPE_SHA=unverified
LABEL io.scarletnexus.recipe.sha256=$RECIPE_SHA io.scarletnexus.tool=postgres
COPY --from=go-tools /out/gosu /usr/local/bin/gosu
RUN gosu --version && gosu nobody true

FROM scratch AS ryuk
ARG RECIPE_SHA=unverified
LABEL io.scarletnexus.recipe.sha256=$RECIPE_SHA io.scarletnexus.tool=ryuk org.testcontainers.ryuk=true
COPY --from=go-tools /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=go-tools /out/ryuk /bin/ryuk
CMD ["/bin/ryuk"]
