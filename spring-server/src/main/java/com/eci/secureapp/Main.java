package com.eci.secureapp;

import com.eci.secureapp.config.AppConfig;
import com.eci.secureapp.config.SecurityConfig;
import com.eci.secureapp.config.CorsFilter;
import com.eci.secureapp.config.JwtFilter;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.DispatcherType;
import java.util.EnumSet;

/**
 * Entry point for the Spring server.
 *
 * Reads all configuration from environment variables (12-factor principle III):
 *   PORT             - HTTPS port to listen on        (default: 8443)
 *   KEYSTORE_PATH    - path to PKCS12 keystore file   (default: keystore/serverkeystore.p12)
 *   KEYSTORE_PASS    - keystore password               (default: changeit)
 *   KEYSTORE_ALIAS   - certificate alias               (default: serverkeypair)
 *   TOKEN_SECRET     - secret for signing JWT tokens   (default: dev-secret)
 *   ALLOWED_ORIGIN   - CORS allowed origin             (default: *)
 */
public class Main {

    public static void main(String[] args) throws Exception {

        int    port          = getInt("PORT", 8443);
        String keystorePath  = getStr("KEYSTORE_PATH", "src/main/resources/keystore/serverkeystore.p12");
        String keystorePass  = getStr("KEYSTORE_PASS",  "changeit");
        String keystoreAlias = getStr("KEYSTORE_ALIAS", "serverkeypair");


        SslContextFactory.Server ssl = new SslContextFactory.Server();
        ssl.setKeyStorePath(keystorePath);
        ssl.setKeyStorePassword(keystorePass);
        ssl.setKeyStoreType("PKCS12");
        ssl.setCertAlias(keystoreAlias);
        ssl.setProtocol("TLS");
        ssl.setIncludeProtocols("TLSv1.2", "TLSv1.3");

        HttpConfiguration httpsConfig = new HttpConfiguration();
        httpsConfig.setSecureScheme("https");
        httpsConfig.setSecurePort(port);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        Server server = new Server();
        ServerConnector connector = new ServerConnector(
                server,
                new SslConnectionFactory(ssl, "http/1.1"),
                new HttpConnectionFactory(httpsConfig)
        );
        connector.setPort(port);
        server.addConnector(connector);

        AnnotationConfigWebApplicationContext ctx =
                new AnnotationConfigWebApplicationContext();
        ctx.register(AppConfig.class, SecurityConfig.class);

        ServletContextHandler context =
                new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        context.addEventListener(new ContextLoaderListener(ctx));

        context.addFilter(
                new FilterHolder(new CorsFilter()),
                "/*", EnumSet.allOf(DispatcherType.class));

        context.addFilter(
                new FilterHolder(new DelegatingFilterProxy("springSecurityFilterChain")),
                "/*", EnumSet.allOf(DispatcherType.class));

        context.addFilter(
                new FilterHolder(new JwtFilter()),
                "/api/*", EnumSet.allOf(DispatcherType.class));

        ServletHolder dispatcher =
                new ServletHolder("dispatcher", new DispatcherServlet(ctx));
        dispatcher.setInitOrder(1);
        context.addServlet(dispatcher, "/*");

        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(context);
        server.setHandler(handlers);

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   ECI Secure App — Spring Server         ║");
        System.out.println("║   HTTPS on port : " + port + "                   ║");
        System.out.println("║   Keystore      : " + keystorePath);
        System.out.println("╚══════════════════════════════════════════╝");

        server.start();
        server.join();
    }

    static int getInt(String name, int def) {
        try { return Integer.parseInt(System.getenv(name)); }
        catch (Exception e) { return def; }
    }

    static String getStr(String name, String def) {
        String v = System.getenv(name);
        return (v != null && !v.isBlank()) ? v : def;
    }
}
