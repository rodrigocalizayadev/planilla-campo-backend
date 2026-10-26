# syntax=docker/dockerfile:1

# --- Build: compila el jar con el wrapper de Maven. Los tests corren en CI, no acá.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/*.jar application.jar

# --- Extract: separa el jar en capas para que un cambio de código no rebaje las dependencias.
FROM eclipse-temurin:21-jre AS extract
WORKDIR /extract
COPY --from=build /build/application.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# --- Runtime
FROM eclipse-temurin:21-jre
RUN groupadd --system spring && useradd --system --gid spring spring
WORKDIR /application
COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
