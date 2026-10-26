#!/usr/bin/env sh
# Despliega un tag de la imagen de la API en el servidor. Lo invoca el workflow de deploy,
# y sirve para rollback manual: sh deploy.sh <sha-anterior>
set -eu

TAG="${1:?uso: sh deploy.sh <tag-de-imagen>}"
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "Falta .env en $(pwd). Crearlo a partir de .env.example." >&2
  exit 1
fi

compose() {
  docker compose --env-file .env --env-file image.env -f docker-compose.prod.yml "$@"
}

health_ok() {
  port="$(sed -n 's/^API_PORT=//p' .env)"
  for _ in $(seq 1 36); do
    if curl -fsS "http://127.0.0.1:${port:-8080}/actuator/health" > /dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  return 1
}

PREVIOUS_TAG="$(sed -n 's/^IMAGE_TAG=//p' image.env 2>/dev/null || true)"

printf 'IMAGE_TAG=%s\n' "$TAG" > image.env
compose pull api
compose up -d --remove-orphans

if health_ok; then
  echo "Deploy OK: $TAG"
  docker image prune -f > /dev/null
  exit 0
fi

echo "La API no respondió healthy con $TAG. Logs:" >&2
compose logs --tail=100 api >&2

# Rollback a la imagen anterior. Ojo: las migraciones de Flyway ya aplicadas no se revierten.
if [ -n "$PREVIOUS_TAG" ] && [ "$PREVIOUS_TAG" != "$TAG" ]; then
  echo "Rollback a $PREVIOUS_TAG" >&2
  printf 'IMAGE_TAG=%s\n' "$PREVIOUS_TAG" > image.env
  compose up -d --remove-orphans
fi
exit 1
