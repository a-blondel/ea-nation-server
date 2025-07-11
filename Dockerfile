# Build image
FROM maven:3.9-ibm-semeru-21-jammy AS build
WORKDIR /usr/local/app
# Copy project into image
COPY ./ /usr/local/app/
# Build
RUN mvn clean package -DskipTests


# Run image
FROM ibm-semeru-runtimes:open-21-jre-jammy
# Copy jar
COPY --from=build /usr/local/app/target/ea-nation-server-*.jar /ea-nation-server.jar

EXPOSE 8080

# Start command
ENTRYPOINT ["java", "-jar", "/ea-nation-server.jar"]
