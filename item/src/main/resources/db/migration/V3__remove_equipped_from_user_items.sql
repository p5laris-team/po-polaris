-- user_items 테이블에서 equipped 컬럼 제거
-- 장착 정보는 character 모듈의 user_characters.equipped_skin_id 가 단일 소스입니다.
-- 클라이언트가 두 값을 비교하여 장착 여부를 판단합니다 (방식 3).
ALTER TABLE user_items DROP COLUMN IF EXISTS equipped;
