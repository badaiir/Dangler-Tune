# Dangler Tune 🎸🩸

Гитарный тюнер для рок-группы Dangler. Базовый строй — **Drop B**, плюс Standard E, Drop D, D Standard, Eb, Drop C.

## Звук: алгоритм детекции

- **YIN** (de Cheveigné & Kawahara) поверх кадров 4096 @ 44100 Гц — устойчив к гармоникам гитары, в отличие от наивного FFT-пика
- Параболическая интерполяция периода → точность ~1 цент
- RMS-gate против тишины, медианный фильтр по 5 кадрам против выбросов/перескоков на октаву
- Центы считаются относительно **ближайшей струны выбранного строя**, а не абстрактной ноты
- Файлы: `dsp/YinPitchDetector.kt`, `audio/AudioRecorder.kt`, `model/Tunings.kt`

## Дизайн-система: Gloryhole-base, кровь Dangler

UI-кит (`ui/components/DanglerKit.kt`) портирован по механике из GloryholeVPN, палитра своя:

| Приём из Gloryhole | Как у нас |
|---|---|
| `AutumnGlassCard` (clip 24dp + glass + border 1dp, без теней) | `DanglerGlassCard`, бордер `blood 0x40FF2E2E` |
| `AutumnHeaderPill` (капсула 14dp + градиент-бордер) | `DanglerHeaderPill` с красно-чёрным градиентом, там живёт `DANGLER • строй` |
| `AutumnStatusPill` (дышащий бордер через `animateColorAsState`) | `DanglerStatusPill`: СЛУШАЮ / ВЫШЕ / НИЖЕ / В ТОЧКЕ |
| `SettingsItemSwitch` (ряд 16dp + иконка + Switch) | `DanglerSwitchRow` |
| `DevTuningDialog`-слайдеры (акцентный thumb, моно-значения) | `DanglerSliderRow`, цифры — `Monospace` |
| Дыхание `tween + Reverse` (Blobatar / TactileConnectButton) | Дыхание glow ноты и кромки меню |
| `GyroParallaxBackground` (rotation vector → калибровка → EMA 0.10 → deadzone 0.18) | `sensor/TiltSensor.kt` — та же математика |

Темы (свои, не осенние): **Brutal Black**, **Blood Stage**, **Acid Rehearsal**. В точке всегда кислотный зелёный, расстройка — кровь.

## HERO: кровавая нота (`ui/components/BloodNote.kt`)

Стрелку-шкалу выкинули — теперь сама нота и есть прибор:

- Гигантский символ строя (`F#2`, Black 148sp) наполняется кровью **слева** (ниже строя) или **справа** (выше), уровень = `|cents| / 50`
- Заливка строго внутри глифов: `saveLayer` + маска текста + `BlendMode.SrcIn`, градиент крови светлая → тёмная
- Поверхность жидкости **наклоняется с гироскопом** (roll ±20°) + бегущая волна — как настоящая кровь в колбе
- В точке: заливка на 100% + вспышка кислотного + дыхание glow + вибрация
- По центру — тонкая риска-датчик; снизу — **пузырьковый уровень**: наклони телефон полубоком и лови центр
- Струны снизу идут в порядке строя, активная подсвечивается

## Меню: свайп влево

Шестерёнка теперь на фиксированном месте в шапке (контрастный кружок с кровавым бордером — видно всегда).
Но главный вход — **свайп влево** по экрану: справа выезжает шторка (строи / эффекты / темы / точность), закрытие — свайп вправо, тап по скриму или крестик. На правой кромке дышит подсказка-шеврон.

Переключатели эффектов: стробоскоп, гироскоп, вибрация.

## Сборка

Слабый ПК не мучаем — билдим через GitHub Actions:

```bash
git push origin main   # → Actions → Build APK → Artifacts: dangler-tune-apk
```

Локально (если приспичит): `./gradlew assembleDebug`.
Установка на девайс: `adb install -r app-debug.apk`, запуск: `adb shell monkey -p com.dangler.tune -c android.intent.category.LAUNCHER 1`.
