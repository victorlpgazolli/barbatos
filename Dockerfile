FROM --platform=linux/amd64 eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y \
    build-essential \
    curl \
    unzip \
    git \
    libssl-dev \
    libcurl4-openssl-dev \
    libssh-dev \
    libbrotli-dev \
    libkrb5-dev \
    libidn2-dev \
    libldap2-dev \
    libnghttp2-dev \
    libpsl-dev \
    librtmp-dev \
    libzstd-dev \
    zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

ENV MISE_INSTALL_PATH="/usr/local/bin/mise"
ENV MISE_DATA_DIR="/mise"
ENV MISE_CONFIG_DIR="/mise"
ENV PATH="/mise/shims:$PATH"

RUN curl https://mise.run | sh && mise use -g node@20.19.5

WORKDIR /app