# Initial Flyway adoption

This runbook covers the one-time adoption of Flyway on the existing production database. It does
not replace the full Multi-AZ rollout runbook.

## Preconditions

1. Put the application in maintenance mode and drain both EC2 instances from the ALB.
2. Create and verify a manual RDS snapshot.
3. Compare the production schema with `V1__baseline_schema.sql`, including UUID types, charset,
   collation, primary keys and indexes. Resolve every difference before continuing.
4. Confirm that the application artifact contains V1 and V2 and uses Hibernate `validate`.

## First migration

Set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` only for the first startup against the existing,
non-empty schema. Flyway records baseline version 1 and then executes V2.

After startup, verify:

- `flyway_schema_history` contains the baseline at version 1 and a successful V2;
- notification counts before and after migration match the expected cleanup and backfill;
- `uk_notificacoes_item_id` exists;
- `fk_notificacoes_item_id` references `itens(id)`;
- Hibernate schema validation succeeds.

## Mandatory follow-up

Remove `SPRING_FLYWAY_BASELINE_ON_MIGRATE` or set it to `false` before the next deployment. Keep
`spring.jpa.hibernate.ddl-auto=validate`; never return production to `update`.

Application rollback may use the prior artifact. Schema rollback requires the RDS snapshot or a
reviewed corrective migration. Never delete or rewrite `flyway_schema_history`.
