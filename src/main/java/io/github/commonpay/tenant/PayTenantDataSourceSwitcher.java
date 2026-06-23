package io.github.commonpay.tenant;

/**
 * Optional integration point for host applications that route payment data by tenant.
 *
 * <p>Implement this interface with the datasource-routing mechanism used by the host
 * application. Single-datasource applications can use the supplied no-op default.</p>
 */
public interface PayTenantDataSourceSwitcher {

    void use(String tenantId);

    void clear();
}
