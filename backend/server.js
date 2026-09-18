import express from "express";
import cors from "cors";
import Groq from "groq-sdk";

const app = express();
const port = process.env.PORT || 8080;

app.use(cors());
app.use(express.json({ limit: "1mb" }));

// إعداد الاتصال باستخدام SDK الرسمي لـ Groq
const groq = process.env.GROQ_API_KEY
  ? new Groq({ apiKey: process.env.GROQ_API_KEY })
  : null;

app.get("/", (req, res) => {
  res.json({ service: "ALPHA Backend", status: "online" });
});

app.get("/health", (req, res) => {
  res.json({ ok: true, aiConfigured: Boolean(groq) });
});

app.post("/api/chat", async (req, res) => {
  try {
    const body = req.body || {};
    const rawPrompt = body.message || body.prompt || body.text || body.content || "";
    const prompt = typeof rawPrompt === "string" ? rawPrompt.trim() : "";

    if (!prompt) {
      return res.status(400).json({ error: "الرسالة فارغة" });
    }

    if (!groq) {
      return res.status(503).json({ error: "مفتاح GROQ_API_KEY غير مضبوط" });
    }

    // التجربة المتتابعة للنماذج الأساسية المتاحة حالياً في Groq
    const modelsToTry = [
      process.env.GROQ_MODEL,
      "llama-3.1-8b-instant",
      "llama-3.3-70b-versatile",
      "gemma2-9b-it"
    ].filter(Boolean);

    let completion = null;
    let usedModel = "";

    for (const model of modelsToTry) {
      try {
        completion = await groq.chat.completions.create({
          messages: [
            { role: "system", content: "أنت ALPHA، مساعد عربي ودود ومفيد. أجب بوضوح وباختصار." },
            { role: "user", content: prompt }
          ],
          model: model,
        });
        usedModel = model;
        break; // نجاح الطلب
      } catch (err) {
        console.warn(`فشل النموذج ${model}:`, err.message);
      }
    }

    if (!completion) {
      return res.status(500).json({ error: "تعذر الاتصال بجميع نماذج Groq" });
    }

    const replyText = completion.choices[0]?.message?.content || "لا يوجد رد";
    res.json({ reply: replyText, model: usedModel });

  } catch (error) {
    console.error("Groq Error:", error);
    res.status(500).json({ error: "حدث خطأ في الخادم" });
  }
});

app.listen(port, "0.0.0.0", () => {
  console.log(`ALPHA Backend active on port ${port}`);
});
