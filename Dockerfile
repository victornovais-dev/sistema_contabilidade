FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

RUN chmod +x mvnw
RUN ./mvnw -DskipTests package

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN ./mvnw -q -DskipTests \
    org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
    -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.args="install chromium"

FROM eclipse-temurin:25-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        fonts-liberation \
        fonts-noto-color-emoji \
        libasound2 \
        libatk-bridge2.0-0 \
        libatk1.0-0 \
        libc6 \
        libcairo2 \
        libcups2 \
        libdbus-1-3 \
        libdrm2 \
        libexpat1 \
        libfontconfig1 \
        libgbm1 \
        libglib2.0-0 \
        libgtk-3-0 \
        libnspr4 \
        libnss3 \
        libpango-1.0-0 \
        libpangocairo-1.0-0 \
        libstdc++6 \
        libu2f-udev \
        libuuid1 \
        libvulkan1 \
        libx11-6 \
        libx11-xcb1 \
        libxcb1 \
        libxcomposite1 \
        libxdamage1 \
        libxext6 \
        libxfixes3 \
        libxkbcommon0 \
        libxrandr2 \
        xdg-utils \
    && rm -rf /var/lib/apt/lists/*

ENV APP_HOME=/app
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

WORKDIR ${APP_HOME}

RUN useradd --create-home --shell /bin/bash spring \
    && mkdir -p ${APP_HOME}/logs \
    && chown -R spring:spring ${APP_HOME}

COPY --from=build /app/target/*.jar app.jar
COPY --from=build /ms-playwright /ms-playwright

RUN chown -R spring:spring ${APP_HOME} /ms-playwright

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
