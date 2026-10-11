package io.github.good728.sale.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final CaptchaInterceptor captchaInterceptor;

    public WebConfig(CaptchaInterceptor captchaInterceptor) {
        this.captchaInterceptor = captchaInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(captchaInterceptor).addPathPatterns("/api/orders/reserve");
    }
}
