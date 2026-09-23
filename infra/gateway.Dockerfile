FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY demo-api/pom.xml demo-api/pom.xml
RUN chmod +x mvnw && ./mvnw -B -ntp -pl gateway-service -am dependency:go-offline

COPY gateway-service/src gateway-service/src
RUN ./mvnw -B -ntp -pl gateway-service -am clean package

FROM eclipse-temurin:17-jre-jammy

RUN useradd --system --uid 10001 --create-home modushield
WORKDIR /app
COPY --from=build /workspace/gateway-service/target/gateway-service-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
