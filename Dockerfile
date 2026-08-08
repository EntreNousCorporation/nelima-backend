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
#
# Réglage mémoire (pas de fuite constatée — le tas vivant tient dans ~82 Mo après GC ; incident du
# 08/08). Sans borne, la JVM prenait 25 % de l'hôte (~5,7 Go) comme tas maximum et G1 laissait le
# garbage s'accumuler : sous une rafale, l'allocation dépassait le GC → OOM + spirale de
# ramasse-miettes, process figé faute de redémarrer. Tout est borné pour que la JVM lève un OOM
# *propre* (avec dump + sortie + redémarrage) bien avant que le noyau ne tue le conteneur à sa
# limite de 3 Go — sinon ce serait un SIGKILL sans empreinte.
#   -Xmx1g               tas borné à 12× le tas vivant : G1 collecte tôt, fini la spirale
#   MaxMetaspaceSize     borne le Metaspace (natif, compté dans le cgroup) → OOM propre si dérive
#   MaxDirectMemorySize  sinon = Xmx par défaut (Java 21) : le natif Netty/NIO pouvait à lui seul
#                        approcher la limite conteneur et provoquer un SIGKILL avant l'OOM JVM
#   ExitOnOutOfMemory    sur un OOM, la JVM sort (code 3) → restart: on-failure la relance
#   HeapDumpPath fixe    un seul fichier réécrit (pas un dossier qui gonfle et sature le disque)
#   -Xlog:gc             journal GC borné, dans le volume, pour diagnostiquer une récidive
ENTRYPOINT ["java", \
            "-Xms256m", "-Xmx1g", \
            "-XX:MaxMetaspaceSize=256m", \
            "-XX:MaxDirectMemorySize=256m", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/dumps/nelima.hprof", \
            "-Xlog:gc*:file=/dumps/gc.log:time,uptime,level,tags:filecount=5,filesize=10m", \
            "--add-opens=java.base/java.util=ALL-UNNAMED", \
            "org.springframework.boot.loader.launch.JarLauncher"]
