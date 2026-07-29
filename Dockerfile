FROM amazoncorretto:21-alpine
WORKDIR /
ADD nelima.jar nelima.jar
ENTRYPOINT ["java", "-jar", "--add-opens=java.base/java.util=ALL-UNNAMED", "nelima.jar"]
