Android Local Music Player

Aplicación Android para la reproducción de archivos de audio locales (.mp3), diseñada para operar completamente offline y optimizada para manejar bibliotecas musicales de gran tamaño (≥ 5.000 pistas, ~40 GB de datos).

El proyecto se centra en el rendimiento, la gestión eficiente del almacenamiento local y una arquitectura clara, evitando dependencias innecesarias y funcionalidades que no aporten al caso de uso principal.

Descripción técnica

La aplicación escanea el almacenamiento local del dispositivo Android para detectar archivos de audio compatibles, los indexa y los presenta al usuario mediante una interfaz ligera y de rápida respuesta.

El sistema de reproducción se apoya en las APIs nativas de Android, garantizando estabilidad, bajo consumo de recursos y compatibilidad con grandes volúmenes de archivos.

Funcionalidades

Escaneo del almacenamiento local del dispositivo

Indexación eficiente de archivos .mp3

Reproducción de audio sin conexión a internet

Controles básicos de reproducción:

Play / Pause

Siguiente / Anterior

Navegación por biblioteca musical

Manejo eficiente de memoria para bibliotecas extensas

Arquitectura

Lenguaje: Kotlin

Plataforma: Android

Arquitectura: MVVM (Model–View–ViewModel)

Gestión del estado: ViewModel + LiveData / StateFlow

Reproducción de audio: APIs nativas de Android (MediaPlayer / ExoPlayer)

Persistencia local: MediaStore / almacenamiento interno

Build system: Gradle

Rendimiento y escalabilidad

La aplicación está diseñada para:

Minimizar operaciones costosas en el hilo principal

Evitar recargas completas de la biblioteca

Soportar miles de archivos de audio sin degradación notable de rendimiento

Reducir el consumo de memoria durante la navegación y reproducción

Requisitos del sistema

Android 8.0 (API 26) o superior

Permisos de lectura de almacenamiento

Dispositivo físico o emulador Android

Alcance del proyecto

Incluido:

Reproducción de música local

Gestión eficiente de archivos grandes

No incluido:

Streaming de audio

Descargas desde internet

Sincronización en la nube

Funciones sociales o recomendaciones