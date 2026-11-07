FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY src/main src/main
RUN mkdir out && javac -d out $(find src/main/java -name "*.java") && cp -r src/main/resources/. out/

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/out /app/classes
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "/app/classes", "com.ganesna.wms.WarehouseApp"]
