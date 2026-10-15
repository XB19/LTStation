package com.ltstation.center.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Refuse les appels /api/** sans session, sauf ceux marqués {@link PublicApi}. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        if (hm.hasMethodAnnotation(PublicApi.class) || hm.getBeanType().isAnnotationPresent(PublicApi.class)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionUser.SESSION_KEY) != null) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Session expirée, veuillez vous reconnecter\"}");
        return false;
    }
}
