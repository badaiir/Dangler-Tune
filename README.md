# Dangler Tune 🎸

Гитарный тюнер для рок-группы Dangler. Базовый строй — **Drop B**, плюс Standard E, Drop D, D Standard, Eb, Drop C.

- Алгоритм: YIN pitch detection + параболическая интерполяция + медианный фильтр (точность ~1 цент)
- UI: Jetpack Compose, брутальный тёмный стиль, Peterson-style строб-полоса, большая нота, glow + вибрация в точке
- Темы: Brutal Black / Blood Stage / Acid Rehearsal
- Сборка: GitHub Actions → APK в Artifacts / Releases

## Сборка локально
```bash
./gradlew assembleDebug
```

## APK из Actions
Вкладка **Actions → Build APK → Artifacts: dangler-tune-apk**
