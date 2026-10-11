# =============================================================================
# Build Stage
# Build context is the repository root: the multi-module reactor needs the
# parent pom plus the backend module pom (ADR-012).
# =============================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy the parent and every module pom.xml first: Maven reads the whole
# reactor (all <modules>) before applying -pl, and this keeps the
# dependency cache keyed on the poms (ADR-012)
COPY pom.xml .
COPY backend/pom.xml backend/pom.xml
COPY mcp-server/pom.xml mcp-server/pom.xml
RUN mvn -pl backend -am dependency:go-offline -B

# Copy source and build
COPY backend/src backend/src
RUN mvn -pl backend clean package -DskipTests -B

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
COPY --from=builder /app/backend/target/*.jar app.jar

# Change ownership
RUN chown appuser:appgroup app.jar

USER appuser

# Expose port
EXPOSE 8080

# Health check against the unauthenticated /actuator/health endpoint
# (status only, no details; see docs/arch/deployment.md).
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider "http://localhost:${PORT:-${SERVER_PORT:-8080}}/actuator/health" || exit 1

# Run with dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]
CMD ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
