# VHS + JDK 25 for recording docs/demo.gif:
#   ./gradlew fatJar
#   docker build -t dopajshell-vhs -f docs/demo.Dockerfile docs
#   docker run --rm -v "$PWD:/vhs" dopajshell-vhs docs/demo.tape
FROM ghcr.io/charmbracelet/vhs
COPY --from=eclipse-temurin:25-jdk /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk PATH=/opt/java/openjdk/bin:$PATH LANG=C.UTF-8 COLORTERM=truecolor
RUN apt-get update && apt-get install -y --no-install-recommends fonts-dejavu-core && rm -rf /var/lib/apt/lists/*
