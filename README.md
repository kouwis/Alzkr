# الذِّكر — Alzkr

تطبيق أندرويد مجاني ومفتوح المصدر للقرآن الكريم (والأذكار قريبًا)، لله ومن غير إعلانات ولا تتبع.

## التحميل
آخر نسخة تجريبية في صفحة [Releases](../../releases): حمّل ملف `alzkr-*.apk` وافتحه على الموبايل.

> أول مرة تفتح صفحة في المصحف، التطبيق بينزّل خط الصفحة دي (مرة واحدة بس)، فمحتاج إنترنت أول مرة.

## مصادر البيانات
- تخطيط مصحف المدينة (15 سطر) ورموز خطوط QCF V2: [zonetecde/mushaf-layout](https://github.com/zonetecde/mushaf-layout)
- خطوط صفحات المصحف QCF V2 من مجمع الملك فهد: [nuqayah/qpc-fonts](https://github.com/nuqayah/qpc-fonts)
- نص القرآن، والتفسير الميسر، وترجمة Sahih International: [AlQuran Cloud](https://alquran.cloud)

## البناء
GitHub Actions بيبني الـ APK مع كل push على `main` (شوف `.github/workflows/build.yml`).
محليًا: `python3 scripts/build_data.py` ثم `gradle :app:assembleRelease`.
