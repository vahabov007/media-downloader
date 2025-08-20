FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY src ./src

RUN ./mvnw package -DskipTests

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

RUN apk add --no-cache python3 ffmpeg wget

RUN wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && yt-dlp -U

RUN yt-dlp --version

COPY --from=build /app/target/media-downloader-0.0.1-SNAPSHOT.jar app.jar

CMD ["java","-jar","app.jar"]