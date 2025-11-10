package com.vtoroy.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val rateLimitingInterceptor: RateLimitingInterceptor
) : WebMvcConfigurer {

    /**
     * Configure view controllers for serving the frontend
     * This ensures that the React/SPA routes are handled properly
     */
    override fun addViewControllers(registry: ViewControllerRegistry) {
        // Serve index.html for all non-API routes to support SPA routing
        registry.addViewController("/").setViewName("forward:/index.html")
        registry.addViewController("/chat").setViewName("forward:/index.html")
        registry.addViewController("/knowledge").setViewName("forward:/index.html")
    }

    /**
     * Register rate limiting interceptor
     */
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(rateLimitingInterceptor)
            .addPathPatterns("/api/**")
    }
}