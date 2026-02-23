FROM clojure:openjdk-17-tools-deps

WORKDIR /app

COPY deps.edn .
RUN clojure -P

COPY src ./src
COPY resources ./resources

EXPOSE 8080

CMD ["clojure", "-M", "-m", "order-system.system"]
