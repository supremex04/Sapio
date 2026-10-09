# 0005. Backend as a modular monolith, package-by-feature

- Status: Accepted
- Date: 2026-10-09

## Context

The roadmap has many domains (users, classes, assignments, submissions, exams, analytics).
Microservices per domain would be heavy for a small team; one big layered codebase
(`controllers/`, `services/`…) gets tangled as it grows.

## Decision

One Spring Boot application. Code is grouped by feature (`com.ved.auth`, `com.ved.document`, …),
each feature exposing a small public surface (services, events, DTOs) and keeping the rest
package-private. External systems sit behind ports (interfaces) inside the feature.

## Consequences

- One deployable, one database, simple transactions.
- Clear seams: a feature can later be extracted into its own service if it really needs to scale
  separately.
- Discipline is needed to keep features from reaching into each other's internals. Spring
  Modulith can enforce this once there are more modules.
