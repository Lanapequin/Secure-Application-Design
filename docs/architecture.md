# Documento de Arquitectura — Secure App
## ECI Workshop: Arquitectura Segura con Certificados Digitales

**Estudiante:** Laura Natalia Perilla Quintero
---

## 1. Introducción

Este documento describe la arquitectura de una aplicación web segura construida siguiendo los principios del taller *Arquitectura Segura con Certificados Digitales*. El sistema está compuesto por dos servidores desplegados en AWS, que se comunican exclusivamente sobre canales cifrados HTTPS/TLS.

El objetivo del sistema es demostrar cómo garantizar **integridad**, **confidencialidad**, **autenticación** y **autorización** tanto a nivel de usuario como a nivel de comunicación entre cliente y servidor.

---

## 2. Arquitectura del Sistema

### 2.1 Diagrama General

```
                        Usuario (Browser)
                              │
                              │ HTTPS (TLS 1.2+)
                              │ Certificado auto-firmado
                              ▼
               ┌──────────────────────────────────┐
               │   SERVIDOR 1 — Apache httpd       │
               │   Amazon Linux 2023               │
               │   IP Pública: 18.207.125.2        │
               │   Puerto: 443 (HTTPS)             │
               │                                  │
               │   Responsabilidad:               │
               │   Entregar el cliente HTML/JS     │
               │   de forma segura al browser      │
               │                                  │
               │   Certificado TLS:               │
               │   Auto-firmado con OpenSSL        │
               │   (RSA 2048, SHA-256)             │
               └────────────┬─────────────────────┘
                            │
                            │ HTTPS (fetch async)
                            │ Authorization: Bearer JWT
                            │
                            ▼
               ┌──────────────────────────────────┐
               │   SERVIDOR 2 — Spring Framework   │
               │   Amazon Linux 2023               │
               │   IP Pública: 54.197.195.207      │
               │   Puerto: 8443 (HTTPS)            │
               │                                  │
               │   Responsabilidad:               │
               │   REST APIs seguras              │
               │   Autenticación con BCrypt/JWT   │
               │                                  │
               │   Certificado TLS:               │
               │   PKCS12 con keytool (RSA 2048)  │
               └──────────────────────────────────┘
```

### 2.2 Componentes

#### Servidor 1 — Apache Web Server

Apache actúa como servidor de presentación. Su única responsabilidad es entregar los archivos estáticos del cliente (HTML, CSS, JavaScript) al navegador del usuario a través de una conexión HTTPS cifrada.

- **Sistema Operativo:** Amazon Linux 2023
- **Software:** Apache httpd 2.4.66 + mod_ssl
- **Puerto:** 443 (HTTPS), redirige 80 → 443
- **TLS:** Certificado auto-firmado generado con OpenSSL (RSA 2048 bits, SHA-256)
- **Headers de seguridad:** HSTS, X-Frame-Options: DENY, X-Content-Type-Options: nosniff

#### Servidor 2 — Spring Framework

Spring actúa como servidor de lógica de negocio. Expone una API REST protegida que maneja la autenticación de usuarios y sirve datos seguros.

- **Sistema Operativo:** Amazon Linux 2023
- **Framework:** Spring Framework 5.3.27 (sin Spring Boot)
- **Servidor embebido:** Jetty 9.4.51
- **Puerto:** 8443 (HTTPS)
- **TLS:** Keystore PKCS12 generado con keytool (RSA 2048 bits)
- **Gestión:** Servicio systemd (arranque automático)

---

## 3. Arquitectura del Cliente (HTML/JS Asíncrono)

El cliente es una Single Page Application (SPA) construida con HTML5, CSS3 y JavaScript puro (sin frameworks). Toda la comunicación con el servidor Spring se realiza de forma **asíncrona** usando la Fetch API del navegador.

```
index.html          → Estructura de la SPA (login + dashboard)
styles.css          → Estilos visuales
config.js           → URLs de los servidores (12-factor)
app.js              → Lógica de autenticación y llamadas API
```

### Flujo asíncrono

```javascript
// Todas las llamadas van sobre HTTPS
async function http(path, options = {}) {
  const res = await fetch(CONFIG.SPRING_URL + path, options);
  // fetch() sobre https:// garantiza cifrado TLS
  return res.json();
}
```

El cliente usa `sessionStorage` para guardar el token JWT — se borra automáticamente cuando el usuario cierra el tab.

---

## 4. Seguridad — Capa por Capa

### 4.1 TLS — Capa de Transporte

Todos los datos viajan cifrados. Ninguna petición se acepta en texto plano.

```
Servidor 1 (Apache):
  Protocolo: TLS 1.2+
  Algoritmo: RSA 2048
  Cert: Auto-firmado con OpenSSL
  Cipher: ECDHE-RSA-AES128-GCM-SHA256

Servidor 2 (Spring/Jetty):
  Protocolo: TLS 1.2, TLS 1.3
  Algoritmo: RSA 2048
  Cert: Auto-firmado con keytool PKCS12
  Keystore: /home/ec2-user/app/keystore/serverkeystore.p12
```

### 4.2 BCrypt — Almacenamiento de Contraseñas

Las contraseñas **nunca** se almacenan en texto plano. Se usa BCrypt con strength-10:

```java
// Registro: la contraseña se hashea antes de guardar
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}

// BCrypt con strength-10 = 2^10 = 1024 iteraciones
// Cada hash incluye un salt aleatorio incorporado
// Ejemplo de hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
```

**El hash de BCrypt es unidireccional.** No puede revertirse. Durante el login, Spring Security llama a `passwordEncoder.matches(plain, hash)` para verificar.

### 4.3 JWT — Autenticación Stateless

Después del login exitoso, el servidor emite un token JWT firmado con HMAC-SHA256:

```
Header:  {"alg":"HS256","typ":"JWT"}
Payload: {"sub":"admin","role":"ROLE_ADMIN","exp":1774194000000}
Firma:   HMAC-SHA256(header.payload, TOKEN_SECRET)
```

**Estructura del token:**
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9    ← Header (base64)
.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJST0xFX0FETUlOIn0  ← Payload (base64)
.signature                                ← Firma HMAC-SHA256
```

El token tiene una validez de 2 horas. Cada petición protegida debe incluirlo:

```
Authorization: Bearer eyJhbGci...
```

### 4.4 JwtFilter — Autorización en cada Request

Cada petición a `/api/*` pasa por el `JwtFilter` antes de llegar al controlador:

```
Request llega a /api/hello
       │
       ▼
JwtFilter.doFilter()
       │
       ├── ¿Tiene header "Authorization: Bearer ..."?
       │         NO → 401 Unauthorized
       │
       ├── ¿La firma HMAC-SHA256 es válida?
       │         NO → 401 Unauthorized
       │
       ├── ¿El token ha expirado?
       │         SÍ → 401 Unauthorized
       │
       └── Todo OK → request.setAttribute("username", sub)
                            │
                            ▼
                     ApiController.hello()
                     → {"message":"Hello","user":"admin"}
```

### 4.5 Spring Security — Configuración

```java
http
  .csrf().disable()               // API REST stateless, no necesita CSRF
  .sessionManagement()
      .sessionCreationPolicy(STATELESS)  // Sin sesiones server-side
  .authorizeRequests()
      .antMatchers("/api/auth/**", "/health").permitAll()  // Rutas públicas
      .antMatchers("/api/**").permitAll()  // JwtFilter maneja la auth
```

---

## 5. Flujo de Autenticación Completo

```
1. Usuario abre https://18.207.125.2
   └── Apache entrega index.html + app.js + config.js + styles.css
       (todo sobre HTTPS/TLS)

2. Usuario escribe usuario/contraseña y hace clic en Login
   └── app.js ejecuta:
       fetch("https://54.197.195.207:8443/api/auth/login", {
         method: "POST",
         body: JSON.stringify({username, password})
       })

3. Spring recibe el POST /api/auth/login
   └── AuthController.login()
       └── authManager.authenticate(username, password)
           └── UserService.loadUserByUsername(username)
               └── BCryptPasswordEncoder.matches(password, storedHash)
                   ├── Incorrecto → 401 {"error":"Invalid credentials"}
                   └── Correcto  → JwtService.generate(username, role)
                                   → {"token":"eyJ...","username":"admin"}

4. El browser guarda el token en sessionStorage
   └── sessionStorage.setItem("eci_token", token)

5. Usuario hace clic en "Call /api/hello"
   └── app.js ejecuta:
       fetch("https://54.197.195.207:8443/api/hello", {
         headers: {"Authorization": "Bearer eyJ..."}
       })

6. Spring recibe GET /api/hello
   └── JwtFilter valida el token
       └── Token válido → ApiController.hello()
           → {"message":"Hello from the secure Spring server!","user":"admin"}

7. El resultado se muestra en el dashboard del cliente
```

---

## 6. Principios 12-Factor App

| Factor | Implementación |
|---|---|
| **I. Codebase** | Un repositorio Git, múltiples deploys (local, AWS) |
| **II. Dependencies** | Todas declaradas en `pom.xml`, sin dependencias implícitas |
| **III. Config** | `PORT`, `KEYSTORE_PATH`, `KEYSTORE_PASS`, `TOKEN_SECRET`, `ALLOWED_ORIGIN` — todas en variables de entorno, nunca en el código |
| **IV. Backing services** | El keystore se trata como recurso adjunto, referenciado por path desde env var |
| **VI. Processes** | Stateless — JWT, sin sesiones server-side |
| **VII. Port binding** | Puerto leído de `PORT` env var. El servidor se auto-contiene con Jetty embebido |
| **IX. Disposability** | Jetty arranca en ~1.5 segundos. systemd reinicia en caso de fallo |
| **XI. Logs** | Escritos a stdout, capturados por systemd (`journalctl -u secure-app`) |

**Ejemplo del principio III en el código:**

```java
// Main.java — todo desde variables de entorno
int    port         = getInt("PORT", 8443);
String keystorePath = getStr("KEYSTORE_PATH", "src/main/resources/keystore/serverkeystore.p12");
String keystorePass = getStr("KEYSTORE_PASS",  "changeit");
String secret       = getStr("TOKEN_SECRET",   "dev-secret-change-in-prod");
```

```bash
# En producción (systemd):
Environment=PORT=8443
Environment=KEYSTORE_PATH=/home/ec2-user/app/keystore/serverkeystore.p12
Environment=TOKEN_SECRET=a8f3b2c1d4e5...  # secreto generado con openssl rand
```
---

## 7. Infraestructura AWS

```
┌─────────────────────────────────────────────────────────────────┐
│                       AWS Region (us-east-1)                     │
│                                                                  │
│   ┌─────────────────────┐    ┌─────────────────────────────┐    │
│   │  EC2: eci-apache     │    │  EC2: eci-spring-server     │    │
│   │  t2.micro            │    │  t2.micro                   │    │
│   │  18.207.125.2        │    │  54.197.195.207             │    │
│   │                     │    │                             │    │
│   │  SG: eci-apache-sg  │    │  SG: eci-spring-sg          │    │
│   │  - 22 (SSH/MyIP)    │    │  - 22 (SSH/MyIP)            │    │
│   │  - 80 (HTTP)        │    │  - 8443 (HTTPS Spring)      │    │
│   │  - 443 (HTTPS)      │    │                             │    │
│   └─────────────────────┘    └─────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 8. Garantías de Seguridad

| Requisito | Mecanismo | Verificación |
|---|---|---|
| Integridad del transporte | TLS cifra y firma cada paquete | `openssl s_client -connect IP:8443` |
| Autenticación de usuario | BCrypt + JWT | Login con credenciales incorrectas → 401 |
| Autorización de usuario | JwtFilter en `/api/*` | Request sin token → 401 |
| Confidencialidad | HTTPS en ambos servidores | Todo tráfico cifrado |
| No repudio de tokens | HMAC-SHA256 con secreto del servidor | Token modificado → 401 |
| Contraseñas seguras | BCrypt strength-10 + salt aleatorio | Hash diferente cada encode |

---

## 9. Referencias

- Spring Framework 5.3 Documentation: https://docs.spring.io/spring-framework/docs/5.3.x/reference/html/
- Spring Security 5.7 Reference: https://docs.spring.io/spring-security/reference/5.7/
- Oracle keytool: https://docs.oracle.com/en/java/javase/11/tools/keytool.html
- PKCS12 vs JKS: https://docs.oracle.com/cd/E19509-01/820-3503/ggffo/index.html
- 12-factor App: https://12factor.net/
- AWS AL2023 LAMP Setup: https://docs.aws.amazon.com/linux/al2023/ug/ec2-lamp-amazon-linux-2023.html
- Taller de Arquitectura Segura — Luis Daniel Benavides Navarro (ECI, 2020)
- JWT RFC 7519: https://datatracker.ietf.org/doc/html/rfc7519
- BCrypt: https://en.wikipedia.org/wiki/Bcrypt
