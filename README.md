# 🛡️ ReWord - Secure Zero-Knowledge Password Manager & Autofill Suite
### مدير كلمات المرور الآمن والمشفر بمبدأ المعرفة الصفرية (Zero-Knowledge) مع الملء التلقائي (Autofill)

[![Cloudflare Pages](https://img.shields.io/badge/Deployed%20on-Cloudflare%20Pages-f38020?logo=cloudflare)](https://reword-app.pages.dev/)
[![Cloudflare Workers](https://img.shields.io/badge/Backend-Cloudflare%20Workers%20KV-F38020?logo=cloudflare)](https://workers.cloudflare.com/)
[![Chrome Extension](https://img.shields.io/badge/Extension-Manifest%20V3-4285F4?logo=googlechrome)](./extension)
[![Security](https://img.shields.io/badge/Encryption-AES--256--GCM%20%2B%20PBKDF2%20(310k)-blueviolet)](#-security--cryptography-model)
[![License: MIT](https://img.shields.io/badge/License-MIT-emerald.svg)](LICENSE)

---

## 🌟 نظرة عامة (Overview)

**ReWord** هو نظام متكامل لإدارة كلمات المرور وتأمين الهوية الرقمية، مصمم وفق مبدأ **المعرفة الصفرية (Zero-Knowledge)**. يعمل بدون إنترنت (Offline-First) كـ Progressive Web App (PWA)، ويتزامن سحابياً وبشكل مشفر تماماً عبر خوادم **Cloudflare Workers & KV**، مع **إضافة متصفح (Chrome Extension - Manifest V3)** تدعم ميزة **الملء التلقائي (Autofill)** الذكي داخل صفحات تسجيل الدخول.

> [!IMPORTANT]
> **مبدأ المعرفة الصفرية (Zero-Knowledge):** لا يتم إرسال كلمة المرور الرئيسية أو أي بيانات غير مشفرة إلى أي خادم على الإطلاق. عمليات التشفير وفك التشفير تتم محلياً 100% داخل جهاز المستخدم باستخدام واجهة `Web Crypto API` القياسية.

---

## ✨ المميزات الرئيسية (Core Features)

* 🔐 **تشفير قياسي فائق الأمان (Military-Grade Encryption):**
  * تشفير متماثل **AES-256-GCM** مع Salt فريد (32 بايت) و IV عشوائي (12 بايت) لكل عملية حفظ.
  * اشتقاق المفاتيح عبر **PBKDF2 مع SHA-256 بـ 310,000 تكرار** (مطابق لتوصيات OWASP لمقاومة هجمات القوة الغاشمة).
  * حماية هويات المزامنة بتوليد SHA-256 Hashes لا يمكن عكسها (`reword:v1:${syncId}`).

* ⚡ **إضافة جوجل كروم للملء التلقائي (Chrome Extension Autofill):**
  * إضافة مبنية وفق أحدث معايير **Manifest V3**.
  * كشف فوري وذكي لحقول اسم المستخدم وكلمات المرور في أي موقع.
  * زر عائم تفاعلي يحمل شعار ReWord داخل حقل الإدخال.
  * قائمة منسدلة فورية لاقتراح الحسابات المتطابقة مع النطاق الحالي (Domain).
  * تعبئة تلقائية فائقة السرعة بضغطة زر مع دعم أطر العمل الحديثة (React, Vue, Angular).
  * قفل تلقائي ذكي بعد فترة عدم نشاط للحفاظ على الأمان.

* ☁️ **مزامنة سحابية فائقة الخفة (Cloudflare Serverless Sync):**
  * استخدام **Cloudflare Worker** كخادم وسيط بدون خوادم تقليدية.
  * تخزين البيانات المشفرة كـ Encrypted Blobs في **Cloudflare KV**.
  * حماية ضد تعارض التعديلات (Conflict Detection) بالاعتماد على الطوابع الزمنية (`timestamp`).

* 📱 **تطبيق ويب تقدمي (PWA & Offline First):**
  * تخزين محلي آمن في المتصفح باستخدام **IndexedDB**.
  * ملف `sw.js` (Service Worker) لتشغيل التطبيق والوصول للخزينة بدون اتصال بالإنترنت.
  * واجهة مستخدم عربية وأنيقة مع تصميم داكن عصري مستوحى من أفضل أدوات الإنتاجية.

* 🔄 **إدارة البيانات والنسخ الاحتياطي (Import & Export):**
  * تصدير واستيراد الخزائن المشفرة بامتداد `.reword`.
  * استيراد كلمات المرور من المتصفحات وتطبيقات مثل Bitwarden عبر ملفات CSV.
  * تصدير تقارير الجداول بتنسيق Excel XLSX.

---

## 🏗️ آلية العمل والمعمارية (How It Works)

### 1. تدفق التشفير وفك التشفير (Client-Side Encryption)

```mermaid
sequenceDiagram
    autonumber
    actor User as المستخدم
    participant Client as التطبيق / الإضافة (Client)
    participant Crypto as Web Crypto API (SubtleCrypto)
    participant Worker as Cloudflare Worker & KV

    User->>Client: إدخال كلمة المرور الرئيسية (Master Password)
    Client->>Crypto: اشتقاق المفتاح (PBKDF2 x 310,000 + SHA-256)
    Crypto-->>Client: مفتاح تشفير AES-GCM 256-bit
    Client->>Crypto: تشفير بيانات الخزينة (AES-256-GCM + Random IV)
    Crypto-->>Client: كتلة مشفرة آمنة (Ciphertext Blob)
    Client->>Worker: رفع الكتلة المشفرة فقط (POST /vault)
    Note over Worker: السيرفر يرى بيانات مشفرة فقط ولا يمتلك المفتاح أبداً!
```

---

### 2. تدفق الملء التلقائي للإضافة (Chrome Extension Autofill Pipeline)

```mermaid
graph TD
    Page["صفحة الويب (مثل facebook.com)"] -->|فحص الحقول| Content["content.js (Injected Script)"]
    Content -->|إظهار أيقونة الدرع| Badge["أيقونة ReWord في الحقل"]
    Badge -->|الضغط على الأيقونة| Request["طلب حسابات النطاق"]
    Request -->|GET_MATCHING_CREDENTIALS| Background["background.js (Service Worker)"]
    Background -->|فك التشفير في الذاكرة| Match["تطابق النطاق (Matching Accounts)"]
    Match -->|إرجاع الحسابات| Content
    Content -->|عرض القائمة المنسدلة| Dropdown["قائمة الحسابات المتاحة"]
    Dropdown -->|اختيار الحساب| Inject["حقن اسم المستخدم وكلمة المرور + Dispatch Events"]
    Inject -->|تعبئة الحقول| Page
```

---

## 📂 هيكل المشروع (Project Structure)

```text
├── core.js                 # المحرك الأساسي (التشفير، IndexedDB، المزامنة، منطق الخزينة)
├── ui.js                   # واجهة المستخدم وتفاعلات القوائم والمودالات والبحث
├── index.html              # الصفحة الرئيسية لتطبيق الويب (PWA)
├── style.css               # الأنماط والتصميم الداكن المتجاوب
├── sw.js                   # Service Worker للعمل أوفلاين كـ PWA
├── manifest.json           # إعدادات تطبيق الويب التقدمي (PWA Manifest)
├── icon.png                # أيقونة التطبيق الرسمية
│
├── worker/
│   └── index.js            # كود Cloudflare Worker لمعالجة المزامنة المشفرة
├── wrangler.toml           # ملف إعدادات Wrangler وربط KV Namespace
│
├── extension/              # إضافة جوجل كروم (Manifest V3)
│   ├── manifest.json       # تعريف الإضافة والأذونات
│   ├── background.js       # مشغل الخدمة في الخلفية (Session Cache, Sync, Auto-lock)
│   ├── content.js          # سكربت الكشف والملء التلقائي داخل صفحات الويب
│   ├── content.css         # أنماط الأيقونة والقائمة المنسدلة العائمة
│   ├── crypto.js           # وحدة التشفير المتوافقة بنسبة 100% مع core.js
│   ├── popup.html          # واجهة نافذة الإضافة السريعة
│   ├── popup.js            # منطق واجهة الإضافة وتعبئة الصفحة النشطة بضغطة زر
│   ├── popup.css           # أنماط الواجهة السريعة
│   └── icons/              # أيقونات الإضافة بأحجامها المختلفة
│
├── ANDROID_AUTOFILL_PLAN.md# وثيقة وخطة تطبيق الأندرويد القادم (Autofill Service & Biometrics)
├── knowledge.owl           # خريطة المعرفة والأنطولوجيا الرسمية للمشروع (OntoLink Graph)
└── README.md               # الدليل الشامل والمفصل للمشروع
```

---

## 🚀 التثبيت والتشغيل المحلي (Getting Started)

### 1. تشغيل موقع الويب محلياً:
بما أن المشروع يعتمد على معايير الويب الصافية (Vanilla HTML/CSS/JS)، يمكنك تشغيله باستخدام أي خادم محلي:
```bash
npx serve .
# أو
python -m http.server 8080
```
افتح المتصفح على `http://localhost:8080`.

---

### 2. تثبيت إضافة كروم (Chrome Extension):
1. افتح متصفح Chrome أو أي متصفح مبني على Chromium (Edge, Brave, Opera).
2. ادخل إلى الرابط: `chrome://extensions/`.
3. قم بتفعيل **Developer Mode** (وضع المطور) من الزاوية العلوية.
4. اضغط على **Load unpacked** (تحميل حزمة غير مضغوطة).
5. اختر مجلد `extension` من داخل مسار هذا المشروع.
6. افتح الإضافة، أدخل الـ Sync ID وكلمة المرور لفك التشفير وابدأ بالملء التلقائي في أي موقع!

---

### 3. نشر الـ Worker على Cloudflare:
إذا أردت نشر الـ Worker بنفسك على حسابك في Cloudflare:
```bash
npx wrangler login
npx wrangler kv namespace create vaults
# ضع الـ ID الناتج في wrangler.toml
npx wrangler deploy
```

---

## 🗺️ خريطة الأنطولوجيا وخطة التطوير القادمة (Roadmap & Ontology)

المشروع مسجل ومنظم بالكامل عبر نظام **OntoLink** المعرفي في [`knowledge.owl`](./knowledge.owl).

### 📱 مهمة أندرويد القادمة (Android Autofill & Biometrics):
* [x] **توثيق الخطة التقنية:** [`ANDROID_AUTOFILL_PLAN.md`](./ANDROID_AUTOFILL_PLAN.md).
* [ ] **`task-android-setup`:** إعداد مشروع أندرويد (Kotlin / Jetpack Compose).
* [ ] **`task-android-crypto`:** تشفير AES-256-GCM و PBKDF2 على أندرويد ومزامنة الـ Worker.
* [ ] **`task-android-autofill`:** برمجة `AutofillService` لنظام أندرويد للملء في كل التطبيقات والمواقع.
* [ ] **`task-android-biometrics`:** تكامل التحقق ببصمة الإصبع أو الوجه (`BiometricPrompt` + Keystore).
* [ ] **`task-android-ui-sync`:** بناء واجهات إدخال الـ Sync ID والربط السحابي.

---

## 🔒 ترخيص الاستخدام (License)
هذا المشروع مرخص تحت رخصة **MIT** - راجع ملف الترخيص لمزيد من التفاصيل.
