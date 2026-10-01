# Ресурсы мода и шрифты

У клиента два независимых хранилища. Шейдеры, текстуры, звуки и локализации доступны Minecraft через внутренний resource pack. Шрифты собственного рендерера интерфейса скомпилированы в Java-классы и передаются рендереру как массивы байтов. Всё находится внутри собранного мода; скачивание при запуске не нужно.

Не смешивайте место хранения с игровым адресом. `assets/client/shaders/core/blur.vsh` — путь, который Minecraft видит **внутри resource pack**. Источник может хранить его в карте памяти или таблице байтов payload. `client:core/blur` — базовый ID двух стадий шейдера, а не имя файла на диске. Для `UiRegular.bytes()` игрового пути нет: его читает код мода.

## Внутренний resource pack

Для Minecraft 1.21.11 создайте карту `Identifier → byte[]` и реализацию [`ResourcePack`](https://maven.fabricmc.net/docs/yarn-1.21.11%2Bbuild.3/net/minecraft/resource/ResourcePack.html). Все ресурсы, которые мод читает через `ResourceManager`, внесите в эту карту **до** reload. Пакет должен выдавать новый `InputStream` на каждый запрос, перечислять namespace и ресурсы, возвращать корректные метаданные и оставаться доступным после reload. Одной реализации `open` недостаточно: Minecraft вызывает также `findResources` и `getNamespaces`.

Пример структуры игровых путей:

```text
assets/client/shaders/core/blur.vsh
assets/client/shaders/core/blur.fsh
assets/client/textures/gui/panel.png
assets/client/lang/ru_ru.json
assets/client/sounds/notify.ogg
```

В карте это ключи `client:shaders/core/blur.vsh`, `client:shaders/core/blur.fsh`, `client:textures/gui/panel.png` и так далее. Префикс `assets/client/` добавляет сам `ResourcePack` при обращении к клиентским ресурсам. Передавать в `Identifier` полную строку `assets/client/...` нельзя: получится двойной префикс. Базовый shader ID `client:core/blur` ведёт к `client:shaders/core/blur.vsh` и `client:shaders/core/blur.fsh`; для vertex и fragment stage используйте один и тот же базовый ID.

Ресурсный мост LegitBuilder уже сохраняет непустые записи входного JAR. `Jar::getResources()` передаёт их в `CodeGen::emitResourceTable()`, `InitResources()` создаёт `g_resources`, а `NativeBridge.listMixinResources0()` и `getMixinResource0()` позволяют перечислить пути и получить байты. Mixin service пользуется мостом для JSON и refmap. **Сам мост не регистрирует resource pack в Minecraft.** Код мода выбирает записи с префиксом `assets/client/`, превращает их в `Identifier` без этого префикса и наполняет ими пакет:

```java
String prefix = "assets/client/";
for (String path : NativeBridge.listMixinResources0()) {
    if (!path.startsWith(prefix)) continue;
    String relative = path.substring(prefix.length());
    if (relative.isEmpty()) throw new IllegalStateException("Empty resource path: " + path);
    byte[] bytes = NativeBridge.getMixinResource0(path);
    if (bytes == null) throw new IllegalStateException("Missing resource bytes: " + path);
    Identifier id = Identifier.of("client", relative);
    if (resources.putIfAbsent(id, bytes) != null) {
        throw new IllegalStateException("Duplicate resource ID: " + id);
    }
}
```

Пример использует методы `mod.runtime.NativeBridge` и карту `resources` из следующего фрагмента. Методы моста должны быть доступны в целевом runtime, а обычный JAR мода собирается с нужным API на classpath. Не включайте в игровой пакет `.class`, `fabric.mod.json`, Mixin JSON и refmap. Для других namespace обрабатывайте каждый `assets/имя/` отдельно и проверяйте имя через правила `Identifier`.

Шейдерные тексты держите в Java-классах и добавляйте в эту же карту как UTF-8. Например, класс `ShaderSources` может хранить строковые константы `BLUR_VERTEX` и `BLUR_FRAGMENT`; регистрация пары выглядит так:

```java
Map<Identifier, byte[]> resources = new HashMap<>();
resources.put(Identifier.of("client", "shaders/core/blur.vsh"),
        ShaderSources.BLUR_VERTEX.getBytes(StandardCharsets.UTF_8));
resources.put(Identifier.of("client", "shaders/core/blur.fsh"),
        ShaderSources.BLUR_FRAGMENT.getBytes(StandardCharsets.UTF_8));
```

Это лишь наполнение карты пакета; `resources` нужно передать реализации `ResourcePack` и включить её профиль. Исходный GLSL не требуется класть отдельными `.vsh`/`.fsh` в JAR. Игра всё равно получает его через resource pack. Коллизии ID разрешайте явно на сборке: один игровой адрес должен иметь один источник. Проверяйте допустимость namespace и path до регистрации, а не после неудачного reload.

Порядок позднего подключения:

1. На клиентском потоке соберите и проверьте полную карту: байты, ID, пары `.vsh`/`.fsh`, метаданные и отсутствие дубликатов.
2. Зарегистрируйте постоянный профиль внутреннего пакета в [`ResourcePackManager`](https://maven.fabricmc.net/docs/yarn-1.21.11%2Bbuild.1/net/minecraft/resource/ResourcePackManager.html), просканируйте профили и включите его. В Minecraft 1.21.11 набор providers у менеджера не имеет публичного метода добавления; интеграцию с ним нужно рассчитывать на конкретную версию и проверять после ремаппинга.
3. Вызовите `MinecraftClient.reloadResources()` и дождитесь завершения возвращённого future. Лишь затем запрашивайте шейдеры через `ResourceManager` и создавайте pipeline на подходящем render thread.
4. При последующих reload пакет должен снова отдавать те же байты. GPU-объекты, зависящие от ресурсов, пересоздавайте после reload; не регистрируйте второй пакет с тем же ID.

Для графического прохода наличия двух GLSL-файлов недостаточно. `RenderPipeline` должен объявлять формат вершин, режим рисования, blend/depth/cull, uniform-блоки и sampler-слоты, соответствующие исходникам. После компиляции код заполняет uniform-данные, привязывает входные текстуры и выполняет draw. Сохраняйте полную ошибку компиляции вместе с ID и стадией. При resize или reload обновляйте framebuffer, pipeline и связанные GPU-объекты в их жизненном цикле.

Для диагностики разделяйте четыре события: профиль пакета включён, обе стадии обнаружены `ResourceManager`, pipeline скомпилирован, draw выполнен. Строка «загрузка ресурсов» подтверждает лишь reload. Она не означает скачивание и не доказывает, что эффект появился на экране.

## Шрифты как байты Java-классов

Исходные файлы шрифтов держите в проекте мода вне `src/main/resources`. Генератор создаёт Java-классы до `compileJava`; в готовом JAR остаются `.class`, а не исходные `.ttf`, `.otf`, `.png` или файл метрик.

```text
my-client/
  src/embedded/fonts.tsv
  src/embedded/fonts/regular.ttf
  src/embedded/fonts/icons.png
  src/embedded/fonts/icons.txtt
  tools/embed_bytes.py
  build.gradle
```

Скопируйте [генератор](../examples/embedded-resources/embed_bytes.py) в `tools/embed_bytes.py`. В `fonts.tsv` каждая строка содержит имя Java-класса, относительный путь к файлу и SHA-256 в нижнем регистре; разделитель — табуляция. Следующий PowerShell-код создаёт manifest из фактических файлов:

```powershell
$root = Resolve-Path .\src\embedded
$entries = @(
    @('UiRegular', 'fonts/regular.ttf'),
    @('UiIconsAtlas', 'fonts/icons.png'),
    @('UiIconsMetrics', 'fonts/icons.txtt')
)
$lines = foreach ($entry in $entries) {
    $path = Join-Path $root $entry[1]
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $entry[0] + "`t" + $entry[1] + "`t" + $hash
}
[System.IO.File]::WriteAllLines((Join-Path $root 'fonts.tsv'), $lines)
```

Зафиксируйте `fonts.tsv` вместе с исходными файлами. После изменения файла пересчитайте manifest; несовпадение SHA-256 останавливает сборку. Генератор проверяет имена классов, пути, размеры и коллизии, разбивает большой файл на части и кодирует их в Base64-строки допустимого размера для Java class file. Это представление байтов, а не шифрование. Один входной файл ограничен 256 МиБ; переполнение явно прерывает сборку.

Подключение генерации в Gradle с Groovy DSL:

```groovy
def fontSources = layout.projectDirectory.dir('src/embedded')
def fontOutput = layout.buildDirectory.dir('generated/sources/fontBytes/java')

tasks.register('generateFontBytes', Exec) {
    inputs.dir(fontSources)
    inputs.file('tools/embed_bytes.py')
    outputs.dir(fontOutput)
    doFirst { project.delete(fontOutput.get().asFile) }
    commandLine('python', file('tools/embed_bytes.py').absolutePath,
            '--manifest', file('src/embedded/fonts.tsv').absolutePath,
            '--out', fontOutput.get().asFile.absolutePath,
            '--package', 'dev.example.client.generated')
}

sourceSets.main.java.srcDir(fontOutput)
tasks.named('compileJava') { dependsOn(tasks.named('generateFontBytes')) }
```

Для генератора нужен Python 3.10 или новее. Каталог `build/generated/sources/fontBytes/java` очищается перед повторной генерацией, поэтому удалённый из manifest шрифт не остаётся старым классом. Команда `jar tf build/libs/my-client-1.21.11.jar` должна показывать `dev/example/client/generated/UiRegular.class` и классы его частей, но не `regular.ttf`. После этого передавайте **готовый** JAR в `build.ps1`.

Использование TTF в коде мода:

```java
import dev.example.client.generated.UiRegular;
import java.nio.ByteBuffer;

public final class FontData {
    private FontData() {}

    public static ByteBuffer regularFont() {
        byte[] bytes = UiRegular.bytes();
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes).flip();
        return buffer;
    }
}
```

`bytes()` возвращает новый массив. Декодируйте шрифт один раз на жизненный цикл шрифтового движка, а не каждый кадр. Если нативная библиотека удерживает адрес `ByteBuffer`, держите прямой буфер живым до освобождения шрифта. Атлас PNG и файл метрик получают отдельные классы; формат метрик разбирает код вашего шрифтового движка. После потери графического контекста или reload пересоздавайте GPU-атлас на render thread, сохраняя или повторно получая исходные байты. Шрифтовые классы не требуют сетевого запроса, `ResourceManager` и записи `assets/client/fonts/...`.

## Проверка готового мода

Сначала проверьте входной JAR: `.class` шрифтов присутствуют, исходные шрифтовые файлы отсутствуют, все игровые ресурсы есть в карте пакета или в записях `assets/client/...`, из которых она заполняется. После инжекта проверьте, что пак включён ровно один раз, reload завершился без ошибки, `ResourceManager` выдаёт обе стадии каждого шейдера и остальные используемые ID. Для каждого ID полезно записывать длину и SHA-256 полученных байтов. Отдельно проверьте декодирование шрифтов, первый фактический draw, обычный reload, вход на сервер и повторный вход в мир.

`resource-index.json` подтверждает, что сборщик перенёс файлы в payload. Он не подтверждает регистрацию pack и не показывает, использует ли игра конкретный шейдер. Это проверяется только в запущенном клиенте после reload.
