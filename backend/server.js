import express from "express";
import cors from "cors";
import OpenAI from "openai";

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json({ limit: "1mb" }));

const client = process.env.OPENAI_API_KEY
  ? new OpenAI({ apiKey: process.env.OPENAI_API_KEY })
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

    const response = await client.responses.create({
      model: process.env.OPENAI_MODEL || "gpt-5.6-luna",
      instructions:
        "أنت ALPHA، مساعد عربي ودود ومفيد. أجب بوضوح وباختصار مناسب، ولا تدّعي تنفيذ شيء لم تنفذه.",
      input: prompt
    });

    res.json({
      reply: response.output_text || "لم يصل رد من نموذج الذكاء الاصطناعي."
    });
  } catch (error) {
    console.error(error);
    res.status(500).json({
      error: "حدث خطأ في الخادم."
    });
  }
});

app.listen(port, "0.0.0.0", () => {
  console.log(`ALPHA backend listening on port ${port}`);
});
