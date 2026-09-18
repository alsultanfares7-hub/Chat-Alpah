import express from "express";
import cors from "cors";
import OpenAI from "openai";

const app = express();
const port = process.env.PORT || 8080;

// Middleware
app.use(cors());
app.use(express.json({ limit: "1mb" }));

// إعداد الاتصال بـ Groq API عبر SDK المتوافق مع OpenAI
const groqApiKey = process.env.GROQ_API_KEY;
const client = groqApiKey
  ? new OpenAI({
      apiKey: groqApiKey,
      baseURL: "https://api.groq.com/openai/v1"
    })
  : null;

// قائمة النماذج المعتمدة بالترتيب (في حال فشل الأوّل يتم الانتقال للثاني تلقائياً)
const DEFAULT_MODELS = [
  "llama-3.1-8b-instant",
  "llama3-70b-8192",
  "mixtral-8x7b-32768"
];

// فحص حالة الخادم
app.get("/", (req, res) => {
  res.json({
    service: "ALPHA Backend API",
    status: "online",
    timestamp: new Date().toISOString()
  });
});

// فحص الجاهزية والاتصال
app.get("/health", (req, res) => {
  res.json({
    ok: true,
    aiConfigured: Boolean(client),
    provider: "Groq"
  });
});

// مسار المحادثة الرئيسي
app.post("/api/chat", async (req, res) => {
  try {
    // مرونة في قراءة الرسالة بجميع المسميات المحتملة من تطبيق الأندرويد
    const body = req.body || {};
    const rawPrompt = body.message || body.prompt || body.text || body.content || "";
    const prompt = typeof rawPrompt === "string" ? rawPrompt.trim() : "";

    if (!prompt) {
      return res.status(400).json({
        error: "message_required",
        message: "الرسالة المرسلة فارغة."
      });
    }

    if (!client) {
      return res.status(503).json({
        error: "service_unconfigured",
        message: "لم يتم ضبط مفتاح Groq API في المتغيرات بعد."
      });
    }

    // تحديد النماذج المراد تجربتها
    const candidateModels = process.env.GROQ_MODEL 
      ? [process.env.GROQ_MODEL, ...DEFAULT_MODELS] 
      : DEFAULT_MODELS;

    let replyText = null;
    let usedModel = null;
    let lastError = null;

    // محاولة الاتصال بالنماذج المتاحة بالتتابع تلقائياً
    for (const modelName of candidateModels) {
      try {
        const response = await client.chat.completions.create({
          model: modelName,
          messages: [
            {
              role: "system",
              content: "أنت ALPHA، مساعد عربي ودود ومفيد. أجب بوضوح وباختصار مناسب."
            },
            {
              role: "user",
              content: prompt
            }
          ],
          temperature: 0.7,
          max_tokens: 1024
        });

        replyText = response.choices[0]?.message?.content?.trim();
        usedModel = modelName;
        break; // نجاح الطلب، الخروج من التكرار
      } catch (err) {
        console.warn(`Model ${modelName} failed or unavailable. Trying fallback...`, err?.message);
        lastError = err;
      }
    }

    if (!replyText) {
      throw lastError || new Error("جميع النماذج المتاحة لم تستجب.");
    }

    return res.json({
      reply: replyText,
      model: usedModel
    });

  } catch (error) {
    console.error("Groq Final Error Detail:", {
      status: error?.status,
      message: error?.message,
      code: error?.code
    });

    if (error?.status === 401) {
      return res.status(401).json({
        error: "unauthorized",
        message: "مفتاح API غير صالح أو غير مصرح له."
      });
    }

    return res.status(500).json({
      error: "internal_server_error",
      message: "حدث خطأ في الخادم أثناء معالجة الطلب."
    });
  }
});

// تشغيل الخادم
app.listen(port, "0.0.0.0", () => {
  console.log(`🚀 ALPHA Professional Backend running on port ${port}`);
});
