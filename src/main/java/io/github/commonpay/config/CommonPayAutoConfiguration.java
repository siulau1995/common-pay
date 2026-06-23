package io.github.commonpay.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the payment module for Spring Boot applications that include the library.
 */
@Configuration
@ComponentScan(basePackages = "io.github.commonpay")
@MapperScan(basePackages = "io.github.commonpay.mapper")
public class CommonPayAutoConfiguration {
}
