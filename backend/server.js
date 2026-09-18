import express from "express";
import cors from "cors";
import OpenAI from "openai";

const app = express();
const port = process.env.PORT || 8080;

app.use(cors());
app.use(express.json({ limit: "1mb" }));

// إعداد الاتصال بـ Groq باستخدام مكتبة OpenAI
const client = process.env.GROQ_API_KEY
  ? new OpenAI({
      apiKey: process.env.GROQ_API_KEY,
      baseURL: "https://api.groq.com/openai/v1"
    })
  : null;

app.get("/", (req, res) => {
  res.json({
    name: "ALPHA Backend",
    status: "online"
  });
});

app.get("/health", (req, res) => {
  res.json({
    ok: true,
    aiConfigured: Boolean(client)
  });
});

app.post("/api/chat", async (req, res) => {
  try {
    const prompt = typeof req.body?.message === "string"
      ? req.body.message.trim()
      : "";

    if (!prompt) {
      return res.status(400).json({
        error: "message is required"
      });
    }

    if (!client) {
      return res.status(503).json({
        error: "AI backend is not configured yet."
      });
    }

    // استدعاء نموذج Groq السريع والمجاني
    const response = await client.chat.completions.create({
      model: "llama-3.1-8b-instant",
      messages: [
        {
          role: "system",
          content: "أنت ALPHA، مساعد عربي ودود ومفيد. أجب بوضوح وباختصار مناسب، ولا تدّعي تنفيذ شيء لم تنفذه."
        },
        {
          role: "user",
          content: prompt
        }
      ]
    });

    const replyText = response.choices[0]?.message?.content;

    res.json({
      reply: replyText || "لم يصل رد من نموذج الذكاء الاصطناعي."
    });
  } catch (error) {
    console.error("Groq Error:", error);
    res.status(500).json({
      error: "حدث خطأ في الخادم."
    });
  }
});

app.listen(port, "0.0.0.0", () => {
  console.log(`ALPHA backend listening on port ${port}`);
});
