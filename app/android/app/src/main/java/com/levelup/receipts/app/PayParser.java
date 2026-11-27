package com.levelup.receipts.app;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 카드 결제 문자·알림 글에서 금액·날짜·가게·카드 끝4자리를 뽑는다. 이름·누적금액은 버린다. */
public final class PayParser {
    public static final class Info {
        public String kind = "approve"; // approve | cancel
        public String company = "";     // 신한, KB ...
        public String card = "";        // 끝 4자리
        public long amount;
        public String date = "";        // yyyy-MM-dd
        public String time = "";        // HH:mm
        public String place = "";
        public String install = "";     // "3개월" 또는 ""
        public boolean noDate;          // 글에 날짜가 없어 받은 시각을 씀
        public String key() { return kind + "|" + card + "|" + amount + "|" + date + "|" + time; }
        @Override public String toString() { return kind + " " + company + " " + card + " " + amount + " " + date + " " + time + " [" + place + "] " + install; }
    }

    private static final Pattern SHINHAN = Pattern.compile(
        "신한카드\\((\\d{4})\\)(승인|취소)\\s+\\S+\\s+([\\d,]+)원\\(\\s*([^)]*?)\\s*\\)\\s*(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s*(.*?)\\s*(?:누적\\s*-?[\\d,]+원)?\\s*$");
    private static final Pattern KBPAY = Pattern.compile(
        "\\[KB Pay[^\\]]*\\]\\s*(?:신용|체크)?\\s*(\\d{4})\\s+(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s+([\\d,]+)원\\s+(.*?)\\s+(승인|취소)");
    // 우리카드(WON): [일시불.승인(6485)]11/27 17:14 33,320원 / 누적:18,970,990원 가맹점
    private static final Pattern WOORI = Pattern.compile(
        "\\[\\s*(일시불|\\d+개월)?[.\\s]*(승인|취소)\\((\\d{4})\\)\\s*\\]\\s*(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s+([\\d,]+)원\\s*(?:/\\s*누적\\s*:?\\s*-?[\\d,]+원)?\\s*(.*)$");
    // 결제 알림 앱: 33,320원 결제 우리카드 | JB㈜(일시불)  (날짜가 없어 받은 시각을 쓴다)
    private static final Pattern WALLET = Pattern.compile(
        "([\\d,]+)원\\s*(결제\\s*취소|결제|취소)\\s+(\\S*?)카드\\s*\\|\\s*(.+?)\\s*(?:\\((일시불|\\d+개월)\\))?\\s*$");
    private static final Pattern GENERIC_AMT = Pattern.compile("([\\d]{1,3}(?:,\\d{3})+|\\d{3,})원");
    private static final Pattern GENERIC_DT = Pattern.compile("(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})");
    private static final Pattern GENERIC_CARD = Pattern.compile("(?:\\((\\d{4})\\)|(?:신용|체크)\\s*(\\d{4}))");
    private static final String[] COMPANIES = {"신한","KB","국민","삼성","현대","롯데","하나","우리","NH","농협","BC","비씨","IBK","기업","씨티","카카오","토스"};

    private PayParser() {}

    public static Info parse(String raw) { return parse(raw, Calendar.getInstance()); }

    public static Info parse(String raw, Calendar now) {
        if (raw == null) return null;
        String t = raw.replace("[Web발신]", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty() || t.contains("거절")) return null;
        Matcher m = SHINHAN.matcher(t);
        if (m.find()) {
            Info i = new Info();
            i.company = "신한"; i.card = m.group(1);
            i.kind = "취소".equals(m.group(2)) ? "cancel" : "approve";
            i.amount = num(m.group(3));
            String ins = m.group(4).replaceAll("\\s+", "");
            i.install = ins.equals("일시불") ? "" : ins;
            i.date = date(now, m.group(5), m.group(6)); i.time = time(m.group(7), m.group(8));
            i.place = m.group(9).trim();
            return ok(i);
        }
        m = KBPAY.matcher(t);
        if (m.find()) {
            Info i = new Info();
            i.company = "KB"; i.card = m.group(1);
            i.date = date(now, m.group(2), m.group(3)); i.time = time(m.group(4), m.group(5));
            i.amount = num(m.group(6)); i.place = m.group(7).trim();
            i.kind = "취소".equals(m.group(8)) ? "cancel" : "approve";
            return ok(i);
        }
        m = WOORI.matcher(t);
        if (m.find()) {
            Info i = new Info();
            i.company = co(t, "우리"); i.card = m.group(3);
            i.kind = "취소".equals(m.group(2)) ? "cancel" : "approve";
            String ins = m.group(1) == null ? "" : m.group(1);
            i.install = ins.equals("일시불") ? "" : ins;
            i.date = date(now, m.group(4), m.group(5)); i.time = time(m.group(6), m.group(7));
            i.amount = num(m.group(8));
            i.place = m.group(9).replaceAll("\\.{2,}|…", "").trim();
            return ok(i);
        }
        m = WALLET.matcher(t);
        if (m.find()) {
            Info i = new Info();
            i.amount = num(m.group(1));
            i.kind = m.group(2).contains("취소") ? "cancel" : "approve";
            i.company = m.group(3).isEmpty() ? "" : co(m.group(3) + "카드", m.group(3).replaceAll("체크|신용", ""));
            i.place = m.group(4).trim();
            String ins = m.group(5) == null ? "" : m.group(5);
            i.install = ins.equals("일시불") ? "" : ins;
            i.date = String.format("%04d-%02d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
            i.time = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
            i.noDate = true;
            return ok(i);
        }
        // 그 밖의 카드사: 승인/취소 + 금액 + 날짜가 모두 있을 때만
        boolean approve = t.contains("승인"), cancel = t.contains("취소");
        if (!(approve || cancel) || !(t.contains("카드") || t.contains("Pay") || t.contains("페이"))) return null;
        Matcher a = GENERIC_AMT.matcher(t), d = GENERIC_DT.matcher(t);
        if (!a.find() || !d.find()) return null;
        Info i = new Info();
        i.kind = cancel ? "cancel" : "approve";
        i.amount = num(a.group(1));
        i.date = date(now, d.group(1), d.group(2)); i.time = time(d.group(3), d.group(4));
        Matcher c = GENERIC_CARD.matcher(t);
        if (c.find()) i.card = c.group(1) != null ? c.group(1) : c.group(2);
        for (String co : COMPANIES) if (t.contains(co)) { i.company = co.equals("국민") ? "KB" : co; break; }
        return ok(i);
    }

    private static String co(String t, String def) {
        for (String c : COMPANIES) if (t.contains(c + "카드")) return c.equals("국민") ? "KB" : c;
        return def;
    }

    private static Info ok(Info i) { return i.amount > 0 && i.amount < 100000000L ? i : null; }
    private static long num(String s) { try { return Long.parseLong(s.replace(",", "")); } catch (Exception e) { return 0; } }
    private static String time(String h, String mi) { return String.format("%02d:%s", Integer.parseInt(h), mi); }
    private static String date(Calendar now, String mo, String d) {
        int y = now.get(Calendar.YEAR), M = Integer.parseInt(mo), D = Integer.parseInt(d);
        if (M > now.get(Calendar.MONTH) + 1 + 1) y -= 1; // 1월에 받은 12월 결제
        return String.format("%04d-%02d-%02d", y, M, D);
    }
}
