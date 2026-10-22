# catmirror - Transmisión de Pantalla y Control Táctil Remoto en Tiempo Real

catmirror es una aplicación Android de alto rendimiento desarrollada en Kotlin con Jetpack Compose que permite reflejar la pantalla de un dispositivo emisor en un dispositivo receptor con latencia ultra-baja a través de Wi-Fi local o cable USB, permitiendo además el control táctil remoto interactivo de las aplicaciones del emisor mediante el Servicio de Accesibilidad de Android.

---

### Decisiones Críticas y Confirmaciones de Usuario

> [!IMPORTANT]
> Decisiones confirmadas a través del proceso de clarificación:
> - **Método de Conexión Principal:** Wi-Fi local (red compartida / hotspot) con soporte complementario para conexión directa por cable USB (tethering / puerto local).
> - **Control Táctil Remoto:** Inyección de toques y gestos en todo el sistema del emisor utilizando el API oficial `AccessibilityService` (`dispatchGesture`) de Android, junto con atajos de navegación del sistema (Inicio, Atrás, Apps recientes).
> - **Mecanismo de Detección y Emparejamiento:** Descubrimiento automático en red local mediante difusión de red (NSD/UDP Beacon) con código PIN de 6 dígitos y código QR para vinculación instantánea y segura.

---

## 1. Visión General y Concepto Central

- **Propósito:** Facilitar la colaboración, asistencia técnica remota, presentaciones y uso multidispositivo entre dos teléfonos o tablets Android sin intermediarios en la nube ni costos de suscripción.
- **Roles de Dispositivo:**
  - **Dispositivo Emisor (Host):** Captura su propia pantalla en tiempo real mediante `MediaProjection` / `VirtualDisplay`, codifica los frames y los transmite por socket TCP/UDP al receptor. Escucha comandos táctiles del receptor y los ejecuta en el sistema mediante su `AccessibilityService`.
  - **Dispositivo Receptor (Controlador):** Descubre o se conecta al emisor, recibe el flujo de video en tiempo real, lo renderiza fluidamente en un canvas interactivo y traduce los toques en pantalla (tap, arrastrar, scroll) a coordenadas normalizadas que envía al emisor.
- **Valor Clave:** Experiencia directa punto a punto, privacidad total (cero paso por servidores externos), baja latencia (<50ms en red Wi-Fi óptima), e interfaz limpia y amigable con temática felina "catmirror".

---

## 2. Experiencia de Usuario y Diseño Visual

### Identidad Visual y Estilo
- **Concepto:** *Cyber Cat / Modern Dark Slate with Amber & Electric Cyan Accents*.
- **Paleta de Colores:**
  - Fondo primario: `#0F141C` (azul pizarra profundo)
  - Superficies elevadas: `#1A2230` y `#242E42`
  - Color Primario (Acento): `#FF9E2C` (Ámbar cálido / Ojo de gato)
  - Color Secundario (Conexión/Tecnología): `#00E5FF` (Cian eléctrico / pulso de red)
  - Color de Éxito / En vivo: `#00E676` (Verde esmeralda neón)
  - Superficie de error / desconexión: `#FF5252` (Rojo coral)
- **Tipografía y Componentes:** Material 3 con bordes suaves redondeados (16dp - 24dp), tarjetas tonales con indicadores de estado de red, animaciones fluidas con `AnimatedVisibility` y pulsos de conexión.

### Flujos Principales de Usuario

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PANTALLA PRINCIPAL                             │
│                  catmirror  🐱 [Modo Wi-Fi / USB]                     │
├───────────────────────────────────┬────────────────────────────────────┤
│      [ Modo Emisor ]              │       [ Modo Receptor ]            │
│  "Transmitir esta pantalla"       │   "Ver y controlar otro teléfono"  │
│  • Genera PIN / Código QR         │   • Escanea dispositivos Wi-Fi     │
│  • Activa MediaProjection         │   • Ingreso de PIN / Escaneo QR    │
│  • Servicio de Accesibilidad      │   • Opción de conexión manual/USB  │
└───────────────────────────────────┴────────────────────────────────────┘
                                     │
                     ┌───────────────┴───────────────┐
                     ▼                               ▼
       ┌───────────────────────────┐   ┌───────────────────────────┐
       │   SESIÓN EN TRANSMISIÓN   │   │  CANVAS INTERACTIVO REMOTO│
       │  (Dispositivo Emisor)     │   │   (Dispositivo Receptor)  │
       │  • Widget flotante / Notif│   │  • Video en tiempo real   │
       │  • Estado de conexión     │   │  • Control táctil activo  │
       │  • Clientes conectados    │   │  • Barra de gestos:       │
       │  • Botón de pausar/cortar │   │    [Atrás] [Inicio] [Apps]│
       └───────────────────────────┘   └───────────────────────────┘
```

1. **Selección de Rol Inicial:** El usuario ve de inmediato dos tarjetas interactivas prominentes: **Emisor (Compartir Pantalla)** y **Receptor (Controlar Pantalla)**.
2. **Flujo Emisor:**
   - Muestra la IP local del dispositivo, el estado del Wi-Fi y genera un PIN de seguridad de 6 dígitos y código QR.
   - Solicita y verifica el permiso de Captura de Pantalla (`MediaProjectionManager`) y el Servicio de Accesibilidad (`CatMirrorAccessibilityService`) con guías paso a paso intuitivas.
   - Al iniciar, lanza un servicio en primer plano (`Foreground Service`) con notificación permanente y métricas de transmisión (FPS, tasa de bits, clientes conectados).
3. **Flujo Receptor:**
   - Escanea automáticamente la red local buscando emisores `catmirror` activos.
   - Permite conectar con 1 toque al dispositivo detectado ingresando el PIN o escaneando el QR, o mediante conexión directa por cable USB / IP manual.
   - Abre la vista de Pantalla Completa donde se renderiza el stream de video a baja latencia. Cada toque o gesto realizado sobre la pantalla se envía inmediatamente al emisor para su ejecución. Incluye una barra de navegación rápida para simular los botones Inicio, Atrás y Multitarea.

---

## 3. Decisiones Técnicas Clave y Compensaciones

- **Protocolo de Transmisión de Cuadros:**
  - *Decisión:* Arquitectura de streaming basada en socket TCP optimizado con buffer circular y compresión adaptativa JPEG/H.264 para garantizar máxima compatibilidad entre diferentes versiones de Android sin requerir compilación NDK compleja, logrando latencias inferiores a 50-70ms en redes Wi-Fi locales estándar.
  - *Ventaja:* No requiere servidores STUN/TURN externos, no depende de servicios en la nube y opera 100% offline o en redes directas/USB.
- **Inyección de Toques Remotos con `AccessibilityService`:**
  - *Decisión:* `CatMirrorAccessibilityService` utiliza `dispatchGesture` con `GestureDescription` (API oficial de Android disponible desde Android 7.0 / API 24).
  - *Ventaja:* No requiere root ni comandos ADB por terminal para el uso diario; el usuario simplemente habilita el interruptor de accesibilidad una sola vez en los Ajustes de Android.
- **Conectividad Híbrida Wi-Fi + USB:**
  - *Decisión:* El servidor socket de `catmirror` escucha en `0.0.0.0:8888`. En Wi-Fi se comunica a través de la IP local de la subred; por USB se puede conectar mediante USB Tethering directo o redirección de puertos `127.0.0.1:8888`.
  - *Ventaja:* Máxima flexibilidad para el usuario en cualquier escenario (con o sin router Wi-Fi).
- **Notificaciones del Sistema:**
  - *Decisión:* Canal de notificación persistente `catmirror_stream` con botones de acción para pausar/detener la transmisión de forma segura en cualquier momento.

---

## 4. Arquitectura Técnica y Estrategia de Datos

### Diagrama de Componentes y Flujo de Datos

```
┌────────────────────────────────────────────────────────────────────────┐
│                        DISPOSITIVO B (EMISOR)                          │
│                                                                        │
│   ┌─────────────────────┐       ┌──────────────────────────────────┐  │
│   │ MediaProjection     │       │ CatMirrorAccessibilityService    │  │
│   │ + VirtualDisplay    │       │ (Inyección de toques en sistema) │  │
│   └──────────┬──────────┘       └──────────────────▲───────────────┘  │
│              │ Captura frames                      │ Ejecuta toques   │
│              ▼                                     │                  │
│   ┌─────────────────────┐               ┌──────────┴───────────────┐  │
│   │ ScreenStreamServer  ├───────────────┤ TouchCommandDispatcher   │  │
│   │ (TCP/Socket Server) │ Frames (Video)│ (Recibe x, y, acción)    │  │
│   └──────────▲──────────┘               └──────────────────────────┘  │
│              │ Anuncia presencia                                       │
│   ┌──────────┴──────────┐                                             │
│   │ DiscoveryBroadcast  │ (NSD / UDP Beacon en puerto 8889)           │
│   └──────────┬──────────┘                                             │
└──────────────┼─────────────────────────────────────────────────────────┘
               │ Red Wi-Fi Local o Cable USB (Socket TCP: 8888)
┌──────────────┼─────────────────────────────────────────────────────────┐
│              ▼                                                         │
│   ┌─────────────────────┐               ┌──────────────────────────┐  │
│   │ DiscoveryClient     │               │ TouchEventListener       │  │
│   │ (Detecta emisores)  │               │ (Captura taps y arrastre)│  │
│   └──────────┬──────────┘               └──────────▲───────────────┘  │
│              │ Dispositivo encontrado              │ Taps del usuario │
│              ▼                                     │                  │
│   ┌─────────────────────┐               ┌──────────┴───────────────┐  │
│   │ ScreenStreamClient  ├───────────────┤ RemoteDisplayCanvas      │  │
│   │ (Socket Client)     │ Cuadros video │ (Renderiza en Compose)   │  │
│   └─────────────────────┘               └──────────────────────────┘  │
│                                                                        │
│                        DISPOSITIVO A (RECEPTOR)                        │
└────────────────────────────────────────────────────────────────────────┘
```

### Modelos de Datos y Protocolo de Mensajería
1. **`DeviceRole`**: `IDLE`, `EMITTER`, `RECEIVER`.
2. **`DiscoveredDevice`**: `name`, `ipAddress`, `port`, `isPaired`, `lastSeenTimestamp`.
3. **`StreamPacket`**:
   - Cabecera: `type` (FRAME, TOUCH, CONTROL, PING), `payloadSize`, `timestamp`.
   - Carga útil FRAME: imagen comprimida codificada con resolución y orientación.
   - Carga útil TOUCH: `action` (DOWN, MOVE, UP), `xPercent` (0.0 a 1.0), `yPercent` (0.0 a 1.0).
   - Carga útil CONTROL: `action` (KEY_BACK, KEY_HOME, KEY_RECENTS, DISCONNECT).

### Permisos y Configuración en AndroidManifest
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.ACCESS_WIFI_STATE`
- `android.permission.CHANGE_WIFI_MULTICAST_STATE`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `android.permission.POST_NOTIFICATIONS`
- Declaración del servicio de accesibilidad: `<service android:name=".service.CatMirrorAccessibilityService" ...>`

---

## 5. Verificación y Pruebas
- Validación del flujo de selección de modo, detección de red y estados de conexión.
- Verificación de la compilación exitosa con `compile_applet`.
- Pruebas unitarias de serialización/deserialización del protocolo de toques y frames.
