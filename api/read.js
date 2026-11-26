// Vercel Serverless Function: 영수증 사진을 받아 Claude Haiku로 읽고, 결과를 JSON으로 정리해 돌려준다.
// 요청자가 Supabase에 로그인한 사용자인지 먼저 확인한다 (토큰 검증).

const SB_URL = process.env.SUPABASE_URL || "https://ahgnlnyfyyoietjyjnhc.supabase.co";
const SB_KEY = process.env.SUPABASE_ANON_KEY || "sb_publishable__jO-LKY2dxdi2QXonfzaIQ_dIQd8G9c";
const AI_KEY = process.env.ANTHROPIC_API_KEY;

const PROMPT =
  "이 영수증 사진에서 정보를 읽어 JSON 객체 하나만 출력하세요. 코드블록, 설명, 다른 글자는 쓰지 마세요.\n" +
  '형식: {"date":"YYYY-MM-DD","item":"품목 요약(한글 12자 이내)","amount":12000,"store":"상호"}\n' +
  "amount는 실제 결제 합계 금액이며 쉼표·원 기호 없이 숫자만 씁니다.\n" +
  '날짜를 읽을 수 없으면 date는 null. 품목이 여러 개면 대표 품목 한두 개로 요약. 영수증이 아니면 {"error":"not_receipt"}만 출력.';

// 사진 형식을 base64 앞부분으로 판별 (jpeg/png/webp/gif)
function mediaType(b64) {
  if (b64.startsWith("/9j/")) return "image/jpeg";
  if (b64.startsWith("iVBOR")) return "image/png";
  if (b64.startsWith("UklGR")) return "image/webp";
  if (b64.startsWith("R0lGOD")) return "image/gif";
  return null;
}

// 모델이 쓴 글에서 JSON 객체를 최대한 너그럽게 꺼낸다
function extract(text) {
  const t = String(text || "").replace(/```(?:json)?/gi, "");
  const start = t.indexOf("{");
  const end = t.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  let s = t.slice(start, end + 1);
  try { return JSON.parse(s); } catch (e) {}
  // "amount": 12,000 처럼 숫자 안 쉼표, 끝 쉼표, 작은따옴표 보정
  s = s.replace(/(\d),(?=\d{3}\b)/g, "$1").replace(/,\s*}/g, "}").replace(/'/g, '"');
  try { return JSON.parse(s); } catch (e) {}
  return null;
}

module.exports = async (req, res) => {
  try {
    if (req.method !== "POST") return res.status(405).json({ error: "method" });

    // 1) 로그인 토큰 확인
    const auth = req.headers.authorization || "";
    if (!auth.startsWith("Bearer ")) return res.status(401).json({ error: "unauthorized" });
    const u = await fetch(`${SB_URL}/auth/v1/user`, { headers: { apikey: SB_KEY, authorization: auth } });
    if (!u.ok) return res.status(401).json({ error: "unauthorized" });

    // 2) 이미지 확인
    const image = req.body && req.body.image;
    if (typeof image !== "string" || image.length === 0 || image.length > 4_000_000) {
      return res.status(400).json({ error: "bad_image", message: "size" });
    }
    const mt = mediaType(image);
    if (!mt) return res.status(400).json({ error: "bad_image", message: "format" });
    if (!AI_KEY) return res.status(500).json({ error: "server_not_configured" });

    // 3) Claude 호출
    const r = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: { "x-api-key": AI_KEY, "anthropic-version": "2023-06-01", "content-type": "application/json" },
      body: JSON.stringify({
        model: "claude-haiku-5-5",
        max_tokens: 1024,
        messages: [{ role: "user", content: [
          { type: "image", source: { type: "base64", media_type: mt, data: image } },
          { type: "text", text: PROMPT },
        ] }],
      }),
    });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) {
      const msg = (j && j.error && j.error.message) || "";
      return res.status(502).json({ error: "upstream", status: r.status, message: msg.slice(0, 300) });
    }

    // 4) 결과 정리
    const text = (j.content || []).filter((c) => c.type === "text").map((c) => c.text).join("");
    const o = extract(text);
    if (!o) {
      return res.status(422).json({ error: "parse", stop: j.stop_reason || "", message: text.slice(0, 200) });
    }
    if (o.error) return res.status(200).json({ error: "not_receipt" });
    const amount = Math.round(Number(String(o.amount == null ? "" : o.amount).replace(/[^\d.-]/g, ""))) || 0;
    const date = /^\d{4}-\d{2}-\d{2}$/.test(o.date || "") ? o.date : null;
    return res.status(200).json({
      date, amount,
      item: String(o.item || o.store || "영수증").slice(0, 20),
      store: String(o.store || "").slice(0, 40),
    });
  } catch (e) {
    return res.status(500).json({ error: "crash", message: String((e && e.message) || e).slice(0, 200) });
  }
};
