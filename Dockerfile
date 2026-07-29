FROM amazoncorretto:21-alpine
WORKDIR /app

# Une instruction COPY par couche, de la plus stable à la plus volatile. Docker met chaque
# couche en cache : seule la dernière est reconstruite lors d'un déploiement courant, alors
# qu'un jar unique invalidait tout à chaque fois.
COPY layers/dependencies/ ./
COPY layers/spring-boot-loader/ ./
COPY layers/snapshot-dependencies/ ./
COPY layers/application/ ./

# Le jar étant extrait, on lance par le JarLauncher plutôt que par -jar.
# Depuis Spring Boot 3.2 la classe vit dans le sous-paquet `launch`.
ENTRYPOINT ["java", "--add-opens=java.base/java.util=ALL-UNNAMED", \
            "org.springframework.boot.loader.launch.JarLauncher"]
