# Rainy.fun

Пустой Fabric client framework для Minecraft 26.2. Скрипт `build-client.ps1` собирает проект и передаёт Fabric JAR в `LegitBuilder` из корня репозитория. Итог билдера — `LegitBuilder.jar`.

## Сборка

Нужны JDK 25 для мод-проекта, JDK 21, CMake и MSVC Build Tools для билдера. Из каталога `rainy.fun`:

```powershell
.\build-client.ps1
```

Если сборочные инструменты установлены в других местах, передайте `-ModJdk`, `-BuilderJdk`, `-Generator` и `-CMake`.

Скрипт сначала выполняет Gradle-сборку Fabric JAR, затем вызывает корневой `build.ps1 -Jar ...`. Финальный результат появляется в `D:\RainyDLC\LegitBuilder.jar` и `D:\RainyDLC\build\release\LegitBuilder.jar`.

Чтобы собрать только входной Fabric JAR:

```powershell
.\gradlew.bat build
```

## Структура

- `rainy.fun.client` — client entrypoint и bootstrap.
- `rainy.fun.module` — базовый модуль, категории и реестр.
- `rainy.fun.module.impl` — пакеты будущих модулей по категориям.
- `rainy.fun.module.settings` — базовые типы настроек модулей.
- `rainy.fun.gui` — минимальный экран и управление зарегистрированными модулями.
- `rainy.fun.utils`, `rainy.fun.event`, `rainy.fun.config` — отдельные места под утилиты, события и конфигурацию.

Реестр по умолчанию пуст. Экран открывается клавишей Right Shift. Чтобы добавить модуль, создайте его в соответствующем пакете и зарегистрируйте экземпляр через `getModuleManager().register(...)` в bootstrap-коде.
