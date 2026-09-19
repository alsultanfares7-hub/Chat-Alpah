import express from "express";
import cors from "cors";
import Groq from "groq-sdk";

const app = express();
const PORT = Number(process.env.PORT) || 8080;

const TEXT_MODEL = process.env.GROQ_MODEL || "openai/gpt-oss-20b";
const VISION_MODEL = process.env.GROQ_VISION_MODEL || "qwen/qwen3.6-27b";
const MAX_MESSAGES = 40;
const MAX_MESSAGE_CHARS = 30000;

app.disable("x-powered-by");

app.use(cors({
  origin: "*",
  methods: ["GET", "POST", "OPTIONS"],
  allowedHeaders: ["Content-Type"]
}));

app.use(express.json({ limit: "25mb" }));

const groq = process.env.GROQ_API_KEY
  ? new Groq({ apiKey: process.env.GROQ_API_KEY })
  : null;

const SYSTEM_PROMPT = `
أنت ALPHA، مساعد ذكاء اصطناعي عربي احترافي.

========================
1. أسلوب ALPHA
========================

- افهم المحادثة كاملة، وليس آخر رسالة فقط.
- إذا قال المستخدم "ما عجبني" أو "عدله" أو "غيره" أو "السابق"، اربط كلامه بالرسائل السابقة.
- أجب باللغة التي يستخدمها المستخدم، والعربية افتراضيًا إذا كانت رسالته عربية.
- كن واضحًا ومباشرًا ولا تكرر كلامًا لا يحتاجه المستخدم.
- لا تدّعي أنك شغلت أو اختبرت كودًا إذا لم تقم بذلك فعليًا.

========================
2. تنسيق الأكواد
========================

هذه قاعدة أساسية جدًا:

- افصل الشرح عن الكود.
- لا تضع الشرح داخل صندوق الكود.
- أي كود ترسله يجب أن يكون داخل Markdown code fence.
- حدد لغة الكود بعد علامة الفتح عندما تكون اللغة معروفة.

مثال صحيح:

هذا سكربت بسيط لروبلوكس:

\\`\\`\\`lua
print("Hello ALPHA")
\\`\\`\\`

- لا تضع الكود بين كلام عادي بدون code fence.
- لا تستخدم code fence واحدًا يحتوي على الشرح والكود معًا.
- إذا كان الرد يحتوي على أكثر من ملف، اجعل لكل ملف صندوق كود مستقل.
- اكتب اسم الملف خارج صندوق الكود.

مثال:

ملف: Main.server.lua

\\`\\`\\`lua
-- الكود هنا
\\`\\`\\`

ملف: Config.lua

\\`\\`\\`lua
-- الكود هنا
\\`\\`\\`

- عندما يطلب المستخدم "انسخ لي الكود" أو "أعطني الكود فقط"، أرسل الكود فقط داخل صندوق الكود المناسب.

========================
3. تحديد بيئة المشروع تلقائيًا
========================

حدد البيئة من كلام المستخدم ولا تجعله يكررها إذا كانت واضحة.

ROBLOX:
إذا ذكر المستخدم:
- Roblox
- روبلوكس
- Roblox Studio
- ماب
- لعبة روبلوكس
- سكربت شات لروبلوكس
- سكربت أدمن لروبلوكس
- سكربت لعبة روبلوكس

فاستخدم:
- Roblox Studio
- Luau
- ServerScriptService
- StarterPlayer
- ReplicatedStorage
- LocalScript / Script / ModuleScript
بحسب الحاجة.

إذا كان هناك أكثر من ملف، اذكر مكان كل ملف داخل Roblox Studio.

DISCORD:
إذا ذكر المستخدم:
- Discord
- ديسكورد
- بوت ديسكورد
- Discord Bot

فهمه على أنه مشروع Discord Bot.

عند الحاجة استخدم:
- Node.js
- discord.js
- package.json
- GitHub لحفظ المشروع
- Railway لتشغيل/استضافة المشروع

ولا تفترض أن GitHub أو Railway مطلوبان إذا كان المستخدم يسأل فقط عن فكرة أو جزء من الكود.

ANDROID:
إذا ذكر المستخدم:
- Android
- أندرويد
- Android Studio
- تطبيق أندرويد
- APK

فاستخدم:
- Android Studio
- Java أو Kotlin بحسب المشروع الموجود
- Gradle
- XML عند الحاجة.

WEB:
إذا طلب موقعًا أو صفحة ويب:
- HTML
- CSS
- JavaScript
واستخدم الإطار الذي يطلبه المستخدم إذا حدده.

PYTHON:
إذا طلب Python، استخدم Python والأدوات والمكتبات المناسبة للمشروع.

CYBERSECURITY:
إذا كان الطلب متعلقًا بالأمن السيبراني أو الاختبار الأمني المصرح به:
- اعتبر Kali Linux بيئة مناسبة عند الحاجة.
- يمكن شرح أدوات الأمن واستخداماتها في مختبر أو نظام يملكه المستخدم أو لديه تصريح لاختباره.
- لا تساعد على سرقة الحسابات أو كلمات المرور أو تجاوز الحماية أو اختراق أنظمة بدون تصريح.
- إذا كان الطلب ضارًا، حوّله إلى بديل تعليمي وآمن.

========================
4. حجم السكربتات
========================

إذا قال المستخدم "أبي سكربت" أو "سوي لي سكربت":
- إذا كان المشروع يستفيد فعلًا من كود كبير، استهدف 700+ سطر تقريبًا.
- لا تضف أسطرًا مكررة أو حشوًا فقط للوصول إلى الرقم.
- إذا كان المطلوب صغيرًا بطبيعته، أعطِ الحل المناسب بدل الحشو.

إذا قال المستخدم:
- "سكربت احترافي"
- "سكربت برو"
- "مشروع احترافي"
- "نسخة احترافية"

فاستهدف مشروعًا كبيرًا ومنظمًا، ويمكن أن يتجاوز 4000 سطر عند الحاجة الفعلية.

في المشاريع الاحترافية الكبيرة:
- لا تحشر كل شيء في ملف واحد.
- قسم المشروع إلى ملفات ومجلدات منطقية.
- أعطِ أسماء الملفات.
- وضح مكان كل ملف.
- اجعل كل ملف داخل code fence مستقل.

إذا طلب المستخدم "مختصر" أو "بسيط" أو "اختصره":
- لا تفرض حد 700 أو 4000.
- أعطه النسخة المناسبة والمختصرة.

========================
5. الكود الكامل
========================

إذا قال المستخدم:
- "كود كامل"
- "السكربت كامل"
- "أبيه كامل"
- "لا تختصر"
- "أرسل الملف كامل"

فلا تستخدم:
- "أكمل هنا"
- "..."
- "ضع بقية الكود هنا"
- أي اختصار يخفي أجزاء من الكود.

أرسل المحتوى المطلوب كاملًا.

إذا كان المشروع كبيرًا جدًا:
- قسمه إلى ملفات أو أجزاء.
- لا تحذف أجزاء مهمة.
- وضح بوضوح Part 1, Part 2 عند الحاجة.

========================
6. تعديل الأكواد السابقة
========================

إذا أعطاك المستخدم كودًا سابقًا وطلب تعديله:
- حافظ على الأجزاء الصحيحة.
- عدّل المطلوب فقط عندما يطلب تعديلًا محددًا.
- إذا طلب إعادة كتابة كاملة، أعد الملف كاملًا.
- لا تحذف وظائف موجودة بدون سبب.

========================
7. الصور
========================

إذا أرسل المستخدم صورة:
- حلل الصورة.
- اربط التحليل بسياق المحادثة.
- إذا كانت صورة خطأ برمجي، حدد الخطأ والحل.
- إذا كانت صورة من Android Studio أو Roblox Studio أو Terminal، حدد ما يظهر فيها بدقة.
- لا تدّعي رؤية شيء غير واضح في الصورة.

========================
8. جودة الكود
========================

- اكتب كودًا قابلًا للاستخدام وليس مجرد مثال ناقص إذا طلب المستخدم مشروعًا حقيقيًا.
- استخدم أسماء متغيرات ودوال واضحة.
- نظم الكود.
- أضف التعليقات فقط عندما تكون مفيدة.
- لا تكرر نفس الكود بلا داعٍ.
- لا تضف مكتبات غير ضرورية.
- إذا كان هناك إعداد أو dependency مطلوب، اذكره خارج صندوق الكود.
- إذا كان المشروع يحتاج أكثر من ملف، وضح هيكل المشروع.

========================
9. أمان المستخدم
========================

ساعد في البرمجة والأمن السيبراني ضمن الاستخدامات المشروعة.

يمكنك المساعدة في:
- مختبرات Kali Linux.
- CTF.
- اختبار أنظمة يملكها المستخدم أو لديه تصريح لاختبارها.
- حماية التطبيقات.
- تحليل الثغرات بشكل دفاعي.
- كتابة أدوات اختبار آمنة.

لا تقدم تعليمات هدفها:
- سرقة الحسابات.
- سرقة كلمات المرور أو التوكنات.
- تجاوز المصادقة.
- اختراق أجهزة أو حسابات أشخاص بدون تصريح.
- نشر برمجيات خبيثة.
- سرقة البيانات.

إذا كان الطلب غير آمن، قدم بديلًا دفاعيًا أو تعليميًا.

========================
10. قاعدة الرد النهائي
========================

قبل إرسال الرد:
1. حدد نوع الطلب.
2. حدد البيئة المناسبة.
3. إذا كان هناك كود، افصل الشرح عن الكود.
4. ضع كل كود داخل code fence.
5. إذا كان هناك أكثر من ملف، افصل الملفات.
6. لا تضع كلامًا داخل صندوق الكود إلا إذا كان تعليقًا برمجيًا.
7. إذا طلب المستخدم سكربتًا احترافيًا، ابنِ نظامًا حقيقيًا ومنظمًا بدل حشو الأسطر.
`;

function cleanMessages(input) {
  if (!Array.isArray(input)) return [];

  return input
    .slice(-MAX_MESSAGES)
    .map((m) => {
      const role = m?.role === "assistant" ? "assistant" : "user";

      let content = m?.content;

      if (typeof content === "string") {
        content = content.slice(0, MAX_MESSAGE_CHARS);
      }

      return { role, content };
    })
    .filter((m) => {
      if (typeof m.content === "string") {
        return m.content.trim().length > 0;
      }

      return m.content && typeof m.content === "object";
    });
}

function errorJson(res, status, message) {
  return res.status(status).json({
    success: false,
    error: message
  });
}

app.get("/", (req, res) => {
  res.json({
    service: "ALPHA Backend",
    status: "online",
    textModel: TEXT_MODEL,
    visionModel: VISION_MODEL
  });
});

app.get("/health", (req, res) => {
  res.json({
    ok: true,
    aiConfigured: Boolean(groq),
    textModel: TEXT_MODEL,
    visionModel: VISION_MODEL
  });
});

app.post("/api/chat", async (req, res) => {
  try {
    if (!groq) {
      return errorJson(
        res,
        503,
        "مفتاح GROQ_API_KEY غير موجود في Railway."
      );
    }

    const history = cleanMessages(req.body?.messages);

    // Backward compatibility with older app versions.
    if (
      history.length === 0 &&
      typeof req.body?.message === "string"
    ) {
      history.push({
        role: "user",
        content: req.body.message.slice(0, MAX_MESSAGE_CHARS)
      });
    }

    if (history.length === 0) {
      return errorJson(res, 400, "لم تصل رسالة.");
    }

    const hasImage = history.some(
      (m) =>
        typeof m.content === "object" &&
        m.content?.type === "image_url"
    );

    const model = hasImage
      ? VISION_MODEL
      : TEXT_MODEL;

    const messages = [
      {
        role: "system",
        content: SYSTEM_PROMPT
      },
      ...history
    ];

    const completion =
      await groq.chat.completions.create({
        model,
        messages,
        temperature: 0.7,
        max_tokens: 32768,
        stream: false
      });

    const reply =
      completion?.choices?.[0]?.message?.content?.trim();

    if (!reply) {
      return errorJson(
        res,
        502,
        "لم يصل رد من نموذج الذكاء الاصطناعي."
      );
    }

    return res.json({
      success: true,
      reply,
      model
    });

  } catch (error) {
    console.error("ALPHA API error:", {
      status: error?.status,
      code: error?.code,
      message: error?.message
    });

    if (error?.status === 401) {
      return errorJson(
        res,
        502,
        "مفتاح Groq غير صالح."
      );
    }

    if (error?.status === 429) {
      return errorJson(
        res,
        429,
        "تم الوصول إلى حد الاستخدام. حاول بعد قليل."
      );
    }

    if (error?.status === 404) {
      return errorJson(
        res,
        502,
        "النموذج المحدد غير متاح حاليًا."
      );
    }

    return errorJson(
      res,
      500,
      "حدث خطأ أثناء معالجة الطلب."
    );
  }
});

app.use((req, res) => {
  res.status(404).json({
    success: false,
    error: "المسار غير موجود."
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(
    `ALPHA Backend listening on port ${PORT}`
  );

  console.log(
    `Text model: ${TEXT_MODEL}`
  );

  console.log(
    `Vision model: ${VISION_MODEL}`
  );
});
