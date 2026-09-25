# Backend CI status

The `Backend CI` workflow runs `mvn verify` with Java 21 for pushes and pull requests targeting `CoupleExpantion`. The GitHub-hosted Linux runner supplies Docker to the repository's PostgreSQL and Redis Testcontainers suites. The workflow is read-only and does not deploy.

This is the execution baseline for roadmap C28, not the complete release gate. Workflow actions are pinned to immutable upstream release commits and checkout credentials are not persisted. Dependency vulnerability scanning, secret scanning, SBOM generation, explicit Flyway validation, test/result retention, and proof of the A/B HTTP authorization matrix still need to be added or verified. No successful run is claimed until the workflow executes on GitHub.
