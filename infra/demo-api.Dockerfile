FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY demo-api/pom.xml demo-api/pom.xml
RUN chmod +x mvnw && ./mvnw -B -ntp -pl demo-api -am dependency:go-offline

COPY demo-api/src demo-api/src
RUN ./mvnw -B -ntp -pl demo-api -am clean package

FROM eclipse-temurin:17-jre-jammy

RUN useradd --system --uid 10002 --create-home modushield
WORKDIR /app
COPY --from=build /workspace/demo-api/target/demo-api-*.jar app.jar
USER 10002
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
