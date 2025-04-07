package me.sonam.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * This is to inject the request from a Spring servlet filter into a context.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RequestContextFilter> requestContextFilter() {
        FilterRegistrationBean<RequestContextFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RequestContextFilter());
        registrationBean.addUrlPatterns("/*"); // or specific URL patterns
        registrationBean.setName("requestContextFilter");
        registrationBean.setOrder(1); // Define the order of the filter
        return registrationBean;
    }
}