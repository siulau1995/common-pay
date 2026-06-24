# Changelog

All notable changes to this project will be documented in this file.

## [0.2.0] - 2026-06-23

### Security

- Replaced Fastjson 1.x with fastjson2.
- Removed the permissive unimplemented WeChat adapter; unsupported channel operations now fail explicitly.
- Added Redis owner-token unlock handling and transaction boundaries for locked state changes.
- Added channel, Alipay application, merchant, amount, refund-detail, and retry idempotency validation.
- Made duplicate provider notifications acknowledge safely without repeated state transitions.

### Added

- Signature, callback tampering, duplicate callback, cross-channel, refund, tenant cleanup, lock ownership, and credential-redaction tests.
- CodeQL, Dependabot, dependency review, SpotBugs/FindSecBugs, CycloneDX SBOM, source/Javadoc artifacts, and GitHub Packages release automation.
- A runnable H2 + Redis + mock-provider Spring Boot example.
- Third-party dependency notices and a project NOTICE file.

## [0.1.0] - 2026-06-23

### Added

- Unified payment order, query, close, and refund services.
- Alipay native QR, desktop page, H5, and app payment adapter.
- Extensible payment channel and business callback interfaces.
- Idempotent callback processing, status history, reconciliation, and retry jobs.
- MySQL and Dameng database schemas.
- Optional REST endpoints and tenant datasource switching integration.
- Continuous integration, unit tests, security policy, and contribution guide.
