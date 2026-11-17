# Моё радио — Android-приложение

Интернет-радио: каталог станций (открытая база radio-browser.info) с поиском
и фильтрами по стране и жанру,
избранное, добавление своей станции по ссылке, воспроизведение в фоне
с управлением из шторки уведомлений и с экрана блокировки.

## Как собрать APK

1. Установите Android Studio: https://developer.android.com/studio
2. Распакуйте архив. File → Open → выберите папку `RadioApp`.
3. Дождитесь окончания синхронизации Gradle (первый раз 5–15 минут,
   нужен интернет: скачиваются Android SDK и библиотеки).
   Если Studio предложит обновить Android Gradle Plugin — можно отказаться.
4. Build → Build App Bundle(s) / APK(s) → Build APK(s).
5. Готовый файл: `app/build/outputs/apk/debug/app-debug.apk`.
   Скопируйте его на телефон и установите (разрешив установку из этого источника),
   либо подключите телефон по USB и нажмите ▶ Run.

## Где что менять

- Название приложения — `app/src/main/res/values/strings.xml` (`app_name`)
- Цвет иконки — `app/src/main/res/values/colors.xml`
- Источник каталога и параметры поиска — `RadioBrowserApi.kt`
- Плеер и уведомление — `PlaybackService.kt`
- Экран — `MainActivity.kt`, `res/layout/activity_main.xml`

Требуется Android 8.0 или новее.

## Сборка без Android Studio (GitHub)

1. Создайте на github.com приватный репозиторий и загрузите в него содержимое
   папки `RadioApp` (вместе с папкой `.github`).
2. Откройте вкладку Actions: сборка «Build APK» запустится сама (5–10 минут).
3. Откройте завершённую сборку и скачайте `MyRadio-apk` в разделе Artifacts.
