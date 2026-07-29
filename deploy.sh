#!/usr/bin/env bash
#
# Déploie le monolithe sur le VPS nelima.
#
# Le jar est construit ici puis envoyé, plutôt que compilé sur le serveur : cela
# éviterait d'y installer Maven et un jeton GitHub Packages pour résoudre
# PaySwitch.
#
set -euo pipefail

JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home}"
REMOTE="${REMOTE:-nelima}"
REMOTE_DIR="${REMOTE_DIR:-~/deployment}"

echo "==> Construction"
JAVA_HOME="$JAVA_HOME" mvn -B clean package

JAR=$(ls target/nelima-backend-*.jar | head -1)
echo "==> Envoi de $JAR"
scp -q "$JAR" "$REMOTE:$REMOTE_DIR/nelima/nelima.jar"
scp -q Dockerfile "$REMOTE:$REMOTE_DIR/nelima/Dockerfile"

echo "==> Reconstruction de l'image et redémarrage"
ssh "$REMOTE" "cd $REMOTE_DIR && docker compose build nelima && docker compose up -d nelima"

echo "==> Attente du démarrage"
ssh "$REMOTE" '
  for i in $(seq 1 60); do
    if docker logs --since 5m nelima 2>&1 | grep -qE "Started NelimaApplication|Application run failed"; then break; fi
    sleep 4
  done
  docker logs --since 5m nelima 2>&1 | grep -E "Started NelimaApplication|Application run failed" | tail -1
'

echo "==> Vérification externe"
curl -s -o /dev/null -w "health HTTP %{http_code}\n" --max-time 30 https://api.nelima.ci/actuator/health
