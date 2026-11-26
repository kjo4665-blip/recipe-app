# 영수증 정리

영수증 사진을 찍으면 날짜·금액·품목을 읽어 정리하는 개인용 웹앱입니다.

- `index.html`: 화면 전체 (로그인, 영수증 찍기, 월별 내역, 복사·CSV 저장)
- `api/read.js`: Vercel 서버 함수. 로그인 확인 후 Claude Haiku로 영수증을 읽습니다.
- `manifest.json`, `sw.js`: 안드로이드 크롬에서 "홈 화면에 추가"로 앱처럼 설치하기 위한 파일
- 데이터는 Supabase `receipts` 테이블에 저장되며, RLS로 본인 데이터만 접근합니다.

## 환경변수 (Vercel)

- `ANTHROPIC_API_KEY`: Anthropic API 키 (비밀 값)
- `SUPABASE_URL`, `SUPABASE_ANON_KEY`: 없으면 코드의 기본값을 씁니다.
