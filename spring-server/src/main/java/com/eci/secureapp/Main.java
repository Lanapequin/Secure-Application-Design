package com.eci.secureapp;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.filter.DelegatingFilterProxy;
import com.eci.secureapp.config.AppConfig;
import com.eci.secureapp.config.SecurityConfig;

import javax.servlet.DispatcherType;
import java.util.EnumSet;

/**
 * Main entry point. Starts an embedded Jetty server with HTTPS (TLS).
 * Port is read from the PORT environment variable (12-factor app principle).
 * Keystore path and password are read from environment variables.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = getPort();
        String keystorePath = getEnv("KEYSTORE_PATH", "src/main/resources/keystore/loginkeystore.p12");
        String keystorePassword = getEnv("KEYSTORE_PASSWORD", "changeit");
        String keystoreAlias = getEnv("KEYSTORE_ALIAS", "loginkeypair");

        // ---- Jetty Server ----
        Server server = new Server();

        // SSL Context Factory
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(keystorePath);
        sslContextFactory.setKeyStorePassword(keystorePassword);
        sslContextFactory.setKeyStoreType("PKCS12");
        sslContextFactory.setCertAlias(keystoreAlias);
        sslContextFactory.setProtocol("TLS");

        // HTTPS Connector
        HttpConfiguration httpsConfig = new HttpConfiguration();
        httpsConfig.setSecureScheme("https");
        httpsConfig.setSecurePort(port);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        ServerConnector httpsConnector = new ServerConnector(
                server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(httpsConfig)
        );
        httpsConnector.setPort(port);
        server.addConnector(httpsConnector);

        // ---- Spring MVC Context ----
        AnnotationConfigWebApplicationContext springContext = new AnnotationConfigWebApplicationContext();
        springContext.register(AppConfig.class, SecurityConfig.class);

        // Servlet Context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Spring ContextLoaderListener
        context.addEventListener(new ContextLoaderListener(springContext));

        // Spring Security filter
        context.addFilter(
                new FilterHolder(new DelegatingFilterProxy("springSecurityFilterChain")),
                "/*",
                EnumSet.allOf(DispatcherType.class)
        );

        // CORS filter
        FilterHolder corsFilter = new FilterHolder(new com.eci.secureapp.config.CorsFilter());
        context.addFilter(corsFilter, "/*", EnumSet.allOf(DispatcherType.class));

        // DispatcherServlet
        DispatcherServlet dispatcherServlet = new DispatcherServlet(springContext);
        ServletHolder servletHolder = new ServletHolder("dispatcher", dispatcherServlet);
        servletHolder.setInitOrder(1);
        context.addServlet(servletHolder, "/*");

        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(context);
        server.setHandler(handlers);

        System.out.println("=================================================");
        System.out.println("  Login Service starting on HTTPS port: " + port);
        System.out.println("  Keystore: " + keystorePath);
        System.out.println("=================================================");

        server.start();
        server.join();
    }

    private static int getPort() {
        String port = System.getenv("PORT");
        if (port != null && !port.isEmpty()) {
            return Integer.parseInt(port);
        }
        return 5000;
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
