#!/usr/bin/env bash
#
# Déploie le monolithe sur le VPS nelima.
#
# Le jar est construit ici puis expédié en couches. Les dépendances pèsent 125 Mo et ne
# changent qu'au gré du pom ; le code applicatif fait 2,5 Mo et change à chaque fois. Envoyer
# le jar entier à chaque déploiement coûtait une dizaine de minutes de transfert, au point
# d'avoir fait tester deux fois l'ancienne version en croyant tester la nouvelle.
#
# rsync serait l'outil naturel mais il est absent du VPS, et l'installer demande un sudo dont
# on ne dispose pas. La sélection se fait donc à l'empreinte : les couches lourdes ne repartent
# que si leur contenu a bougé.
#
set -euo pipefail

JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home}"
REMOTE="${REMOTE:-nelima}"
REMOTE_DIR="${REMOTE_DIR:-deployment/nelima}"
SKIP_TESTS="${SKIP_TESTS:-false}"

say() { printf '\n==> %s\n' "$1"; }

say "Construction"
if [ "$SKIP_TESTS" = "true" ]; then
    JAVA_HOME="$JAVA_HOME" mvn -B clean package -DskipTests
else
    JAVA_HOME="$JAVA_HOME" mvn -B clean package
fi

JAR=$(ls target/nelima-backend-*.jar | head -1)

say "Extraction des couches"
rm -rf target/layers
"$JAVA_HOME/bin/java" -Djarmode=tools -jar "$JAR" extract --layers --launcher --destination target/layers
du -sh target/layers/* | sed 's/^/    /'

# Empreinte des couches lourdes : dépendances et lanceur. Elles ne bougent qu'en cas de
# changement du pom ou de version de Spring Boot.
HEAVY_HASH=$(find target/layers/dependencies target/layers/spring-boot-loader -type f -exec shasum {} \; \
    | sort | shasum | cut -d' ' -f1)
REMOTE_HASH=$(ssh "$REMOTE" "cat $REMOTE_DIR/.heavy-hash 2>/dev/null || echo none")

ssh "$REMOTE" "mkdir -p $REMOTE_DIR/layers"

if [ "$HEAVY_HASH" != "$REMOTE_HASH" ]; then
    say "Dépendances modifiées — envoi complet (~125 Mo, quelques minutes)"
    ssh "$REMOTE" "rm -rf $REMOTE_DIR/layers/dependencies $REMOTE_DIR/layers/spring-boot-loader"
    COPYFILE_DISABLE=1 tar czf - -C target/layers dependencies spring-boot-loader \
        | ssh "$REMOTE" "tar xzf - -C $REMOTE_DIR/layers"
    ssh "$REMOTE" "echo '$HEAVY_HASH' > $REMOTE_DIR/.heavy-hash"
else
    say "Dépendances inchangées — rien à envoyer"
fi

say "Envoi du code applicatif"
ssh "$REMOTE" "rm -rf $REMOTE_DIR/layers/application $REMOTE_DIR/layers/snapshot-dependencies"
COPYFILE_DISABLE=1 tar czf - -C target/layers application snapshot-dependencies \
    | ssh "$REMOTE" "tar xzf - -C $REMOTE_DIR/layers"
scp -q Dockerfile "$REMOTE:$REMOTE_DIR/Dockerfile"
ssh "$REMOTE" "rm -f $REMOTE_DIR/nelima.jar"

say "Reconstruction de l'image et redémarrage"
ssh "$REMOTE" "cd deployment && docker compose build nelima && docker compose up -d nelima"

say "Attente du démarrage"
# On borne la lecture des journaux à l'instant de démarrage du conteneur qui vient d'être
# recréé. Une fenêtre relative du type `--since 10m` capterait le « Started » du démarrage
# précédent et déclarerait le succès avant même que la nouvelle version soit en ligne.
ssh "$REMOTE" '
  STARTED_AT=$(docker inspect nelima --format "{{.State.StartedAt}}")
  for i in $(seq 1 90); do
    if docker logs --since "$STARTED_AT" nelima 2>&1 \
        | grep -qE "Started NelimaApplication|Application run failed"; then break; fi
    sleep 4
  done
  docker logs --since "$STARTED_AT" nelima 2>&1 \
    | grep -E "Started NelimaApplication|Application run failed" | tail -1
'

say "Vérification externe"
curl -s -o /dev/null -w "    health HTTP %{http_code}\n" --max-time 30 https://api.nelima.ci/actuator/health
