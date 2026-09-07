# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre-jammy
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD curl -f http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
