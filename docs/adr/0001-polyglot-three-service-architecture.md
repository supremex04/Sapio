# 0001. Three services: Spring Boot backend, Python inference, React/TS frontend

- Status: Accepted
- Date: 2026-10-09

## Context

The platform combines classic business software (accounts, classes, assignments, grading
records, analytics) with ML features (OCR, AI-generated tasks, personalisation). The Java
ecosystem is strong for the first; ML models and tooling are Python-first.

## Decision

- **Backend** in Java 21 / Spring Boot 4: the only public API and the only owner of data.
- **Inference** in Python 3.12 / FastAPI: stateless, internal, called only by the backend.
- **Frontend** in TypeScript / React (Vite).
- Backend ↔ inference over plain HTTP + JSON with a shared API key.

## Consequences

- ML can use any Python library or model server without affecting business code.
- Business rules, authorisation and persistence live in one place (backend).
- Two runtimes to operate; the HTTP contract must be documented (`docs/specs/inference-api.md`).
- GPU-heavy models can move to dedicated hardware by swapping the engine, with no backend change.
