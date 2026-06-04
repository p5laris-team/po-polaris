FROM eclipse-temurin:21-jre

ARG JAR_FILE

WORKDIR /app

COPY ${JAR_FILE} app.jar

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]