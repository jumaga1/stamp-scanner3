# Escáner de Sellos — Proyecto Android (Kotlin + Jetpack Compose)

App para catalogar una colección filatélica personal: captura por cámara,
preprocesamiento de imagen, detección de duplicados por hash perceptual +
metadatos, base de datos local, y un punto de integración con IA externa
para sugerir país/época/valor/estado de conservación.

## ⚠️ Alcance real (léelo antes de usarlo)

- **No incluye un modelo de IA propio entrenado en filatelia mundial.** No existe
  hoy un modelo público así. El módulo de IA (`ai/AiRecognitionRepository.kt`)
  llama a **Gemini** (Google AI Studio) usando su capa **gratuita** (sin
  tarjeta, sin cobro, con límite de solicitudes por día) con un prompt
  especializado. La calidad de la identificación depende del modelo de Gemini
  disponible en el momento.
- **La detección de duplicados sí funciona 100% localmente y sin IA**: usa un
  hash perceptual (pHash con DCT) comparado por distancia de Hamming, combinado
  con coincidencia de metadatos (país/año/valor). Es rápido y no requiere internet.
- **Los catálogos Scott, Michel y Yvert son privados/de pago**, sin API pública.
  La app genera enlaces de búsqueda (y usa Colnect/StampWorld, que sí son
  navegables públicamente) en vez de simular datos de catálogo inventados.
- Este código compila un **APK real** una vez lo abras en Android Studio — pero
  el archivo `.apk` en sí no se puede generar en este entorno de chat (no hay
  Android SDK/Gradle disponible aquí).

## Compilar el APK automáticamente en la nube (sin instalar nada)

Este repo incluye `.github/workflows/build-apk.yml`: cada vez que hagas
`git push` a la rama `main`, GitHub Actions compila el APK por ti.

1. Sube el proyecto a GitHub (ver sección siguiente).
2. (Opcional, para que la IA identifique los sellos) Consigue una clave
   **gratuita** de Gemini en https://aistudio.google.com/apikey (inicia sesión
   con tu cuenta de Google, clic en "Create API Key" — no pide tarjeta).
   Luego, en tu repo de GitHub ve a **Settings → Secrets and variables →
   Actions → New repository secret** y agrega:
   - `AI_API_KEY`: la clave que te dio Google AI Studio
   - (no necesitas agregar `AI_API_BASE_URL`, ya tiene el valor correcto por defecto)
3. Haz push. Ve a la pestaña **Actions** de tu repo → entra a la ejecución
   más reciente → al terminar (unos 3-5 min), baja hasta **Artifacts** →
   descarga `stamp-scanner-debug-apk`.
4. Descomprime ese artefacto: dentro está `app-debug.apk`, listo para copiar
   a tu celular e instalar.

> Nota: es un APK de **debug**, perfecto para probar en tu propio celular.
> Para publicarlo en una tienda necesitarías firmarlo como "release"
> (ver sección de Android Studio más abajo).

## Subir este proyecto a GitHub

Desde la carpeta del proyecto (ya viene inicializada como repo git local
con el primer commit hecho):

```bash
# 1. Crea un repositorio vacío en https://github.com/new (sin README, sin .gitignore)
# 2. Conéctalo y sube el código:
git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
git push -u origin main
```

Si prefieres usar SSH en vez de HTTPS:
```bash
git remote add origin git@github.com:TU_USUARIO/TU_REPO.git
git push -u origin main
```

## Requisitos para compilar localmente (Android Studio)

- Android Studio (Koala o más reciente recomendado)
- JDK 17 (Android Studio ya trae uno compatible)
- Un dispositivo o emulador con Android 8.0 (API 26) o superior

## Pasos para generar el APK

1. Descomprime el proyecto y ábrelo con **Android Studio** (`Open` → selecciona
   la carpeta `StampScanner`).
2. Deja que Gradle sincronice (puede tardar unos minutos la primera vez,
   descarga dependencias).
3. Copia `local.properties.example` a `local.properties` en la raíz del
   proyecto y completa:
   - `sdk.dir`: normalmente Android Studio ya lo autocompleta.
   - `AI_API_KEY`: tu clave de API del proveedor de IA que quieras usar
     (opcional — sin ella, la app sigue funcionando, solo que no sugiere
     datos automáticamente y tendrás que llenarlos a mano).
4. Conecta un celular Android (con "Depuración USB" activada) o crea un
   emulador desde Android Studio (`Device Manager`).
5. Presiona **Run ▶** o usa el menú `Build > Build Bundle(s)/APK(s) > Build APK(s)`.
6. El APK generado queda en:
   `app/build/outputs/apk/debug/app-debug.apk`
   Cópialo a tu celular e instálalo (activa "Instalar apps de fuentes
   desconocidas" si Android lo pide).

Para un APK firmado y optimizado para distribuir (no solo debug):
`Build > Generate Signed Bundle / APK...` y sigue el asistente (necesitas
crear un keystore la primera vez).

## Cómo funciona el reconocimiento por IA (capa gratuita)

El archivo clave es `app/src/main/java/com/filatelia/scanner/ai/AiRecognitionService.kt`.
Usa la **API de Gemini** (Google AI Studio), que tiene una capa gratuita real:
sin tarjeta de crédito, sin cobro, con un límite generoso de solicitudes por
día (no de dinero). Para catalogar sellos uno por uno como colección personal
alcanza sobradamente.

**Cómo conseguir tu clave gratuita:**
1. Ve a https://aistudio.google.com/apikey
2. Inicia sesión con cualquier cuenta de Google
3. Clic en "Create API Key" — no pide tarjeta ni datos de pago
4. Copia la clave y ponla en `local.properties` (`AI_API_KEY=...`) para
   compilar localmente, o como secreto de GitHub Actions (`AI_API_KEY`) para
   que el APK compilado en la nube ya la traiga incluida.

**Importante sobre "gratis":** el límite es de *solicitudes por día*, no de
dinero — no te van a cobrar nada, pero si en un mismo día escaneas muchísimos
sellos podrías toparte con el límite y tener que esperar al día siguiente
(la app te avisa con un mensaje si eso pasa, y puedes seguir llenando los
datos a mano mientras tanto).

**Si quieres usar otro proveedor** (OpenAI, Claude, tu propio backend, etc.)
en vez de Gemini, cambia las clases de datos en `AiRecognitionService.kt` por
las que coincidan con el formato de ese proveedor, y ajusta la autenticación
en `AiRecognitionRepository.kt`. El patrón es siempre el mismo: mandas imagen
en base64 + instrucciones, recibes texto/JSON.

Verifica siempre en la documentación **vigente** de Google
(https://ai.google.dev/gemini-api/docs) el nombre exacto del modelo y el
formato de petición/respuesta, ya que estos detalles cambian con el tiempo.

## Estructura del proyecto

```
app/src/main/java/com/filatelia/scanner/
├── MainActivity.kt              # punto de entrada
├── StampScannerApp.kt           # inicializa BD y repos
├── data/                        # Room: entidad, DAO, base de datos, repositorio
├── imageprocessing/             # recorte, normalización, hash perceptual (pHash)
├── duplicate/                   # reglas de detección de duplicados
├── ai/                          # conector a IA externa (desacoplado)
├── catalog/                     # enlaces a catálogos internacionales
└── ui/
    ├── screens/                 # Escanear, Colección, Detalle
    ├── viewmodel/                # lógica de presentación
    ├── navigation/               # bottom nav + rutas
    └── theme/                    # Material 3
```

## Funcionalidades implementadas vs. lo solicitado

| Requisito | Estado |
|---|---|
| Captura por cámara | ✅ CameraX, más opción de elegir imagen de galería (para escáner externo) |
| Preprocesamiento (recorte, limpieza, normalización) | ✅ corrección de rotación EXIF, recorte, ajuste de contraste/brillo, tamaño estándar |
| Identificación con IA (país, época, valor, estado) | ⚙️ Conectado a Gemini (capa gratuita); solo necesitas tu propia clave gratuita |
| Base de datos personal con metadatos | ✅ Room, organización por país/año/serie/rareza, búsqueda |
| Detección de duplicados (imagen + metadatos) | ✅ pHash + comparación de metadatos, con niveles de confianza |
| Ficha informativa automática | ⚙️ Estructura completa lista; el contenido depende de la IA conectada o se llena a mano |
| Enlace a catálogos Scott/Michel/Yvert | ⚙️ Enlaces de búsqueda (no hay API pública de esos catálogos) |

## Próximos pasos sugeridos

- Si quieres recorte automático más preciso del sello, integra **OpenCV
  Android** para detección de bordes (dejé el punto de extensión en
  `ImagePreprocessor.kt`).
- Si más adelante consigues un dataset filatélico etiquetado, se puede
  entrenar un modelo específico (por ejemplo con TensorFlow Lite) y
  reemplazar el conector de IA externo por inferencia en el dispositivo.
