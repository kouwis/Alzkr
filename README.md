# الذِّكر — Alzkr

تطبيق أندرويد مجاني ومفتوح المصدر للقرآن الكريم (والأذكار قريبًا)، لله ومن غير إعلانات ولا تتبع.

## التحميل
آخر نسخة تجريبية في صفحة [Releases](../../releases): حمّل ملف `alzkr-*.apk` وافتحه على الموبايل.

> كل خطوط المصحف جوه التطبيق، فبيشتغل من غير إنترنت.

## مصادر البيانات
- تخطيط مصحف المدينة (15 سطر) ورموز خطوط QCF: [zonetecde/mushaf-layout](https://github.com/zonetecde/mushaf-layout)
- خطوط مجمع الملك فهد (صفحات المصحف QCF V1، وإطارات السور والبسملة QCF_BSML، وخط عثمان طه النسخ): [nuqayah/qpc-fonts](https://github.com/nuqayah/qpc-fonts)
- نص القرآن، والتفسير الميسر، وترجمة Sahih International: [AlQuran Cloud](https://alquran.cloud)

## البناء
GitHub Actions بيبني الـ APK مع كل push على `main` (شوف `.github/workflows/build.yml`).
محليًا: `python3 scripts/build_data.py` ثم `gradle :app:assembleRelease`.
