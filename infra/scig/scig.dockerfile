FROM alpine:3.19

RUN apk add --no-cache \
    bash \
    curl \
    jq \
    redis \
    ca-certificates \
    gettext \
    tar \
    coreutils

# Install Syft & Grype for SBOM generation and CVE vulnerability scanning
RUN curl -sSfL https://raw.githubusercontent.com/anchore/syft/main/install.sh | sh -s -- -b /usr/local/bin \
    && curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh | sh -s -- -b /usr/local/bin \
    && curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin

# Install kubectl
RUN curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" \
    && chmod +x kubectl && mv kubectl /usr/local/bin/

COPY infra/scig/scan.sh /usr/local/bin/scan.sh
RUN chmod +x /usr/local/bin/scan.sh

ENTRYPOINT ["/usr/local/bin/scan.sh"]
