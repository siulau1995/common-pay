# Contributing to common-pay

Thank you for helping improve common-pay.

## Development setup

Requirements:

- JDK 8 or newer
- Maven 3.6 or newer

Build and run the test suite:

```bash
mvn clean verify
```

## Making a change

1. Fork the repository and create a focused branch from `main`.
2. Keep payment-provider details behind `PayChannelAdapter`.
3. Add or update tests for every behavior change.
4. Run `mvn clean verify` locally.
5. Open a pull request describing the problem, approach, and verification performed.

Small, focused pull requests are preferred. Avoid unrelated formatting or dependency changes.

## Security and test data

Never commit API keys, application private keys, merchant secrets, access tokens, real callback payloads, customer data, or production transaction identifiers. Use placeholders and synthetic fixtures only.

Security issues must follow [SECURITY.md](SECURITY.md) and must not be disclosed in public issues.

## Commit messages

Use concise imperative messages, for example:

```text
Add callback replay protection
Fix refund amount validation
Document tenant datasource integration
```

By submitting a contribution, you agree that it is licensed under the repository's Apache License 2.0.
