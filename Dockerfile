# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-noble@sha256:0386aaf49d6756b4856119f8e037f40cc865c7c8fbdda7c81733cc806f462daf AS builder

WORKDIR /workspace

RUN apt-get update && \
    apt-get install --yes --no-install-recommends unzip && \
    rm -rf /var/lib/apt/lists/*

COPY --chmod=0755 mvnw ./mvnw
COPY .mvn/ .mvn/
COPY pom.xml ./

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw --batch-mode --no-transfer-progress clean package && \
    test -f target/GameExchange_war-1.0.0-rc2.war

FROM tomcat:9.0.120-jre17-temurin-noble@sha256:c8963563a89328eff74e48ff01c5ac774672e6ffefd5c96decdf8d0718ca87be AS runtime

WORKDIR /usr/local/tomcat

RUN groupadd --gid 10001 gameexchange && \
    useradd --uid 10001 \
        --gid gameexchange \
        --no-create-home \
        --home-dir /usr/local/tomcat \
        --shell /usr/sbin/nologin \
        gameexchange && \
    rm -rf webapps/*

COPY --from=builder --chown=gameexchange:gameexchange \
    /workspace/target/GameExchange_war-1.0.0-rc2.war \
    webapps/ROOT.war

RUN mkdir -p conf/Catalina/localhost && \
    chown gameexchange:gameexchange conf/Catalina/localhost && \
    chmod 0750 conf/Catalina/localhost && \
    chown -R gameexchange:gameexchange \
        webapps work temp logs && \
    chmod 0750 webapps work temp logs

ENV SIMULATOR_ENABLED=false \
    CATALINA_OPTS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

EXPOSE 8080

USER gameexchange:gameexchange

CMD ["catalina.sh", "run"]
