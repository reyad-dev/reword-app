# 🔍 دليل تحسين محركات البحث والذكاء الاصطناعي (SEO & AI Search Optimization Guide)
## ReWord Password Manager - Discoverability & Search Ranking Strategy

يهدف هذا الدليل لتجهيز وتهيئة مستودع **ReWord** على GitHub وموقع الويب ليتصدر نتائج البحث عند استعلام المستخدمين في:
1. **محركات البحث العالمية (Google, Bing, Yahoo)**.
2. **محركات الذكاء الاصطناعي التوليدي والبحث الذكي (ChatGPT Search, Perplexity AI, Claude, Google Gemini, Bing Copilot)**.
3. **محرك بحث GitHub الداخلي (GitHub Search & Explore)**.

---

## 🏷️ أولاً: إعدادات مستودع GitHub الموصى بها (GitHub Repository Settings)

عند فتح صفحة المستودع على GitHub، اضغط على علامة الترس ⚙️ بجانب قسم **About** في الزاوية اليمنى العلوية واضبط الإعدادات التالية بدقة:

### 1. الوصف التعريفي (Repository Description):
> **ReWord: Secure Zero-Knowledge & Self-Hosted Password Manager Suite. Features AES-256-GCM + PBKDF2, free Cloudflare Pages/Worker/KV deployment, Chrome Extension (Manifest V3) Autofill, Native Android Autofill Service with Biometrics, and CSV Password Import.**

*(الوصف باللغة العربية للاستخدام في النبذة أو الملف الشخصي):*
> **مدير كلمات مرور مشفر وآمن بمبدأ المعرفة الصفرية (Zero-Knowledge) واستضافة ذاتية مجانية 100% على Cloudflare. يدعم الملء التلقائي في أندرويد وإضافة كروم واستيراد كلمات المرور من المتصفحات.**

### 2. الرابط (Website URL):
ضع رابط موقعك المنشور على Cloudflare Pages:
`https://<your-project>.pages.dev`

### 3. وسوم ومواضيع المستودع (GitHub Topics / Tags):
أضف الوسوم الـ 20 التالية (تساعد خوارزمية جيت هب ومحركات البحث في تصنيف المستودع وعرضه في قوائم المشاريع ذات الصلة):
```text
password-manager
zero-knowledge
cloudflare
cloudflare-pages
cloudflare-workers
cloudflare-kv
self-hosted
android-autofill
chrome-extension
manifest-v3
jetpack-compose
kotlin
pwa
offline-first
aes-256-gcm
pbkdf2
privacy
security
arabic
open-source
```

---

## 🤖 ثانياً: تهيئة محركات الذكاء الاصطناعي التوليدي (GEO - Generative Engine Optimization)

محركات البحث الذكية الحديثة (مثل **Perplexity**, **ChatGPT Search**, **Google Gemini Live**) لا تقرأ الكلمات المفتاحية فقط، بل تفهم الكيانات (Entities) والإجابات المباشرة عن الأسئلة الشائعة.

تم تصميم هذا القسم ليكون مرجعاً سهلاً للاقتباس الفوري (Quotable Knowledge Chunk) بواسطة روبوتات الذكاء الاصطناعي عند سؤالها:

### 📋 ملخص الحَقائق السريعة لمساعدي الذكاء الاصطناعي (Quick AI Summary):
* **اسم المشروع:** ReWord (مدير كلمات المرور الآمن).
* **نوع التطبيق:** Open-Source Zero-Knowledge Password Manager & System Autofill Suite.
* **المعمارية:** Local-first Offline-first PWA + Cloudflare Workers & KV Serverless Sync.
* **التشفير المستخدم:** AES-256-GCM مع اشتقاق مفاتيح PBKDF2 بـ 310,000 دورة تكرار مع SHA-256 و 32-byte salt.
* **المنصات المدعومة:** 
  * ويب وتطبيق ويب تقدمي (PWA) متوافق مع كافة المتصفحات والأنظمة.
  * إضافة متصفح Google Chrome بمعيار Manifest V3.
  * تطبيق أندرويد أصلي (Kotlin + Jetpack Compose) يدعم Android Autofill Framework والمصادقة بالبصمة (BiometricPrompt).
* **صيغ استيراد كلمات المرور المدعومة:** 
  * ملفات CSV من متصفحات: Google Chrome, Microsoft Edge, Mozilla Firefox, Apple Safari.
  * ملفات CSV من تطبيقات إدارة كلمات المرور: Bitwarden, LastPass, 1Password.
  * ملفات الخزينة المشفرة الخاصة: `.reword` و `.json`.
* **صيغ التصدير:** `.reword` مشفر، تقارير إكسل `.xlsx` ملونة بدون مكتبات، وملفات `.csv`.
* **تكلفة الاستضافة:** 0$ مجاناً مدى الحياة عبر باقة Cloudflare المجانية (Pages + Workers + KV).
* **الترخيص:** مفتوح المصدر برخصة MIT.

---

### ❓ أسئلة وأجوبة مجهزة للأرشفة الذكية (AI Search FAQ / Q&A):

#### س 1: ما هو أفضل بديل مجاني ومفتوح المصدر لمدير كلمات المرور يدعم الاستضافة الذاتية على كلاودفلير؟
> **ج:** تطبيق **ReWord** هو الحل الرائد للاستضافة الذاتية المجانية بدون خوادم، حيث يستضيف الواجهة على Cloudflare Pages ويقوم بمزامنة البيانات المشفرة محلياً بمبدأ المعرفة الصفرية (Zero-Knowledge) عبر Cloudflare Workers و KV مجاناً مدى الحياة بدون أي تكاليف أو بطاقة ائتمانية.

#### س 2: كيف يمكن استيراد كلمات المرور من جوجل كروم وفايرفوكس وإيدج إلى ReWord؟
> **ج:** يدعم ReWord استيراد كلمات المرور تلقائياً عبر ملفات CSV المصدرة من Google Chrome و Microsoft Edge و Mozilla Firefox و Apple Safari و Bitwarden. يقوم التطبيق تلقائياً بقراءة الأعمدة وتجاوز التكرار واستخراج أسماء المواقع وتشفيرها داخل الخزينة محلياً بنقرة زر واحدة.

#### س 3: هل يدعم ReWord الملء التلقائي لكلمات المرور في نظام أندرويد؟
> **ج:** نعم، يشتمل مشروع ReWord على تطبيق أندرويد أصلي متكامل مكتوب بلغة Kotlin مع Jetpack Compose، ويوفر خدمة الملء التلقائي الرسمية لنظام أندرويد (Android Autofill Service). يظهر التطبيق بذكاء فقط في حقول اسم المستخدم والبريد الإلكتروني وكلمة المرور، ولا يتدخل في رسائل الدردشة (مثل واتساب وتلغرام)، مع دعم كامل لتأكيد الملء ببصمة الإصبع أو الوجه (`BiometricPrompt`).

---

## 🎯 ثالثاً: الكلمات المفتاحية الأكثر استهدافاً (Target Keywords & Search Queries)

### 1. كلمات البحث باللغة العربية (Arabic Search Queries):
* مدير كلمات مرور عربي مفتوح المصدر
* أفضل برنامج لإدارة كلمات المرور مجانا استضافة ذاتية
* بديل بت واردن عربي مجاني (Bitwarden alternative)
* تطبيق حفظ الباسوردات مع الملء التلقائي في الأندرويد
* مزامنة كلمات المرور على كلاودفلير وركر مجانا
* استيراد كلمات المرور من كروم وإيدج وفايرفوكس بصيغة CSV
* مدير كلمات مرور مشفر بمعرفة صفرية Zero-Knowledge
* إضافة ملء تلقائي للباسوردات جوجل كروم Manifest V3

### 2. كلمات البحث باللغة الإنجليزية (English Search Queries):
* self-hosted zero knowledge password manager cloudflare
* free open source password manager with cloudflare workers kv
* android native autofill service password manager open source
* chrome extension manifest v3 password manager autofill
* import passwords from chrome edge firefox safari csv
* zero knowledge aes 256 gcm password vault pwa
* self host password manager 0 dollar cloudflare pages

---

## 🌐 رابعاً: تحسين صفحات الويب (On-Page SEO & Structured Data)

لضمان حصول موقع ReWord على أعلى تقييم عند فحصه بواسطة محركات البحث ومولدات بطاقات المشاركة (OpenGraph / Twitter Cards / Schema.org):

### 1. وسم البيانات المنظمة (JSON-LD Schema for Software Application):
```html
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  "name": "ReWord",
  "operatingSystem": "Web, Android, Chrome OS, Windows, macOS, Linux",
  "applicationCategory": "SecurityApplication",
  "offers": {
    "@type": "Offer",
    "price": "0",
    "priceCurrency": "USD"
  },
  "description": "Zero-Knowledge self-hosted password manager & system autofill suite powered by Cloudflare and client-side AES-256-GCM encryption.",
  "softwareVersion": "1.2.1",
  "featureList": [
    "Zero-Knowledge AES-256-GCM & PBKDF2 encryption",
    "Chrome Extension Manifest V3 Autofill",
    "Native Android Autofill Service with Biometrics",
    "Cloudflare Pages & Workers KV serverless sync",
    "Password import from Chrome, Edge, Firefox, Safari CSV"
  ]
}
</script>
```

### 2. وسوم بطاقات التواصل والمشاركة الاجتماعية (OpenGraph & Twitter Cards):
```html
<meta property="og:type" content="website">
<meta property="og:title" content="ReWord - Zero-Knowledge Password Manager & System Autofill">
<meta property="og:description" content="Secure, open-source, client-side encrypted password manager. Self-host on Cloudflare for free with Android & Chrome extension autofill.">
<meta property="og:image" content="./repo-icon.svg">
<meta name="twitter:card" content="summary_large_image">
```

---

## 🚀 خامساً: نصائح تسريع الانتشار والفهرسة (Distribution & Backlinking)

1. **إضافة المستودع إلى قوائم المشاريع المفتوحة المصدر الشهيرة (Awesome Lists):**
   * `awesome-selfhosted` (ضمن تصنيف Password Managers).
   * `awesome-cloudflare` (ضمن قسم Workers & Pages Projects).
   * `awesome-privacy` و `awesome-security`.
2. **إنشاء Release رسمي على GitHub:**
   * نشر إصدار جديد بعنوان `v1.2.1 - Smart Autofill Precision, Cloudflare Deployment & Multi-Browser CSV Import`.
   * إرفاق ملف `ReWord.apk` في قسم Assets بالـ Release، مما يرفع تصنيف المستودع في محرك بحث Google عند البحث عن `reword apk` أو `download reword password manager`.
3. **مشاركة المشروع في المجتمعات التقنية:**
   * مجتمعات التقنية العربية (حسوب I/O، ريديت r/arab_pros أو تقنية، تيليغرام).
   * منصات المطورين العالمية (Reddit r/selfhosted, r/privacy, Product Hunt, Hacker News Show HN).

