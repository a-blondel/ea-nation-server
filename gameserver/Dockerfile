# Build image
FROM maven:3.9-ibm-semeru-21-jammy AS build
WORKDIR /usr/local/app
COPY . .
RUN mvn clean package -pl gameserver -am -DskipTests

# Run image
FROM ibm-semeru-runtimes:open-21-jre-jammy
COPY --from=build /usr/local/app/backend/target/ea-nation-gameserver-*.jar /ea-nation-gameserver.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/ea-nation-gameserver.jar"]
