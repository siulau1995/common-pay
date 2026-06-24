# common-pay

[![CI](https://github.com/siulau1995/common-pay/actions/workflows/ci.yml/badge.svg)](https://github.com/siulau1995/common-pay/actions/workflows/ci.yml)
[![Security](https://github.com/siulau1995/common-pay/actions/workflows/security.yml/badge.svg)](https://github.com/siulau1995/common-pay/actions/workflows/security.yml)
[![Release](https://img.shields.io/github/v/release/siulau1995/common-pay)](https://github.com/siulau1995/common-pay/releases)
[![License](https://img.shields.io/github/license/siulau1995/common-pay)](LICENSE)

[English](README.md) | [中文](README.zh-CN.md)

`common-pay` 是一个可嵌入 Spring Boot 应用的通用支付模块，提供支付订单、退款、异步回调、状态流水与定时对账能力。内置支付宝适配器，并提供宿主应用自行扩展支付渠道的接口。

## 能力

- 统一创建、查询、关闭支付订单
- 支付宝扫码、网页、H5、APP 支付
- 退款申请与退款查询
- 支付/退款异步回调验签与幂等处理
- Redis 状态锁与渠道通知号唯一约束
- 支付状态变更记录、渠道调用流水、失败业务回调补偿
- 可选的多租户数据源切换扩展点
- MySQL、达梦初始化脚本，以及源码包、Javadoc 包和 CycloneDX SBOM

金额字段均以 **分** 为单位，避免浮点精度问题。

## 安全设计

- 支付渠道适配器负责验签和渠道状态映射。
- 回调必须通过验签、渠道和金额校验后，才会改变订单或退款状态。
- Redis 串行化状态变更；同一渠道通知重复到达时会正常确认，但不会重复执行业务状态迁移。
- 渠道密钥保存在渠道配置中，并从自动生成的 `toString()` 中排除。
- 当前仅内置 **支付宝** 适配器。微信或其他渠道必须由宿主应用实现 `PayChannelAdapter` 接入，不提供可误用的占位适配器。

安全问题请按 [SECURITY.md](SECURITY.md) 提交，请勿在 Issue 中提交生产密钥或客户数据。

## 安装

### 本地构建

```bash
mvn clean install
```

### GitHub Packages

发行版本会发布到 GitHub Packages。在 Maven `settings.xml` 中配置带有 `read:packages` 权限的 GitHub Token 后，加入仓库：

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

模块通过 `META-INF/spring.factories` 自动装配。数据库初始化脚本位于：

- MySQL：`src/main/resources/sql/ddl-mysql.sql`
- 达梦：`src/main/resources/sql/ddl-dm.sql`

## 可运行示例

[`examples/spring-boot-demo`](examples/spring-boot-demo) 使用 H2、Redis 和合成支付渠道启动完整示例，覆盖创建订单、合成验签回调、重复回调幂等和查询/退款流程。示例不包含任何真实支付凭证。

```bash
cd examples/spring-boot-demo
docker compose up --build
```

## 最小配置

```yaml
common:
  pay:
    web:
      enabled: true # 启用模块自带 /pay REST 接口
    reconcile:
      enabled: true
      tenant-ids: tenant-a,tenant-b
      pay-fixed-delay: 300000
      refund-fixed-delay: 300000
      business-retry-fixed-delay: 120000
```

支付渠道配置不写死在代码中，保存在 `base_pay_channel_config` 表。支付宝 `config_json` 示例：

```json
{
  "gatewayUrl": "https://openapi.alipay.com/gateway.do",
  "appPrivateKey": "应用私钥",
  "alipayPublicKey": "支付宝公钥",
  "signType": "RSA2",
  "charset": "UTF-8",
  "format": "json"
}
```

沙箱环境只需将 `gatewayUrl` 改为支付宝沙箱网关地址，并填写相应沙箱密钥。

## 多租户接入

模块不绑定任何特定的数据源框架。单数据源应用无需额外配置；多租户应用实现 `PayTenantDataSourceSwitcher`，在支付回调与定时对账开始前切换数据源、结束后清理上下文。

## 业务回调与渠道扩展

业务系统实现 `PayBusinessHandler` 并注册为 Spring Bean，用于接入支付生命周期。实现 `PayChannelAdapter` 并注册为 Spring Bean，即可接入新的支付渠道。渠道适配器负责渠道 API 调用与验签；订单生命周期、校验、锁、幂等控制和业务回调由模块统一处理。

## 运行要求

- Java 8+
- Spring Boot 2.7.x
- MyBatis-Plus 3.5.x
- Redis（使用分布式回调锁与集群对账时需要）
- 接入方提供数据库 DataSource 与事务管理器

## 许可证与声明

项目采用 [Apache License 2.0](LICENSE)。直接依赖许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，每个发行版本还会附带 CycloneDX SBOM；贡献规则见 [CONTRIBUTING.md](CONTRIBUTING.md)。
