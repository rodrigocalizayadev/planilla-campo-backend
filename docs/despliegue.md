# Despliegue

## Flujo

| Evento | Workflow | Qué hace |
|---|---|---|
| PR hacia `dev` o `master` | `ci.yml` | `./mvnw verify` (incluye Testcontainers) |
| Merge (push) a `master` | `deploy.yml` | `verify` → build y push de la imagen a GHCR → deploy por SSH |

La imagen se publica con tres tags: `:<sha>`, `:<version del pom.xml>` y `:latest`.
En el servidor siempre se despliega por SHA, nunca `latest`.

- **Versión**: sale de `<version>` en `pom.xml`. Una versión de release ya publicada no se
  puede republicar (el workflow falla); una `-SNAPSHOT` sí.
- **Retención**: tras cada deploy sano se conservan solo la imagen actual y la anterior,
  tanto en GHCR como en el servidor. El rollback es solo a la versión inmediata anterior.

## Configuración inicial (una sola vez)

### 1. Servidor

```bash
# Usuario de deploy con acceso a Docker
sudo adduser --disabled-password deploy
sudo usermod -aG docker deploy

# Directorio del stack y secretos
sudo -u deploy mkdir -p /home/deploy/planilla-campo
sudo -u deploy nano /home/deploy/planilla-campo/.env   # completar a partir de deploy/.env.example
sudo chmod 600 /home/deploy/planilla-campo/.env
```

`curl` tiene que estar instalado: `deploy.sh` lo usa para verificar `/actuator/health`.

### 2. Clave SSH dedicada al deploy

En tu máquina (no reutilizar tu clave personal):

```bash
ssh-keygen -t ed25519 -f deploy_key -N "" -C "github-actions-planilla-campo"
ssh-keyscan -t ed25519 <host>   # salida para SSH_KNOWN_HOSTS
```

Instalar la clave pública para el usuario `deploy` desde la sesión de root (no se puede usar
`ssh-copy-id deploy@<host>`: `deploy` no tiene contraseña con la que autenticarse):

```bash
# En el servidor, como root. Pegar el contenido de deploy_key.pub entre las comillas.
sudo -u deploy mkdir -p -m 700 /home/deploy/.ssh
echo "ssh-ed25519 AAAA... github-actions-planilla-campo" | sudo -u deploy tee -a /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys

# Fingerprint real del host: debe coincidir con la salida de ssh-keyscan
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

Probar desde tu máquina: `ssh -i deploy_key deploy@<host> docker ps`.

### 3. GitHub

En *Settings → Environments* crear el environment `production` y cargar estos secrets:

| Secret | Valor |
|---|---|
| `SSH_HOST` | IP o dominio del servidor |
| `SSH_USER` | `deploy` |
| `SSH_PRIVATE_KEY` | contenido de `deploy_key` |
| `SSH_KNOWN_HOSTS` | salida de `ssh-keyscan` |

Recomendado: en *Settings → Branches* proteger `master` exigiendo que pase el check `verify`
antes de mergear.

## Operación

```bash
# Rollback manual a un SHA anterior (la imagen ya está en el servidor)
cd ~/planilla-campo && sh deploy.sh <sha>

# Logs
docker compose --env-file .env --env-file image.env -f docker-compose.prod.yml logs -f api
```

Si el health check falla tras un deploy, `deploy.sh` vuelve solo a la imagen anterior.
**Las migraciones de Flyway no se revierten**: una migración que rompa la versión anterior
deja el rollback inservible. Las migraciones tienen que ser compatibles hacia atrás.

## Backups

El volumen `mysql_data` es la única copia de los datos. Hasta tener backups automáticos:

```bash
docker compose --env-file .env --env-file image.env -f docker-compose.prod.yml \
  exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction planilla_campo' \
  > backup-$(date +%F).sql
```
