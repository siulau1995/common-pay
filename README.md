# common-pay

[![CI](https://github.com/siulau1995/common-pay/actions/workflows/ci.yml/badge.svg)](https://github.com/siulau1995/common-pay/actions/workflows/ci.yml)
[![Security](https://github.com/siulau1995/common-pay/actions/workflows/security.yml/badge.svg)](https://github.com/siulau1995/common-pay/actions/workflows/security.yml)
[![Release](https://img.shields.io/github/v/release/siulau1995/common-pay)](https://github.com/siulau1995/common-pay/releases)
[![License](https://img.shields.io/github/license/siulau1995/common-pay)](LICENSE)

[English](README.md) | [中文](README.zh-CN.md)

`common-pay` is a reusable Spring Boot payment module for payment orders, refunds, asynchronous callbacks, status history, and scheduled reconciliation. It ships with an Alipay adapter and provider extension points for application-specific channels.

## Features

- Create, query, and close payment orders
- Alipay native QR, desktop web, H5, and app payments
- Refund creation and refund queries
- Verified, idempotent payment and refund callbacks
- Redis-backed state locks and unique provider notification identifiers
- Status history, provider call records, and failed business callback retry
- Optional tenant datasource switching integration
- MySQL and Dameng schemas, source JAR, Javadoc JAR, and CycloneDX SBOM

All monetary values are stored in **cents** to avoid floating-point errors.

## Security model

- Provider adapters own signature verification and provider status mapping.
- A callback must pass signature, channel, and amount validation before it changes an order or refund.
- Redis serializes state changes. A repeated provider notification is acknowledged without repeating the business transition.
- Credentials are supplied through channel configuration and excluded from generated `toString()` output.
- The built-in adapter is **Alipay only**. Other channels must be supplied by the host application through `PayChannelAdapter`; there is no permissive placeholder adapter.

Report vulnerabilities through [SECURITY.md](SECURITY.md). Do not include production credentials or customer data in issues.

## Install

### Local build

```bash
mvn clean install
```

### GitHub Packages

Releases are published to GitHub Packages. Configure a GitHub token with `read:packages` in Maven `settings.xml`, then add the repository:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/siulau1995/common-pay</url>
    </repository>
</repositories>

<dependency>
    <groupId>io.github.commonpay</groupId>
    <artifactId>common-pay</artifactId>
    <version>0.2.0</version>
</dependency>
```

The module is auto-configured through `META-INF/spring.factories`. Database DDL is available at:

- MySQL: `src/main/resources/sql/ddl-mysql.sql`
- Dameng: `src/main/resources/sql/ddl-dm.sql`

## Runnable demo

[`examples/spring-boot-demo`](examples/spring-boot-demo) starts a synthetic payment provider with H2 and Redis. It creates an order, accepts a deliberately synthetic signed callback, demonstrates replay-safe callback handling, and supports query/refund flows. It contains no real provider credentials.

```bash
cd examples/spring-boot-demo
docker compose up --build
```

## Minimal configuration

```yaml
common:
  pay:
    web:
      enabled: true # Enables the built-in /pay REST controller.
    reconcile:
      enabled: true
      tenant-ids: tenant-a,tenant-b
      pay-fixed-delay: 300000
      refund-fixed-delay: 300000
      business-retry-fixed-delay: 120000
```

Provider credentials are stored in `base_pay_channel_config`, not hard-coded. Example Alipay `config_json`:

```json
{
  "gatewayUrl": "https://openapi.alipay.com/gateway.do",
  "appPrivateKey": "your application private key",
  "alipayPublicKey": "Alipay public key",
  "signType": "RSA2",
  "charset": "UTF-8",
  "format": "json"
}
```

For sandbox testing, use the Alipay sandbox gateway URL and sandbox keys.

## Multi-tenant integration

The module deliberately does not depend on a particular dynamic datasource library. A single-datasource application needs no extra setup. A multi-tenant host can implement `PayTenantDataSourceSwitcher` to switch and clear its own datasource context around callbacks and scheduled reconciliation.

```java
@Component
public class ApplicationTenantSwitcher implements PayTenantDataSourceSwitcher {
    @Override
    public void use(String tenantId) {
        // Use the host application's datasource routing mechanism.
    }

    @Override
    public void clear() {
        // Clear the host application's datasource routing context.
    }
}
```

## Business callbacks and provider extensions

Implement `PayBusinessHandler` as a Spring bean for payment lifecycle integration. Implement `PayChannelAdapter` as a Spring bean to add a provider. The adapter owns provider API calls and signature verification; the module owns order lifecycle, validation, locking, idempotency, and business callbacks.

## Requirements

- Java 8+
- Spring Boot 2.7.x
- MyBatis-Plus 3.5.x
- Redis for distributed callback locking and clustered reconciliation
- A host-provided `DataSource` and transaction manager

## License and notices

Licensed under [Apache License 2.0](LICENSE). Direct dependency notices are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); each release also attaches a CycloneDX SBOM. Contributions are accepted under Apache-2.0 as described in [CONTRIBUTING.md](CONTRIBUTING.md).
