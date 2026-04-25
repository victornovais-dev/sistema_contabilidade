# Observability

## Local stack

Start the application on port `8080`, then run:

```powershell
docker compose -f observability/docker-compose.yml up -d
```

Open:

```text
http://localhost:3001
```

Default local credentials:

```text
admin / admin
```

Set `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` before starting Docker Compose to override
the local defaults.

## Query count dashboard

Grafana dashboard file:

```text
observability/grafana/dashboards/query-count-dashboard.json
```

Dashboard provider:

```text
observability/grafana/provisioning/dashboards/dashboards.yaml
```

Datasource provider:

```text
observability/grafana/provisioning/datasources/prometheus.yaml
```

The dashboard shows:

- worst SQL query count in the selected time range
- top routes by maximum query count
- requests per second by route
- average queries per request by route
- route ranking table
- routes above the `15` query threshold

## Query count alerts

The application exports per-route SQL query counts through Micrometer at:

```text
/actuator/prometheus
```

Prometheus rule file:

```text
observability/prometheus/rules/query-count-alerts.yml
```

Grafana alert provisioning file:

```text
observability/grafana/provisioning/alerting/query-count-alerts.yaml
```

The alert fires when any API route records more than 15 SQL queries in a request within the last
5 minutes. The metric is tagged by `method` and `uri`.

For Grafana provisioning, set `PROMETHEUS_DS_UID` to the UID of the Prometheus datasource.

## Prometheus scrape config

Local Prometheus config:

```text
observability/prometheus/prometheus.yml
```

It scrapes:

```text
host.docker.internal:8080/actuator/prometheus
```
