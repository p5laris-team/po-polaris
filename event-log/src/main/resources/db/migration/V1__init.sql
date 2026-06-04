CREATE TABLE event_logs (
    id BIGSERIAL PRIMARY KEY, -- DB 내부 식별자. event_logs 테이블의 기본키이며 외부 이벤트 식별자로 사용하지 않는다.

    event_id UUID NOT NULL, -- 이벤트 고유 식별자. 클라이언트 또는 서버에서 생성한 UUID이며 중복 저장 방지와 멱등성 보장에 사용한다.
    event_type VARCHAR(100) NOT NULL, -- 이벤트 종류. 예: USER_SIGNED_UP, MISSION_OFFERED, MISSION_REJECTED, MISSION_COMPLETED, AD_IMPRESSION, AD_CLICKED 등

    source_service VARCHAR(50) NULL, -- 이벤트를 발생시킨 서비스 또는 모듈 이름. 예: user-service, mission-service, character-service, item-service, client 등

    user_id BIGINT NULL, -- 로그인한 사용자 ID. 비로그인 상태에서 발생한 이벤트이거나 사용자를 식별할 수 없는 경우 NULL일 수 있다.
    anonymous_id VARCHAR(100) NULL, -- 비로그인 사용자 또는 브라우저/기기를 식별하기 위한 익명 ID. 회원가입 전 행동, 공유 유입, 광고 노출 분석 등에 사용한다.

    ref_type VARCHAR(50) NULL, -- 이벤트가 참조하는 대상 타입. 예: USER, MISSION, CHARACTER, ITEM, NOTIFICATION, AD, SHARE 등
    ref_id BIGINT NULL, -- 이벤트가 참조하는 대상 ID. ref_type에 해당하는 도메인 객체의 식별자이다.

    properties_json JSONB NULL, -- 이벤트 자체의 상세 속성 JSON. 예: missionTemplateId, category, rewardStarPiece, elapsedSeconds, adProvider, shareChannel 등
    context_json JSONB NULL, -- 이벤트 발생 환경 JSON. 예: screenName, platform, appVersion, userAgent, deviceType, locale 등

    occurred_at TIMESTAMP NOT NULL, -- 이벤트가 실제로 발생한 시각. 클라이언트 또는 이벤트 발생 서비스 기준의 시간이다.
    created_at TIMESTAMP NOT NULL DEFAULT now(), -- event_logs 테이블에 row가 생성된 시각. DB insert 시각이다.

    CONSTRAINT uk_event_logs_event_id UNIQUE (event_id)
);