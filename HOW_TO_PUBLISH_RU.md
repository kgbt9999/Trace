# Как выложить Trace на GitHub

В проекте **больше 100 файлов**. Загрузка перетаскиванием на сайте GitHub  
(**Upload files**) **не подходит** — появится *“Yowza, that's a lot of files…”*.

Ниже — рабочие способы от простого к привычному для разработчиков.

---

## Способ A (рекомендуется): GitHub Desktop

1. Скачайте [GitHub Desktop](https://desktop.github.com/).
2. Войдите в аккаунт GitHub.
3. **File → Add local repository → Choose…**  
   укажите папку: `C:\Users\HONOR\Desktop\Trace`  
   (если предложит *create a repository* — согласитесь).
4. Внизу слева введите сообщение коммита, например: `Initial Trace Android release`.
5. **Commit to main**.
6. **Publish repository**:
   - имя: `Trace`
   - снимите галку *Keep this code private*, если нужен публичный репозиторий  
     (или оставьте, если хотите приватный).
7. **Publish repository**.

Готово: код на GitHub, без лимита «100 файлов».

---

## Способ B: сайт + Git в терминале

### 1. Создайте пустой репозиторий на сайте

1. [github.com/new](https://github.com/new)
2. Repository name: `Trace`
3. **без** галок README / .gitignore / license (они уже в папке).
4. Create repository.

### 2. Залейте папку командами

Откройте **PowerShell**:

```powershell
cd C:\Users\HONOR\Desktop\Trace

git init
git add -A
git status
# Проверьте: НЕТ keystore.properties, *.keystore, local.properties, *.apk

git commit -m "Initial Trace Android release"
git branch -M main
git remote add origin https://github.com/ВАШ_ЛОГИН/Trace.git
git push -u origin main
```

Подставьте свой логин вместо `ВАШ_ЛОГИН`.  
При запросе входа используйте GitHub login / Personal Access Token.

---

## Способ C: только сайт (не для этого проекта)

**Upload files** на GitHub принимает **меньше 100 файлов за раз**.  
У Trace ~170+ файлов исходников — придётся дробить вручную по кускам, легко ошибиться.  
**Не рекомендуется.**

Если уже открыли Upload и видите предупреждение — нажмите **Cancel** и используйте способ A или B.

---

## Перед публикацией — чеклист

- [ ] В папке нет `keystore.properties`, `release.keystore`, `local.properties`, `*.apk`
- [ ] Есть `README.md`, `LICENSE`, `.gitignore`
- [ ] В README указано: это не диагностика
- [ ] Открывать в Studio нужно папку `android/`, не корень

Секреты подписи APK храните только у себя на компьютере.
