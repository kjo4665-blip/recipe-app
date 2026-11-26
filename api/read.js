// Vercel Serverless Function: 영수증 사진을 받아 Claude Haiku로 읽는다.
// 요청자가 Supabase에 로그인한 사용자인지 먼저 확인한다 (토큰 검증).

const SB_URL = process.env.SUPABASE_URL || "https://ahgnlnyfyyoietjyjnhc.supabase.co";
const SB_KEY = process.env.SUPABASE_ANON_KEY || "sb_publishable__jO-LKY2dxdi2QXonfzaIQ_dIQd8G9c";
const AI_KEY = process.env.ANTHROPIC_API_KEY;

const PROMPT =
  "이 영수증 사진에서 정보를 읽어 JSON 객체 하나만 출력하세요. 설명이나 다른 글자는 쓰지 마세요.\n" +
  '형식: {"date":"YYYY-MM-DD","item":"품목 요약(한글 12자 이내)","amount":실제 결제 합계 금액(정수, 원),"store":"상호"}\n' +
  '날짜를 읽을 수 없으면 date는 null. 품목이 여러 개면 대표 품목 한두 개로 요약. 영수증이 아니면 {"error":"not_receipt"}만 출력.';

module.exports = async (req, res) => {
  if (req.method !== "POST") return res.status(405).json({ error: "method" });

  // 1) 로그인 토큰 확인
  const auth = req.headers.authorization || "";
  if (!auth.startsWith("Bearer ")) return res.status(401).json({ error: "unauthorized" });
  const u = await fetch(`${SB_URL}/auth/v1/user`, {
    headers: { apikey: SB_KEY, authorization: auth },
  });
  if (!u.ok) return res.status(401).json({ error: "unauthorized" });

  // 2) 이미지 확인
  const image = req.body && req.body.image;
  if (typeof image !== "string" || image.length === 0 || image.length > 4_000_000) {
    return res.status(400).json({ error: "bad_image" });
  }
  if (!AI_KEY) return res.status(500).json({ error: "server_not_configured" });

  // 3) Claude 호출
  const r = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": AI_KEY,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "claude-haiku-5-5",
      max_tokens: 300,
      messages: [
        {
          role: "user",
          content: [
            { type: "image", source: { type: "base64", media_type: "image/jpeg", data: image } },
            { type: "text", text: PROMPT },
          ],
        },
      ],
    }),
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) return res.status(502).json({ error: "upstream" });
  return res.status(200).json(j);
};
