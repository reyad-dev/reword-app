# 📱 خطة تنفيذ تطبيق أندرويد لـ ReWord مع الملء التلقائي الشامل (Android Autofill)

## 🎯 الهدف
تطوير تطبيق أندرويد لـ **ReWord** يعمل على مستوى النظام بأكمله (System-wide Autofill):
1. يظهر تلقائياً كخيار ملء عند الوقوف على أي حقل اسم مستخدم أو كلمة مرور في **جميع التطبيقات** (تطبيقات البنوك، فيسبوك، جوجل، تويتر، إلخ) وفي **متصفحات الهاتف** (Chrome, Edge, Samsung Internet, Firefox).
2. التحقق الآمن عبر **بصمة الإصبع أو الوجه (Biometric Authentication)** أو كلمة المرور الرئيسية كبديل.
3. التزامن السلس والمشفر مع نفس الـ Cloudflare Worker (`reword-vault-sync`).

---

## 🏗️ البنية التقنية (Architecture)

```
┌────────────────────────────────────────────────────────┐
│               Android OS Autofill Framework            │
│  (Triggered on focused username/password input fields) │
└───────────────────────────┬────────────────────────────┘
                            │ System IPC
                            ▼
┌────────────────────────────────────────────────────────┐
│           ReWord Autofill Service (Kotlin)             │
│   - Identifies package name / domain (e.g. twitter)    │
│   - Prompts for BiometricPrompt (بصمة الإصبع)           │
│   - Decrypts matching credential via Keystore & PBKDF2 │
│   - Returns Dataset to Android Autofill Manager        │
└───────────────────────────┬────────────────────────────┘
                            │ Local Encrypted Storage / Worker Sync
                            ▼
┌────────────────────────────────────────────────────────┐
│     Cloudflare Worker: reword-vault-sync.workers.dev    │
│           (Encrypted Vault Blob Sync - AES-256-GCM)    │
└────────────────────────────────────────────────────────┘
```

---

## 🛠️ تفاصيل التنفيذ والتقنيات

### 1. إطار الملء التلقائي (`AutofillService`):
* استخدام واجهة أندرويد الرسمية: `android.service.autofill.AutofillService` (متوافقة من Android 8.0 Oreo فما فوق).
* تعريف خدمة الملء في `AndroidManifest.xml` مع الإذن `android.permission.BIND_AUTOFILL_SERVICE`.
* معالجة دالة `onFillRequest()` لتحليل شجرة الواجهة (`AssistStructure`) واستخراج معرّفات الحقول (مثل `AUTOFILL_HINT_USERNAME`, `AUTOFILL_HINT_PASSWORD`).

### 2. المصادقة البيومترية (بصمة الإصبع / الوجه):
* استخدام مكتبة `androidx.biometric.BiometricPrompt`.
* عند ظهور طلب الملء أو فتح الخزينة، يُطلب من المستخدم لمس مستشعر البصمة.
* يتم تأمين مفتاح فك التشفير بواسطة **Android Keystore (Hardware-backed KeyStore)**، بحيث لا يمكن استخدام المفتاح إلا بعد نجاح التحقق بالبصمة.
* إمكانية الرجوع إلى كلمة المرور الرئيسية في حال عدم دعم البصمة أو فشلها.

### 3. التشفير والمزامنة:
* مطابقة 100% لمعيار التشفير المتبع في الويب:
  - **AES-256-GCM**
  - **PBKDF2 with HMAC-SHA256 (310,000 iterations)**
* سحب الخزينة المشفرة وحفظها من خلال نفس الـ Cloudflare Worker:
  `https://reword-vault-sync.moasaar6.workers.dev/vault`

---

## 📋 خطة العمل عند البدء في المهمة:
1. **إعداد المشروع:** إنشاء مشروع أندرويد (باستخدام Kotlin / Jetpack Compose).
2. **برمجة فك وتشفير الخزينة:** مطابقة كود `CV` في الجافاسكريبت إلى Kotlin باستخدام `javax.crypto`.
3. **تطبيق الـ AutofillService:** التعامل مع طلبات النظام وتمرير الحقول لملئها.
4. **تكامل BiometricPrompt:** ربط البصمة بفك تشفير الخزينة بأمان.
5. **شاشات الإعداد والمزامنة:** شاشة بسيطة لإدخال الـ `Sync ID` و `Worker URL` لأول مرة.
