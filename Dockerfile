FROM clojure:openjdk-17-tools-deps

WORKDIR /app

COPY deps.edn.new ./deps.edn
RUN clojure -P

COPY src ./src
COPY resources ./resources

EXPOSE 8081

CMD ["clojure", "-M", "-m", "order-system.system"]
