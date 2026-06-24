# Spring Boot demo

This example runs `common-pay` with an in-memory H2 database, Redis, and a synthetic payment provider. It contains no real provider credentials and must not be used as a production payment channel.

## Start

From this directory:

```bash
docker compose up --build
```

## Create an order

```bash
curl -sS http://localhost:8080/pay/order/create \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId":"default",
    "bizType":"DEMO_ORDER",
    "refTable":"demo_order",
    "refValue":"1001",
    "refNo":"DEMO-1001",
    "channelCode":"WECHAT",
    "payScene":"NATIVE",
    "payAppCode":"DEMO_PAY",
    "subject":"Synthetic order",
    "totalAmount":1200
  }'
```

Keep the returned `payOrderNo`, then simulate a signed provider callback. `total_amount` is expressed in yuan by the mock provider, while API request amounts are expressed in cents.

```bash
curl -sS -X POST 'http://localhost:8080/pay/notify/default/WECHAT/DEMO_PAY' \
  --data-urlencode 'out_trade_no=PAY_ORDER_NO' \
  --data-urlencode 'total_amount=12.00' \
  --data-urlencode 'trade_no=MOCK-TRADE-1001' \
  --data-urlencode 'notify_id=MOCK-NOTIFY-1001' \
  --data-urlencode 'trade_status=TRADE_SUCCESS' \
  --data-urlencode 'demo_signature=demo-signature'
```

Query the order:

```bash
curl -sS http://localhost:8080/pay/order/query \
  -H 'Content-Type: application/json' \
  -d '{"payOrderNo":"PAY_ORDER_NO"}'
```

The callback can be sent repeatedly; only the first valid callback changes the order and invokes the business handler.
