# PingMonitorApp - Proyecto completo

Este proyecto contiene una app Android que:
- Permite agregar múltiples IPs
- Cada IP puede activarse/desactivarse
- Ejecuta pings periódicos en un servicio en foreground (funciona en background aunque cierres la app)
- Envía notificación con sonido cuando un host deja de responder
- Muestra un gráfico simple de latencias con MPAndroidChart
- Permite ajustar el intervalo (SeekBar)

## Cómo compilar
- Importa el proyecto en Android Studio **o** usa `./gradlew assembleDebug`
- El workflow de GitHub Actions previamente proporcionado funcionará con este repositorio.

## Notas
- Android 13+ requiere permiso de `POST_NOTIFICATIONS`; la app pedirá permiso al iniciar el servicio.
- Para sonido personalizado: reemplaza `app/src/main/res/raw/alert_sound_placeholder.txt` por `alert_sound.mp3` en la misma carpeta.
- El servicio es foreground para evitar que Android lo mate; aun así, algunos fabricantes limitan procesos en background.

## Qué contiene el ZIP
- Código fuente Kotlin
- Layouts y recursos básicos
- README con instrucciones

Si quieres que añada el GitHub Actions workflow dentro del ZIP (para compilar automáticamente en GitHub), dime y lo incluyo.
