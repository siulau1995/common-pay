# common-pay

[![CI](https://github.com/siulau1995/common-pay/actions/workflows/ci.yml/badge.svg)](https://github.com/siulau1995/common-pay/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/siulau1995/common-pay)](https://github.com/siulau1995/common-pay/releases)
[![License](https://img.shields.io/github/license/siulau1995/common-pay)](LICENSE)

[English](README.md) | [中文](README.zh-CN.md)

`common-pay` is a reusable Spring Boot payment module providing payment orders, refunds, asynchronous callbacks, status history, and scheduled reconciliation. It includes an Alipay adapter and extension points for WeChat Pay or other providers.

## Features

- Create, query, and close payment orders
- Alipay native QR, desktop web, H5, and app payments
- Refund creation and refund queries
- Verified, idempotent payment and refund callbacks
- Status history, provider call records, and failed business callback retry
- Optional Redis distributed lock and reconciliation jobs
- Optional tenant datasource switching integration

All monetary values are stored in **cents** to avoid floating-point errors.

## Install

```bash
mvn clean install
```

```xml
<dependency>
    <groupId>io.github.commonpay</groupId>
    <artifactId>common-pay</artifactId>
    <version>0.1.0</version>
</dependency>
```

The module is auto-configured through `META-INF/spring.factories`. Database DDL is available at:

- MySQL: `src/main/resources/sql/ddl-mysql.sql`
- Dameng: `src/main/resources/sql/ddl-dm.sql`

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

## Business callbacks

Implement `PayBusinessHandler` as a Spring bean. The module calls it after payment success, close, expiration, and refund success. Failed success callbacks can be retried by the scheduled job.

## Extending providers

Implement `PayChannelAdapter` and register it as a Spring bean to add a provider. The adapter owns provider API calls, signature verification, and provider status mapping; the module keeps order lifecycle, idempotency, and business callbacks centralized.

## Requirements

- Java 8+
- Spring Boot 2.7.x
- MyBatis-Plus 3.5.x
- Redis for callback locking and clustered reconciliation
- A host-provided `DataSource` and transaction manager

## Before publishing

This repository removes direct dependencies on the original host platform, including its Maven artifacts, entity and mapper base classes, ID utility, response wrapper, and tenant-switching implementation. The code owner should still review all source provenance, third-party SDK licenses, and the repository license before publishing.
