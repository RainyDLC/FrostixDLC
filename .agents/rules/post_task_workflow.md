# Post-Task Workflow

После каждой завершенной задачи обязательно выполнять в терминале следующую последовательность:
1. `git add .`
2. `git commit -m "<Краткое описание изменений>"`
3. `git pull --rebase`
4. `git push`
5. Запустить клиент игры: `powershell -Command ".\gradlew.bat runClient"` (в фоновом режиме `IsDaemon: true`).
