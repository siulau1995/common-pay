package io.github.commonpay.tenant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a no-op tenant switcher when the host application uses one datasource. */
@Configuration
public class PayTenantConfiguration {

    @Bean
    @ConditionalOnMissingBean(PayTenantDataSourceSwitcher.class)
    public PayTenantDataSourceSwitcher payTenantDataSourceSwitcher() {
        return new PayTenantDataSourceSwitcher() {
            @Override
            public void use(String tenantId) {
                // A single-datasource host does not need to switch context.
            }

            @Override
            public void clear() {
                // A single-datasource host does not need to clear context.
            }
        };
    }
}
