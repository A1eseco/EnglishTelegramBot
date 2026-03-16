FROM eclipse-temurin:17
COPY EnglishTelegramBot-1.0-SNAPSHOT-jar-with-dependencies.jar EnglishTelegramBot-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD ["java", "-jar", "EnglishTelegramBot-1.0-SNAPSHOT-jar-with-dependencies.jar"]