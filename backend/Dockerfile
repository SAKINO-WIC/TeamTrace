FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN chmod +x mvnw && ./mvnw package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ENV SERVER_PORT=8081
ENV JWT_EXPIRATION_SECONDS=604800

EXPOSE 8081

COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT java -jar app.jar \
  --server.port=$SERVER_PORT \
  --spring.datasource.url="$DB_URL" \
  --spring.datasource.username=$DB_USERNAME \
  --spring.datasource.password=$DB_PASSWORD \
  --jwt.secret=$JWT_SECRET \
  --jwt.expiration-seconds=$JWT_EXPIRATION_SECONDS \
  --teamtrace.admin.phone=$ADMIN_PHONE \
  --teamtrace.admin.password=$ADMIN_PASSWORD \
  --teamtrace.admin.name=$ADMIN_NAME \
  --teamtrace.security.cors.allowed-origins=$ALLOWED_ORIGINS
