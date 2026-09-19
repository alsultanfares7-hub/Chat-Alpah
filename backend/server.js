import express from "express";
import cors from "cors";
import Groq from "groq-sdk";

const app = express();

const PORT = Number(process.env.PORT) || 8080;
const MODEL = process.env.GROQ_MODEL || "openai/gpt-oss-20b";
const MAX_MESSAGE_LENGTH = 12000;

app.disable("x-powered-by");

app.use(
  cors({
    origin: "*",
    methods: ["GET", "POST", "OPTIONS"],
    allowedHeaders: ["Content-Type", "Authorization"],
  })
);

app.use(
  express.json({
    limit: "1mb",
  })
);

// ==============================
// Groq Configuration
// ==============================

const GROQ_API_KEY = process.env.GROQ_API_KEY;

const groq = GROQ_API_KEY
  ? new Groq({
      apiKey: GROQ_API_KEY,
    })
  : null;

// ==============================
// System Prompt
// ==============================

const SYSTEM_PROMPT = `
أنت ALPHA، مساعد ذكاء اصطناعي عربي ودود واحترافي.

قواعد الرد:
- أجب باللغة التي يستخدمها المستخدم.
- إذا كان المستخدم يتحدث بالعربية، استخدم العربية بشكل طبيعي وواضح.
- كن مفيدًا ومباشرًا.
- لا تطيل الرد بدون حاجة.
- استخدم نقاطًا أو خطوات عندما يكون ذلك مفيدًا.
- إذا لم تكن متأكدًا من معلومة، وضّح ذلك بدل اختلاقها.
- لا تدّعي أنك نفذت شيئًا لم تنفذه.
`;

// ==============================
// Helpers
// ==============================

function getUserMessage(body) {
  const raw =
    body?.message ??
    body?.prompt ??
    body?.text ??
    body?.content ??
    "";

  if (typeof raw !== "string") {
    return "";
  }

  return raw.trim();
}

function sendError(res, status, message, details = undefined) {
  const response = {
    error: message,
  };

  if (process.env.NODE_ENV !== "production" && details) {
    response.details = details;
  }

  return res.status(status).json(response);
}

// ==============================
// Health Routes
// ==============================

app.get("/", (req, res) => {
  res.json({
    service: "ALPHA Backend",
    status: "online",
    model: MODEL,
    timestamp: new Date().toISOString(),
  });
});

app.get("/health", (req, res) => {
  res.json({
    ok: true,
    service: "ALPHA Backend",
    aiConfigured: Boolean(groq),
    model: MODEL,
    timestamp: new Date().toISOString(),
  });
});

// ==============================
// Chat API
// ==============================

app.post("/api/chat", async (req, res) => {
  const requestId = `${Date.now()}-${Math.random()
    .toString(36)
    .slice(2, 8)}`;

  try {
    const prompt = getUserMessage(req.body);

    // Empty message
    if (!prompt) {
      return sendError(res, 400, "الرسالة فارغة.");
    }

    // Message too long
    if (prompt.length > MAX_MESSAGE_LENGTH) {
      return sendError(
        res,
        413,
        `الرسالة طويلة جدًا. الحد الأقصى ${MAX_MESSAGE_LENGTH} حرف.`
      );
    }

    // API key missing
    if (!groq) {
      console.error(`[${requestId}] GROQ_API_KEY is missing.`);

      return sendError(
        res,
        503,
        "خدمة الذكاء الاصطناعي غير مهيأة في الخادم."
      );
    }

    console.log(
      `[${requestId}] Chat request received | model=${MODEL} | length=${prompt.length}`
    );

    // Groq request
    const completion = await groq.chat.completions.create({
      model: MODEL,

      messages: [
        {
          role: "system",
          content: SYSTEM_PROMPT,
        },
        {
          role: "user",
          content: prompt,
        },
      ],

      // لا نحتاج إظهار reasoning للمستخدم
      include_reasoning: false,

      // تحكم أفضل في طول الرد
      max_completion_tokens: 2048,

      // رد متوازن وغير عشوائي جدًا
      temperature: 0.7,

      // لا نستخدم streaming حاليًا
      stream: false,
    });

    const reply =
      completion?.choices?.[0]?.message?.content?.trim() || "";

    if (!reply) {
      console.error(`[${requestId}] Empty response from Groq.`);

      return sendError(
        res,
        502,
        "لم يتم الحصول على رد من نموذج الذكاء الاصطناعي."
      );
    }

    console.log(
      `[${requestId}] Chat request completed successfully.`
    );

    return res.json({
      success: true,
      reply,
      model: MODEL,
      requestId,
    });
  } catch (error) {
    console.error(`[${requestId}] Groq error:`, {
      message: error?.message,
      status: error?.status,
      code: error?.code,
    });

    // Authentication / API key
    if (error?.status === 401) {
      return sendError(
        res,
        502,
        "مفتاح Groq غير صالح أو منتهي."
      );
    }

    // Permission / model access
    if (error?.status === 403) {
      return sendError(
        res,
        502,
        "لا توجد صلاحية لاستخدام نموذج الذكاء الاصطناعي المحدد."
      );
    }

    // Rate limit
    if (error?.status === 429) {
      return sendError(
        res,
        429,
        "الخدمة مشغولة حاليًا. حاول مرة أخرى بعد قليل."
      );
    }

    // Model not found
    if (error?.status === 404) {
      return sendError(
        res,
        502,
        "نموذج الذكاء الاصطناعي غير متاح حاليًا."
      );
    }

    // Other errors
    return sendError(
      res,
      500,
      "حدث خطأ أثناء معالجة الطلب.",
      error?.message
    );
  }
});

// ==============================
// 404 Handler
// ==============================

app.use((req, res) => {
  return res.status(404).json({
    error: "المسار غير موجود.",
    path: req.originalUrl,
  });
});

// ==============================
// Global Error Handler
// ==============================

app.use((error, req, res, next) => {
  console.error("Unhandled server error:", error);

  if (res.headersSent) {
    return next(error);
  }

  return res.status(500).json({
    error: "حدث خطأ غير متوقع في الخادم.",
  });
});

// ==============================
// Start Server
// ==============================

app.listen(PORT, "0.0.0.0", () => {
  console.log("=================================");
  console.log("       ALPHA Backend Online      ");
  console.log("=================================");
  console.log(`Port: ${PORT}`);
  console.log(`Model: ${MODEL}`);
  console.log(`AI configured: ${Boolean(groq)}`);
  console.log("=================================");
});
