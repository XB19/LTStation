package com.ltstation.center.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pages et fichiers à nom fixe : le navigateur revalide à chaque fois, pour afficher une nouvelle version dès son installation.
 * Les fichiers de /assets portent une empreinte dans leur nom et n'en ont pas besoin.
 */
@Component
public class RevalidateFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod()) || request.getRequestURI().startsWith("/assets/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-cache");
        chain.doFilter(request, response);
    }
}
