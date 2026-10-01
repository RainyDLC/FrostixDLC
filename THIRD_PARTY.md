# Сторонние библиотеки

В репозитории закреплены зависимости, необходимые для сборки и запуска JAR. Их SHA-256 перечислены в `dependencies/lock.json`.

| Библиотека | Версия | Использование | Лицензия |
| --- | --- | --- | --- |
| Fabric Loader | 0.19.5 | компиляция Java-runtime | Apache 2.0, `dependencies/licenses/fabric-loader.txt` |
| Sponge Mixin (Fabric) | 0.17.4 + Mixin 0.8.7 | компиляция Java-runtime | MIT, `dependencies/licenses/sponge-mixin.txt` |
| ASM, включая tree/commons/util/analysis | 9.9 | компиляция и тесты | BSD 3-Clause, `dependencies/licenses/asm.txt` |
| JNA и JNA Platform | 5.14.0 | Windows API в загрузчике | Apache 2.0 или LGPL 2.1, `dependencies/licenses/jna.txt` |

JNA входит в итоговый загрузочный JAR. Остальные зависимости остаются библиотеками компиляции и должны присутствовать в целевой Fabric JVM в совместимых версиях. Лицензия самого LegitBuilder этим файлом не устанавливается.
