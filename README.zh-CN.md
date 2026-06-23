# common-pay

[English](README.md) | [中文](README.zh-CN.md)

`common-pay` 是一个可嵌入 Spring Boot 应用的通用支付模块，提供支付订单、退款、异步回调、状态流水与定时对账能力。当前内置支付宝适配器，并保留微信及其他渠道的扩展接口。

## 能力

- 统一创建、查询、关闭支付订单
- 支付宝扫码、网页、H5、APP 支付
- 退款申请与退款查询
- 支付/退款异步回调的验签、幂等处理和业务回调
- 支付状态变更记录、渠道调用流水、失败业务回调补偿
- 可选 Redis 分布式锁和定时对账
- 可选的多租户数据源切换扩展点

金额字段均以 **分** 为单位，避免浮点精度问题。

## 安装

```bash
mvn clean install
```

```xml
<dependency>
    <groupId>io.github.commonpay</groupId>
    <artifactId>common-pay</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

模块通过 `META-INF/spring.factories` 自动装配 Mapper、服务和渠道适配器。数据库初始化脚本位于：

- MySQL：`src/main/resources/sql/ddl-mysql.sql`
- 达梦：`src/main/resources/sql/ddl-dm.sql`

## 最小配置

```yaml
common:
  pay:
    web:
      enabled: true # 启用模块自带 /pay REST 接口；仅注入服务时可不启用
    reconcile:
      enabled: true # 开启订单、退款和业务回调补偿任务
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

模块不绑定任何特定的数据源框架。单数据源应用无需额外配置；多租户应用实现 `PayTenantDataSourceSwitcher`，在支付回调与定时对账开始前切换数据源、结束后清理上下文：

```java
@Component
public class ApplicationTenantSwitcher implements PayTenantDataSourceSwitcher {
    @Override
    public void use(String tenantId) {
        // 调用应用自身的数据源路由逻辑
    }

    @Override
    public void clear() {
        // 清理应用自身的数据源路由上下文
    }
}
```

## 业务回调

业务系统实现 `PayBusinessHandler` 并注册为 Spring Bean。模块在支付成功、关单、过期和退款成功后调用对应方法；成功回调失败时，可由定时补偿任务重试。

```java
@Component
public class OrderPayHandler implements PayBusinessHandler {
    @Override
    public boolean supports(String bizType) {
        return "ORDER".equals(bizType);
    }

    @Override
    public void onPaySuccess(PaySuccessContext context) {
        // 根据 context.getRefNo() 更新业务订单
    }
}
```

## 渠道扩展

实现 `PayChannelAdapter` 并注册为 Spring Bean，即可接入新的支付渠道。渠道适配器负责预下单、关单、退款、查询、回调验签和渠道状态映射；订单状态、幂等控制和业务回调仍由模块统一处理。

## 运行要求

- Java 8+
- Spring Boot 2.7.x
- MyBatis-Plus 3.5.x
- Redis（使用回调锁与集群对账时需要）
- 接入方提供数据库 DataSource 与事务管理器

## 发布前检查

本仓库已移除对原宿主平台的直接 Maven 依赖、实体基类、Mapper 基类、ID 工具、统一响应和租户切换实现。发布前仍应由代码权利人核验全部源文件、第三方 SDK 许可证及拟采用的开源许可证。
