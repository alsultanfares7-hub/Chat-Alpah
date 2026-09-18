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

// المسار الرئيسي للتحقق من عمل الخادم
app.get("/", (req, res) => {
  res.json({
    service: "ALPHA Backend",
    status: "online",
    timestamp: new Date().toISOString()
  });
});

// مسار فحص الحالة (Health Check)
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
    const prompt = typeof req.body?.message === "string" ? req.body.message.trim() : "";

    if (!prompt) {
      return res.status(400).json({
        error: "message_required",
        message: "الرجاء إدخال نص الرسالة."
      });
    }

    if (!client) {
      return res.status(503).json({
        error: "service_unconfigured",
        message: "لم يتم ضبط مفتاح Groq API في المتغيرات بعد."
      });
    }

    // تحديد النموذج الافتراضي المعتمد
    const activeModel = process.env.GROQ_MODEL || "llama-3.1-8b-instant";

    const response = await client.chat.completions.create({
      model: activeModel,
      messages: [
        {
          role: "system",
          content: "أنت ALPHA، مساعد ذكي وودود ومفيد يجيب باللغة العربية بأسلوب واضح ومباشر."
        },
        {
          role: "user",
          content: prompt
        }
      ],
      temperature: 0.7,
      max_tokens: 1024
    });

    const replyText = response.choices[0]?.message?.content?.trim();

    return res.json({
      reply: replyText || "لم يتم استلام رد مناسب من النموذج.",
      modelUsed: activeModel
    });

  } catch (error) {
    console.error("Groq API Error Detail:", {
      status: error?.status,
      message: error?.message,
      code: error?.code
    });

    // معالجة الأخطاء الشائعة واستجابة واضحة
    if (error?.status === 401) {
      return res.status(401).json({ error: "invalid_api_key", message: "مفتاح API غير صالح." });
    }

    if (error?.status === 404 || error?.code === "model_not_found" || error?.code === "model_decommissioned") {
      return res.status(400).json({ error: "invalid_model", message: "النموذج المحدد غير متاح حالياً." });
    }

    return res.status(500).json({
      error: "internal_server_error",
      message: "حدث خطأ غير متوقع في الخادم."
    });
  }
});

// تشغيل السيرفر
app.listen(port, "0.0.0.0", () => {
  console.log(`🚀 ALPHA Backend is running on port ${port}`);
});
