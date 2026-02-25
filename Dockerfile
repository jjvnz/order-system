FROM clojure:openjdk-17-tools-deps

WORKDIR /app

COPY deps.edn ./deps.edn
RUN clojure -P

COPY src ./src

ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV HTTP_PORT=8081

EXPOSE 8081

CMD ["clojure", "-M", "-m", "order-system.system"]
