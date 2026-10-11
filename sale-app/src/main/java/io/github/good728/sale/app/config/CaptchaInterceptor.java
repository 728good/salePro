package io.github.good728.sale.app.config;

import io.github.good728.sale.app.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class CaptchaInterceptor implements HandlerInterceptor {
    private final CaptchaService captchaService;

    public CaptchaInterceptor(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        boolean validTicket = captchaService.consumeGatewayTicket(request.getHeader("X-Sale-Gateway-Ticket"));
        boolean validDirect = captchaService.verify(
                request.getHeader("X-Captcha-Token"), request.getHeader("X-Captcha-Code"));
        if (validTicket || validDirect) return true;
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"验证码无效或已使用\"}");
        return false;
    }
}
