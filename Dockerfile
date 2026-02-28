# Build image
FROM maven:3.9-ibm-semeru-21-jammy AS build
WORKDIR /usr/local/app
ARG GITHUB_TOKEN
# Copy project into image
COPY ./ /usr/local/app/
COPY .m2/settings.xml /root/.m2/settings.xml
# Build
RUN GITHUB_TOKEN=$GITHUB_TOKEN mvn clean package -DskipTests


# Run image
FROM ibm-semeru-runtimes:open-21-jre-jammy
# Copy jar
COPY --from=build /usr/local/app/target/ea-nation-server-*.jar /ea-nation-server.jar

EXPOSE 8080

# Start command
ENTRYPOINT ["java", "-jar", "/ea-nation-server.jar"]
