# =============================================================================
# Build Stage
# Build context is the repository root: the multi-module reactor needs the
# parent pom plus every module pom for reactor validation (ADR-012).
# =============================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy all pom.xml files first for dependency caching
COPY pom.xml .
COPY api-server/pom.xml api-server/pom.xml
COPY mcp-server/pom.xml mcp-server/pom.xml
RUN mvn -pl mcp-server -am dependency:go-offline -B

# Copy source and build only this module
COPY mcp-server/src mcp-server/src
RUN mvn -pl mcp-server clean package -DskipTests -B

# =============================================================================
# Runtime Stage
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/mcp-server/target/*.jar app.jar

# Change ownership
RUN chown appuser:appgroup app.jar

USER appuser

# Internal-only service: EXPOSE is metadata, no host port is published in
# docker compose except a loopback-only mapping for local debugging.
EXPOSE 8081

# Health check against the unauthenticated /actuator/health endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider "http://localhost:8081/actuator/health" || exit 1

# Run with dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]
CMD ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
