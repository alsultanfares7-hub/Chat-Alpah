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

قواعد مهمة:
- افهم المحادثة كاملة، وليس آخر رسالة فقط.
- إذا قال المستخدم "ما عجبني" أو "عدله" أو "غير الكلمة" أو "السابق"، اربط كلامه بالرسائل السابقة.
- عند تعديل كود سابق، حافظ على المطلوب الصحيح وعدّل الجزء المطلوب فقط، إلا إذا طلب المستخدم إعادة كتابة كاملة.
- إذا طلب المستخدم سكربتًا أو مشروعًا برمجيًا، اكتب كودًا عمليًا ومنظمًا، واذكر اسم الملف عند الحاجة.
- لا تختصر الكود أو تستبدله بعبارات مثل "أكمل هنا" إذا طلب المستخدم الكود كاملًا.
- إذا كان الكود طويلًا جدًا بحيث لا يمكن إرساله في رد واحد، وضّح ذلك واقسمه إلى أجزاء متتابعة مع أسماء الملفات وأرقام الأجزاء.
- إذا أرسل المستخدم صورة، حلل محتواها وربطه بسياق المحادثة.
- لا تدّعي أنك شغلت أو اختبرت كودًا إذا لم تقم بذلك فعليًا.
- أجب باللغة التي يستخدمها المستخدم، والعربية افتراضيًا إذا كانت رسالته عربية.
`;

function cleanMessages(input) {
  if (!Array.isArray(input)) return [];

  return input
    .slice(-MAX_MESSAGES)
    .map((m) => {
      const role = m?.role === "assistant" ? "assistant" : "user";
      const content = typeof m?.content === "string"
        ? m.content.slice(0, MAX_MESSAGE_CHARS)
        : "";
      return { role, content };
    })
    .filter((m) => m.content.trim().length > 0);
}

function errorJson(res, status, message) {
  return res.status(status).json({ success: false, error: message });
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
      return errorJson(res, 503, "مفتاح GROQ_API_KEY غير موجود في Railway.");
    }

    const history = cleanMessages(req.body?.messages);

    // Backward compatibility with older app versions.
    if (history.length === 0 && typeof req.body?.message === "string") {
      history.push({
        role: "user",
        content: req.body.message.slice(0, MAX_MESSAGE_CHARS)
      });
    }

    if (history.length === 0) {
      return errorJson(res, 400, "لم تصل رسالة.");
    }

    const hasImage = history.some(
      (m) => typeof m.content === "object" && m.content?.type === "image_url"
    );

    // The Android client sends normal text history. Image requests can send
    // a final user message with multimodal content.
    const model = hasImage ? VISION_MODEL : TEXT_MODEL;

    const messages = [
      { role: "system", content: SYSTEM_PROMPT },
      ...history
    ];

    const completion = await groq.chat.completions.create({
      model,
      messages,
      temperature: 0.7,
      max_tokens: 32768,
      stream: false
    });

    const reply = completion?.choices?.[0]?.message?.content?.trim();

    if (!reply) {
      return errorJson(res, 502, "لم يصل رد من نموذج الذكاء الاصطناعي.");
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
      return errorJson(res, 502, "مفتاح Groq غير صالح.");
    }

    if (error?.status === 429) {
      return errorJson(res, 429, "تم الوصول إلى حد الاستخدام. حاول بعد قليل.");
    }

    if (error?.status === 404) {
      return errorJson(res, 502, "النموذج المحدد غير متاح حاليًا.");
    }

    return errorJson(res, 500, "حدث خطأ أثناء معالجة الطلب.");
  }
});

app.use((req, res) => {
  res.status(404).json({ success: false, error: "المسار غير موجود." });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`ALPHA Backend listening on port ${PORT}`);
  console.log(`Text model: ${TEXT_MODEL}`);
  console.log(`Vision model: ${VISION_MODEL}`);
});
