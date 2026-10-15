package com.ltstation.center.config;

import com.ltstation.center.security.AuthInterceptor;
import com.ltstation.center.security.CurrentUserResolver;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CurrentUserResolver currentUserResolver;
    private final String[] corsOrigins;
    private final String uploadDir;

    public WebConfig(AuthInterceptor authInterceptor, CurrentUserResolver currentUserResolver,
                     @Value("${app.cors-origins}") String corsOrigins, @Value("${app.upload-dir}") String uploadDir) {
        this.authInterceptor = authInterceptor;
        this.currentUserResolver = currentUserResolver;
        this.corsOrigins = corsOrigins.split(",");
        this.uploadDir = uploadDir;
    }

    /** Images envoyées servies sous /img/** (publicités, logos), comme le dossier ~/img de l'original. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = java.nio.file.Path.of(uploadDir).toAbsolutePath().normalize().resolve("img").toUri().toString();
        registry.addResourceHandler("/img/**").addResourceLocations(location.endsWith("/") ? location : location + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(corsOrigins).allowCredentials(true)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
