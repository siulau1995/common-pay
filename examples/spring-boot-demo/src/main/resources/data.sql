INSERT INTO base_pay_channel_config (
    f_id, f_creator_time, f_delete_mark, f_pay_app_code, f_pay_app_name,
    f_channel_code, f_merchant_id, f_app_id, f_environment,
    f_notify_url, f_refund_notify_url, f_config_json, f_enabled_mark, f_sort_code
) VALUES (
    'demo-channel', CURRENT_TIMESTAMP, 0, 'DEMO_PAY', 'Synthetic demo provider',
    'WECHAT', 'DEMO_MERCHANT', 'DEMO_APP', 'DEMO',
    'http://localhost:8080/pay/notify/default/WECHAT/DEMO_PAY',
    'http://localhost:8080/pay/refund/notify/default/WECHAT/DEMO_PAY',
    '{}', 1, 1
);
