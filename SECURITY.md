# Security Policy

## Supported versions

Security fixes are applied to the latest published release and the `main` branch.

| Version | Supported |
| --- | --- |
| 0.2.x | Yes |
| 0.1.x and earlier | No |

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability.

Use GitHub's private vulnerability reporting feature:

1. Open the repository's **Security** tab.
2. Select **Advisories** and then **Report a vulnerability**.
3. Include affected versions, reproduction steps, impact, and a proposed fix when available.

Reports involving signature verification, callback replay, authorization, tenant isolation, payment idempotency, refunds, dependency vulnerabilities, or credential exposure are especially important. The project runs CodeQL, Dependabot, dependency review, SpotBugs/FindSecBugs, and CycloneDX SBOM generation in its public automation; reports remain welcome even when an automated check passes.

The maintainer will acknowledge a complete report within seven days and coordinate disclosure after a fix is available. Please never include real merchant credentials, private keys, customer information, or production payment data in a report.
