#!/usr/bin/env bash
set -euo pipefail
# This script creates disposable fixture databases; never run it against a deployment.
[[ "${GITHUB_ACTIONS:-}" == "true" ]] || { echo 'Run this smoke test in GitHub Actions only.' >&2; exit 1; }
: "${COMPOSE_PROJECT_NAME:?CI Compose project name is required}"

mysql_app() {
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -h 127.0.0.1 -u stockflow -N -B "$@"' sh "$@"
}
mysql_root() {
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -u root -N -B "$@"' sh "$@"
}

docker compose up -d --wait --wait-timeout 240
curl --fail --silent http://localhost:8080/api/health/ready | jq -e '.status == "UP"'
curl --fail --silent http://localhost:8080/api/version | jq -e '.version == "1.3.0" and .service == "stockflow-backend" and .builtAt != "unknown"'
[[ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/products)" == 401 ]]
[[ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/v3/api-docs)" == 404 ]]
[[ "$(mysql_app stockflow_db -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IN ('1','2')")" == 2 ]]

# Exercise both supported adoption paths against real MySQL, using only CI databases.
for release in v1 v11; do
  database="stockflow_upgrade_$release"
  mysql_root -e "CREATE DATABASE $database; GRANT ALL ON $database.* TO 'stockflow'@'%';"
  mysql_app "$database" < stockflow-backend/src/main/resources/db/migration/V1__initial_schema.sql
  mysql_app "$database" < stockflow-backend/src/test/resources/db/fixtures/v1_data.sql
  expected_active=1
  if [[ "$release" == v11 ]]; then
    mysql_app "$database" -e 'ALTER TABLE app_users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE; UPDATE app_users SET active=FALSE WHERE id=1;'
    expected_active=0
  fi
  preserved_user=$(mysql_app "$database" -e 'SELECT id,username,password_hash,role,created_at,updated_at,version FROM app_users ORDER BY id')
  preserved_data=$(mysql_app "$database" -e 'CHECKSUM TABLE categories, suppliers, customers, products, inventory_transactions, purchase_orders, purchase_order_items, sales_orders, sales_order_items, stock_documents, stock_document_lines EXTENDED')
  container="$COMPOSE_PROJECT_NAME-upgrade-$release"
  docker compose run --rm -d --no-deps --name "$container" \
    -e "SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/$database" \
    -e FLYWAY_BASELINE_ON_MIGRATE=true backend
  healthy=false
  for attempt in {1..60}; do
    if [[ "$(docker inspect -f '{{.State.Health.Status}}' "$container")" == healthy ]]; then
      healthy=true; break
    fi
    sleep 3
  done
  if [[ "$healthy" != true ]]; then docker logs "$container"; exit 1; fi
  [[ "$(mysql_app "$database" -e 'SELECT active FROM app_users WHERE id=1')" == "$expected_active" ]]
  [[ "$(mysql_app "$database" -e 'SELECT id,username,password_hash,role,created_at,updated_at,version FROM app_users ORDER BY id')" == "$preserved_user" ]]
  [[ "$(mysql_app "$database" -e 'CHECKSUM TABLE categories, suppliers, customers, products, inventory_transactions, purchase_orders, purchase_order_items, sales_orders, sales_order_items, stock_documents, stock_document_lines EXTENDED')" == "$preserved_data" ]]
  [[ "$(mysql_app "$database" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='2'")" == 1 ]]
  docker stop "$container"
done

# A regular restart must leave migration history intact.
docker compose restart backend
docker compose up -d --wait --wait-timeout 180
[[ "$(mysql_app stockflow_db -e 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1')" == 2 ]]
echo 'Docker smoke passed: fresh schema, V1/V1.1 adoption, preserved data, readiness, version, and API protection.'
