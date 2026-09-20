import express from "express";
import cors from "cors";
import Groq from "groq-sdk";

const app = express();
const PORT = Number(process.env.PORT) || 8080;

const TEXT_MODEL = process.env.GROQ_MODEL || "openai/gpt-oss-120b";
const VISION_MODEL = process.env.GROQ_VISION_MODEL || "qwen/qwen3.6-27b";

const MAX_MESSAGES = 40;
const MAX_MESSAGE_CHARS = 30000;
const MAX_REQUEST_BYTES = "25mb";
const TEXT_MAX_TOKENS = Number(process.env.GROQ_TEXT_MAX_TOKENS) || 32768;
const VISION_MAX_TOKENS = Number(process.env.GROQ_VISION_MAX_TOKENS) || 16384;

app.disable("x-powered-by");

app.use(
  cors({
    origin: "*",
    methods: ["GET", "POST", "OPTIONS"],
    allowedHeaders: ["Content-Type"]
  })
);

app.use(express.json({ limit: MAX_REQUEST_BYTES }));

const groq = process.env.GROQ_API_KEY
  ? new Groq({ apiKey: process.env.GROQ_API_KEY })
  : null;

const SYSTEM_PROMPT = `
أنت ALPHA، مساعد ذكاء اصطناعي عربي احترافي، متعدد الاستخدامات، ومهمتك مساعدة المستخدم بأفضل إجابة عملية ممكنة.

القواعد العامة:
- افهم المحادثة كاملة، وليس آخر رسالة فقط.
- اربط العبارات مثل "عدله" و"السابق" و"نفس الكود" و"ما عجبني" بالسياق السابق.
- أجب بلغة المستخدم. العربية هي الافتراضية عندما تكون الرسالة عربية.
- كن مباشرًا وذكيًا ولا تكرر ما لا يحتاجه المستخدم.
- لا تدّعي تنفيذ أو اختبار شيء لم تنفذه فعليًا.
- إذا كانت المعلومة غير مؤكدة، وضّح ذلك بدل اختلاقها.
- إذا كان المطلوب يحتاج خطوات، رتبها بوضوح.

الكود والبرمجة:
- عندما تكتب كودًا، افصل الشرح عن الكود تمامًا.
- ضع كل كود داخل Markdown fenced code block.
- استخدم اسم اللغة الصحيح بعد فتح الصندوق: \`\`\`js أو lua أو java أو xml أو python أو bash أو json أو غيرها.
- لا تضع شرحًا عربيًا داخل صندوق الكود إلا إذا كان تعليقًا برمجيًا مطلوبًا.
- لا تكتب code fence داخل code fence.
- إذا كان الرد يحتوي عدة ملفات، اجعل لكل ملف صندوقًا مستقلًا، واكتب اسم الملف خارج الصندوق.
- لا تضع أي كلام أو عناوين مثل "ملف:" داخل صندوق الكود.
- إذا طلب المستخدم "الكود فقط" فأرسل الكود فقط، مع code fence مناسب لكل ملف.
- إذا طلب "كود كامل" أو "الملف كامل" فلا تستخدم (...) أو "أكمل الباقي" أو أجزاء مخفية.
- لا تحذف وظائف صحيحة من كود المستخدم عند التعديل إلا إذا كان هناك سبب واضح.
- لا تضف حشوًا أو آلاف الأسطر لمجرد زيادة الحجم. اجعل الكود كاملًا بقدر ما يحتاجه المشروع.
- إذا كان المشروع كبيرًا، قسّمه إلى ملفات منظمة بدل ملف واحد ضخم.

البيئات:
- Roblox / Roblox Studio / روبلوكس: استخدم Luau، وحدد Script/LocalScript/ModuleScript ومكان الملف داخل Studio عند الحاجة.
- Discord: استخدم Node.js وdiscord.js عند الحاجة.
- Android: التزم بالمشروع الحالي ولغة Java/Kotlin وGradle وXML حسب الملفات الموجودة.
- Web: HTML/CSS/JavaScript أو الإطار الذي طلبه المستخدم.
- Python: Python والمكتبات المناسبة.
- Cybersecurity: ساعد في CTF والمختبرات والأنظمة المملوكة أو المصرح باختبارها، ووجّه الطلبات الضارة إلى بدائل دفاعية.

المشاريع البرمجية:
- عند طلب مشروع حقيقي، أعطِ بنية قابلة للتشغيل، لا مجرد مثال ناقص.
- اذكر dependencies والإعدادات المطلوبة خارج صناديق الكود.
- إذا كان هناك ملف إعدادات أو متغيرات بيئية، وضّحها بوضوح.
- عند تعديل مشروع سابق، حافظ على التصميم والوظائف الموجودة ما لم يطلب المستخدم تغييرها.

الردود:
- استخدم العناوين والقوائم عند الحاجة.
- استخدم **bold** للمعلومات المهمة عندما يكون ذلك مفيدًا.
- لا تضع Markdown غير ضروري داخل الكود.
- اجعل الإجابة طبيعية ومفيدة، مثل مساعد محادثة حديث.

السلامة:
- ساعد في البرمجة والاستخدامات المشروعة.
- لا تقدم تعليمات لسرقة الحسابات أو كلمات المرور أو التوكنات، أو تجاوز المصادقة، أو اختراق أنظمة بدون تصريح، أو نشر برمجيات خبيثة.
- عند وجود طلب غير آمن، قدم بديلًا تعليميًا أو دفاعيًا مناسبًا.
`;

function cleanMessages(input) {
  if (!Array.isArray(input)) return [];

  return input
    .slice(-MAX_MESSAGES)
    .map((message) => {
      const role = message?.role === "assistant" ? "assistant" : "user";
      const content = message?.content;

      if (typeof content === "string") {
        return {
          role,
          content: content.slice(0, MAX_MESSAGE_CHARS)
        };
      }

      if (Array.isArray(content)) {
        return {
          role,
          content: content.slice(0, 12)
        };
      }

      return { role, content: "" };
    })
    .filter((message) => {
      if (typeof message.content === "string") {
        return message.content.trim().length > 0;
      }
      return Array.isArray(message.content) && message.content.length > 0;
    });
}

function containsImage(messages) {
  return messages.some(
    (message) =>
      Array.isArray(message.content) &&
      message.content.some((part) => part?.type === "image_url")
  );
}

function errorJson(res, status, message) {
  return res.status(status).json({
    success: false,
    error: message
  });
}

function buildMessages(history) {
  return [
    { role: "system", content: SYSTEM_PROMPT },
    ...history
  ];
}

function modelFor(history) {
  return containsImage(history) ? VISION_MODEL : TEXT_MODEL;
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

    if (
      history.length === 0 &&
      typeof req.body?.message === "string" &&
      req.body.message.trim()
    ) {
      history.push({
        role: "user",
        content: req.body.message.slice(0, MAX_MESSAGE_CHARS)
      });
    }

    if (history.length === 0) {
      return errorJson(res, 400, "لم تصل رسالة.");
    }

    const model = modelFor(history);
    const completion = await groq.chat.completions.create({
      model,
      messages: buildMessages(history),
      temperature: 0.7,
      max_tokens: model === VISION_MODEL ? VISION_MAX_TOKENS : TEXT_MAX_TOKENS,
      stream: false,
      ...(model.startsWith("openai/gpt-oss") ? { include_reasoning: false } : {})
    });

    const reply = completion?.choices?.[0]?.message?.content?.trim();

    if (!reply) {
      return errorJson(res, 502, "لم يصل رد من نموذج الذكاء الاصطناعي.");
    }

    return res.json({ success: true, reply, model });
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
    if (error?.status === 400) {
      return errorJson(res, 400, error?.error?.message || error?.message || "الطلب غير صالح.");
    }
    if (error?.status === 404) {
      return errorJson(res, 502, "النموذج المحدد غير متاح حاليًا.");
    }

    return errorJson(res, 500, "حدث خطأ أثناء معالجة الطلب.");
  }
});

app.post("/api/chat/stream", async (req, res) => {
  try {
    if (!groq) {
      return errorJson(res, 503, "مفتاح GROQ_API_KEY غير موجود في Railway.");
    }

    const history = cleanMessages(req.body?.messages);

    if (
      history.length === 0 &&
      typeof req.body?.message === "string" &&
      req.body.message.trim()
    ) {
      history.push({
        role: "user",
        content: req.body.message.slice(0, MAX_MESSAGE_CHARS)
      });
    }

    if (history.length === 0) {
      return errorJson(res, 400, "لم تصل رسالة.");
    }

    const model = modelFor(history);

    res.status(200);
    res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
    res.setHeader("Cache-Control", "no-cache, no-transform");
    res.setHeader("Connection", "keep-alive");
    res.setHeader("X-Accel-Buffering", "no");
    res.flushHeaders?.();

    const send = (payload) => {
      if (!res.writableEnded) {
        res.write(`data: ${JSON.stringify(payload)}\\n\\n`);
      }
    };

    const stream = await groq.chat.completions.create({
      model,
      messages: buildMessages(history),
      temperature: 0.7,
      max_tokens: model === VISION_MODEL ? VISION_MAX_TOKENS : TEXT_MAX_TOKENS,
      stream: true,
      ...(model.startsWith("openai/gpt-oss") ? { include_reasoning: false } : {})
    });

    for await (const chunk of stream) {
      if (req.destroyed || res.writableEnded) break;

      const delta = chunk?.choices?.[0]?.delta?.content;
      if (typeof delta === "string" && delta.length > 0) {
        send({ type: "delta", text: delta });
      }
    }

    send({ type: "done", model });
    res.end();
  } catch (error) {
    console.error("ALPHA stream error:", {
      status: error?.status,
      code: error?.code,
      message: error?.message
    });

    if (!res.headersSent) {
      return errorJson(res, error?.status === 429 ? 429 : 500, "حدث خطأ أثناء إنشاء الرد.");
    }

    if (!res.writableEnded) {
      res.write(`data: ${JSON.stringify({
        type: "error",
        message:
          error?.status === 429
            ? "تم الوصول إلى حد الاستخدام. حاول بعد قليل."
            : error?.status === 401
              ? "مفتاح Groq غير صالح."
              : "حدث خطأ أثناء إنشاء الرد."
      })}\\n\\n`);
      res.end();
    }
  }
});

app.use((req, res) => {
  res.status(404).json({
    success: false,
    error: "المسار غير موجود."
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`ALPHA Backend listening on port ${PORT}`);
  console.log(`Text model: ${TEXT_MODEL}`);
  console.log(`Vision model: ${VISION_MODEL}`);
});
