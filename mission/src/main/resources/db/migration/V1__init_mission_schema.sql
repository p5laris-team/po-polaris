CREATE TABLE mission_templates (
    id BIGSERIAL PRIMARY KEY,
    base_title VARCHAR(100) NOT NULL,
    base_description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    difficulty VARCHAR(30) NOT NULL,
    reward_star_piece INT NOT NULL DEFAULT 10 CHECK (reward_star_piece >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    fallback_character_message TEXT NOT NULL,
    fallback_question TEXT NOT NULL,
    fallback_completion_response TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mission_templates_category CHECK (
        category IN (
            'BASIC_ROUTINE',
            'SPACE_RESET',
            'BODY_CARE',
            'OUTDOOR_LIGHT',
            'MIND_RECORD',
            'REST_RECOVERY',
            'SOCIAL_LIGHT'
        )
    ),
    CONSTRAINT chk_mission_templates_difficulty CHECK (
        difficulty IN ('EASY', 'NORMAL')
    )
);

CREATE INDEX idx_mission_templates_active_category_difficulty
    ON mission_templates(active, category, difficulty);

CREATE TABLE user_missions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    mission_template_id BIGINT,
    ai_generation_id BIGINT,
    mission_date DATE NOT NULL,
    stack_order INT NOT NULL CHECK (stack_order > 0),
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    character_message TEXT NOT NULL,
    completion_character_response TEXT,
    category VARCHAR(50) NOT NULL,
    difficulty VARCHAR(30) NOT NULL,
    reward_star_piece INT NOT NULL CHECK (reward_star_piece >= 0),
    status VARCHAR(30) NOT NULL,
    offered_at TIMESTAMP,
    completion_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    rejected_at TIMESTAMP,
    expired_at TIMESTAMP,
    idempotency_key VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_missions_template
        FOREIGN KEY (mission_template_id)
        REFERENCES mission_templates(id)
        ON DELETE SET NULL,
    CONSTRAINT uk_user_missions_user_date_stack
        UNIQUE (user_id, mission_date, stack_order),
    CONSTRAINT uk_user_missions_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT chk_user_missions_category CHECK (
        category IN (
            'BASIC_ROUTINE',
            'SPACE_RESET',
            'BODY_CARE',
            'OUTDOOR_LIGHT',
            'MIND_RECORD',
            'REST_RECOVERY',
            'SOCIAL_LIGHT'
        )
    ),
    CONSTRAINT chk_user_missions_difficulty CHECK (
        difficulty IN ('EASY', 'NORMAL')
    ),
    CONSTRAINT chk_user_missions_status CHECK (
        status IN (
            'GENERATED',
            'OFFERED',
            'ANSWERING',
            'COMPLETED',
            'REJECTED',
            'EXPIRED'
        )
    )
);

CREATE INDEX idx_user_missions_user_date_status
    ON user_missions(user_id, mission_date, status);

CREATE TABLE mission_completion_answers (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    answer_text TEXT,
    answered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mission_completion_answers_mission
        FOREIGN KEY (mission_id)
        REFERENCES user_missions(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_mission_completion_answers_mission
        UNIQUE (mission_id)
);

INSERT INTO mission_templates (
    base_title,
    base_description,
    category,
    difficulty,
    reward_star_piece,
    fallback_character_message,
    fallback_question,
    fallback_completion_response
) VALUES
(
    '물 한 컵 마시기',
    '지금 자리에서 물 한 컵을 천천히 마셔보세요.',
    'BASIC_ROUTINE',
    'EASY',
    10,
    '물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.',
    '물 마시고 나서 기분이 조금 달라졌어?',
    '잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.'
),
(
    '양치하기',
    '잠깐 시간을 내서 이를 깨끗하게 닦아보세요.',
    'BASIC_ROUTINE',
    'EASY',
    10,
    '양치 한 번이면 오늘이 조금 더 산뜻해질 거야.',
    '양치하고 나서 입안이 조금 개운해졌어?',
    '좋아. 산뜻한 루틴 하나를 별조각으로 저장했어.'
),
(
    '창문 3분 열기',
    '창문을 열고 3분 동안 공기를 바꿔보세요.',
    'SPACE_RESET',
    'EASY',
    10,
    '공기만 바뀌어도 공간이 조금 가벼워질 수 있어.',
    '창문을 열었을 때 바깥 공기는 어땠어?',
    '공기를 바꾼 일도 충분히 작은 회복이야.'
),
(
    '책상 위 물건 하나 치우기',
    '책상 위에서 물건 하나만 제자리로 옮겨보세요.',
    'SPACE_RESET',
    'EASY',
    10,
    '물건 하나만 치워도 공간이 조금 숨을 쉴 수 있어.',
    '어떤 물건을 치웠어?',
    '작은 정리도 오늘의 별조각으로 남겨둘게.'
),
(
    '목/어깨 10초 돌리기',
    '목과 어깨를 천천히 10초 동안 돌려보세요.',
    'BODY_CARE',
    'EASY',
    10,
    '몸한테 10초만 안부를 물어보자.',
    '목이나 어깨가 조금 풀린 느낌이 있었어?',
    '몸을 챙긴 10초도 충분히 의미 있었어.'
),
(
    '현관까지 다녀오기',
    '부담 없이 현관까지 다녀오며 몸을 한 번 움직여보세요.',
    'OUTDOOR_LIGHT',
    'EASY',
    10,
    '현관까지도 오늘의 작은 이동이 될 수 있어.',
    '현관까지 다녀오면서 제일 먼저 눈에 들어온 건 뭐였어?',
    '움직인 만큼 오늘이 아주 조금 넓어졌어.'
),
(
    '하늘 한 번 보고 오기',
    '잠깐 고개를 들어 오늘 하늘을 한 번 바라보세요.',
    'OUTDOOR_LIGHT',
    'EASY',
    10,
    '하늘 한 번 보는 것도 오늘을 환기하는 방법이야.',
    '오늘 하늘 색깔은 어땠어?',
    '오늘 하늘을 본 일을 별조각으로 남겨둘게.'
),
(
    '오늘 기분 한 단어 적기',
    '지금 기분을 한 단어로 짧게 적어보세요.',
    'MIND_RECORD',
    'EASY',
    10,
    '기분을 한 단어로 붙잡아두면 오늘이 조금 선명해져.',
    '오늘 기분을 어떤 단어로 적었어?',
    '그 단어 하나도 오늘을 기억하는 좋은 기록이야.'
),
(
    '눈 감고 1분 쉬기',
    '눈을 감고 1분 동안 아무것도 하지 않고 쉬어보세요.',
    'REST_RECOVERY',
    'EASY',
    10,
    '1분 쉬는 것도 오늘을 버티는 힘이 될 수 있어.',
    '1분 쉬고 나서 몸이나 마음이 조금 달라졌어?',
    '멈춘 1분도 충분히 잘한 일이야.'
),
(
    '고마운 사람 떠올리기',
    '오늘 고마웠던 사람 한 명을 조용히 떠올려보세요.',
    'SOCIAL_LIGHT',
    'EASY',
    10,
    '고마운 사람을 떠올리는 것만으로도 마음이 조금 따뜻해질 수 있어.',
    '누가 떠올랐어? 이름 대신 느낌만 적어도 좋아.',
    '그 마음을 오늘의 작은 별조각으로 남겨둘게.'
),
(
    '세수하기',
    '잠깐 세수를 하며 얼굴을 가볍게 깨워보세요.',
    'BASIC_ROUTINE',
    'EASY',
    10,
    '세수 한 번이면 오늘이 조금 다시 시작될 수 있어.',
    '세수하고 나서 얼굴이나 기분이 조금 달라졌어?',
    '작은 리셋 하나를 오늘의 별조각으로 남겨둘게.'
),
(
    '물병 채우기',
    '비어 있는 컵이나 물병에 물을 채워두세요.',
    'BASIC_ROUTINE',
    'EASY',
    10,
    '미리 물을 채워두면 다음의 내가 조금 편해져.',
    '어디에 물을 채워뒀어?',
    '다음의 너를 챙긴 일도 충분히 좋은 루틴이야.'
),
(
    '손 씻기',
    '잠깐 손을 씻고 개운하게 정리해보세요.',
    'BASIC_ROUTINE',
    'EASY',
    10,
    '손을 씻는 짧은 시간도 하루를 정리하는 신호가 될 수 있어.',
    '손 씻고 나서 조금 개운해졌어?',
    '깨끗한 시작 하나를 별조각으로 저장했어.'
),
(
    '쓰레기 하나 버리기',
    '주변에 보이는 작은 쓰레기 하나를 버려보세요.',
    'SPACE_RESET',
    'EASY',
    10,
    '쓰레기 하나만 사라져도 공간이 조금 가벼워져.',
    '어떤 걸 버렸어?',
    '작은 정리 하나가 공간을 조금 바꿨어.'
),
(
    '이불 한 번 정리하기',
    '이불이나 담요를 한 번만 가볍게 정리해보세요.',
    'SPACE_RESET',
    'EASY',
    10,
    '이불을 한 번 정리하면 방이 살짝 덜 무거워질 수 있어.',
    '정리하고 나서 방이 조금 달라 보였어?',
    '공간을 가볍게 만든 일을 별조각으로 남겨둘게.'
),
(
    '바닥 물건 하나 줍기',
    '바닥에 있는 물건 하나만 제자리 근처로 옮겨보세요.',
    'SPACE_RESET',
    'EASY',
    10,
    '바닥 물건 하나만 옮겨도 움직일 자리가 생겨.',
    '어떤 물건을 옮겼어?',
    '딱 하나를 치운 것도 오늘의 충분한 변화야.'
),
(
    '손목 10초 돌리기',
    '손목을 천천히 10초 동안 돌려보세요.',
    'BODY_CARE',
    'EASY',
    10,
    '손목한테도 잠깐 안부를 물어보자.',
    '손목이 조금 편해진 느낌이 있었어?',
    '몸을 챙긴 짧은 시간이 오늘을 조금 도왔어.'
),
(
    '등 한 번 펴기',
    '앉은 자리에서 등을 곧게 펴고 숨을 한 번 쉬어보세요.',
    'BODY_CARE',
    'EASY',
    10,
    '등을 펴는 것만으로도 몸이 조금 깨어날 수 있어.',
    '등을 펴고 나서 숨 쉬기가 조금 달라졌어?',
    '자세를 바로잡은 작은 순간을 기억해둘게.'
),
(
    '발목 10초 돌리기',
    '발목을 천천히 10초 동안 돌려보세요.',
    'BODY_CARE',
    'EASY',
    10,
    '발목도 오늘 너를 버티느라 꽤 일했을 거야.',
    '발목을 돌리고 나서 느낌이 어땠어?',
    '몸 아래쪽까지 챙긴 일을 별조각으로 남겨둘게.'
),
(
    '창밖 풍경 10초 보기',
    '창밖이나 멀리 보이는 풍경을 10초만 바라보세요.',
    'OUTDOOR_LIGHT',
    'EASY',
    10,
    '멀리 보는 10초가 생각을 조금 느슨하게 해줄 수 있어.',
    '10초 동안 뭐가 제일 먼저 보였어?',
    '시선을 멀리 보낸 일도 작은 환기였어.'
),
(
    '문 앞에서 숨 한 번 쉬기',
    '문 앞까지 가서 숨을 한 번 천천히 쉬어보세요.',
    'OUTDOOR_LIGHT',
    'EASY',
    10,
    '문 앞까지 가는 것도 오늘의 작은 외출 준비야.',
    '문 앞에서 숨 쉬었을 때 느낌이 어땠어?',
    '작게 움직인 만큼 오늘이 조금 열렸어.'
),
(
    '햇빛 있는 곳 보기',
    '햇빛이나 밝은 곳을 잠깐 바라보세요.',
    'OUTDOOR_LIGHT',
    'EASY',
    10,
    '밝은 곳을 보는 것만으로도 하루가 조금 덜 흐려질 수 있어.',
    '밝은 곳을 봤을 때 눈에 들어온 색은 뭐였어?',
    '빛을 확인한 일을 오늘의 별조각으로 저장했어.'
),
(
    '오늘 할 일 하나 적기',
    '오늘 할 일 중 가장 작은 것 하나만 적어보세요.',
    'MIND_RECORD',
    'EASY',
    10,
    '할 일 하나를 적으면 머릿속이 조금 자리를 찾을 수 있어.',
    '어떤 할 일을 적었어?',
    '하나만 적어도 오늘을 붙잡는 데 충분했어.'
),
(
    '좋았던 순간 하나 떠올리기',
    '오늘 괜찮았던 순간 하나를 짧게 떠올려보세요.',
    'MIND_RECORD',
    'EASY',
    10,
    '좋았던 순간 하나는 생각보다 오래 남을 수 있어.',
    '어떤 순간이 떠올랐어?',
    '그 장면 하나를 오늘의 작은 별로 남겨둘게.'
),
(
    '지금 필요한 것 한 단어 적기',
    '지금 나에게 필요한 것을 한 단어로 적어보세요.',
    'MIND_RECORD',
    'EASY',
    10,
    '필요한 걸 한 단어로 적으면 마음이 조금 또렷해져.',
    '어떤 단어를 적었어?',
    '네가 필요한 걸 알아차린 것도 좋은 루틴이야.'
),
(
    '화면에서 눈 20초 떼기',
    '화면에서 눈을 떼고 20초 동안 먼 곳을 바라보세요.',
    'REST_RECOVERY',
    'EASY',
    10,
    '눈도 잠깐 쉬면 오늘을 조금 더 버틸 수 있어.',
    '20초 동안 어디를 바라봤어?',
    '눈에게 준 짧은 휴식을 별조각으로 남겨둘게.'
),
(
    '어깨 힘 빼고 숨 세 번 쉬기',
    '어깨에 들어간 힘을 빼고 숨을 세 번 천천히 쉬어보세요.',
    'REST_RECOVERY',
    'EASY',
    10,
    '힘을 빼는 것도 꽤 중요한 일일 때가 있어.',
    '숨을 세 번 쉬고 나서 어깨가 조금 내려갔어?',
    '긴장을 조금 내려놓은 일을 기억해둘게.'
),
(
    '자리에서 30초 쉬기',
    '하던 일을 잠깐 멈추고 30초만 그대로 쉬어보세요.',
    'REST_RECOVERY',
    'EASY',
    10,
    '30초 멈추는 것도 오늘의 속도를 조절하는 방법이야.',
    '30초 쉬는 동안 제일 먼저 든 생각은 뭐였어?',
    '잠깐 멈춘 것도 충분히 좋은 선택이었어.'
),
(
    '나에게 짧게 칭찬하기',
    '오늘의 나에게 짧은 칭찬 한마디를 떠올려보세요.',
    'SOCIAL_LIGHT',
    'EASY',
    10,
    '가끔은 나한테도 좋은 말을 해줘야 해.',
    '나에게 어떤 칭찬을 해줬어?',
    '그 말은 오늘 너에게 꽤 필요한 별조각이었을 거야.'
),
(
    '안부 보낼 사람 떠올리기',
    '안부를 보내고 싶은 사람 한 명을 떠올려보세요.',
    'SOCIAL_LIGHT',
    'EASY',
    10,
    '연락하지 않아도 떠올리는 것부터 작은 연결이야.',
    '누가 떠올랐어? 이름 대신 관계만 적어도 좋아.',
    '그 사람을 떠올린 마음을 조용히 저장해둘게.'
),
(
    '컵 하나 씻기',
    '싱크대나 책상 위의 컵 하나를 씻어보세요.',
    'BASIC_ROUTINE',
    'EASY',
    10,
    '컵 하나를 씻으면 다음 한 모금이 조금 편해져.',
    '어떤 컵을 씻었어?',
    '작은 생활 정리 하나를 별조각으로 남겨둘게.'
),
(
    '약이나 영양제 확인하기',
    '오늘 먹어야 할 약이나 영양제가 있는지 한 번 확인해보세요.',
    'BASIC_ROUTINE',
    'EASY',
    10,
    '챙길 게 있는지 확인하는 것만으로도 나를 돌보는 일이야.',
    '확인해보니 챙길 게 있었어?',
    '내 몸을 확인한 일을 오늘의 작은 별로 저장했어.'
),
(
    '휴지나 물티슈 채워두기',
    '자주 쓰는 휴지나 물티슈가 부족하지 않은지 확인하고 채워두세요.',
    'BASIC_ROUTINE',
    'EASY',
    10,
    '미리 채워두는 일은 다음의 나를 덜 귀찮게 해줘.',
    '무엇을 채워뒀어?',
    '작은 준비 하나가 다음 시간을 편하게 만들 거야.'
),
(
    '의자 주변 정리하기',
    '의자 주변에 놓인 물건 하나를 정리해보세요.',
    'SPACE_RESET',
    'EASY',
    10,
    '의자 주변이 조금 비면 몸도 마음도 덜 걸릴 수 있어.',
    '의자 주변에서 뭘 정리했어?',
    '앉는 자리를 가볍게 만든 일을 기억해둘게.'
),
(
    '문고리나 손잡이 닦기',
    '자주 만지는 문고리나 손잡이 하나를 가볍게 닦아보세요.',
    'SPACE_RESET',
    'EASY',
    10,
    '자주 닿는 곳 하나를 닦으면 공간이 조금 산뜻해져.',
    '어디를 닦았어?',
    '눈에 잘 안 띄는 정리도 충분히 좋은 일이야.'
),
(
    '충전기 선 정리하기',
    '엉켜 있는 충전기나 선 하나를 정리해보세요.',
    'SPACE_RESET',
    'EASY',
    10,
    '선 하나만 풀어도 공간의 복잡함이 조금 줄어들어.',
    '어떤 선을 정리했어?',
    '엉킴 하나를 풀어낸 일을 별조각으로 남겨둘게.'
),
(
    '읽지 않는 알림 하나 지우기',
    '휴대폰이나 컴퓨터의 오래된 알림 하나를 지워보세요.',
    'SPACE_RESET',
    'EASY',
    10,
    '알림 하나만 지워도 머릿속이 조금 조용해질 수 있어.',
    '어떤 알림을 지웠어?',
    '작은 디지털 정리도 오늘의 정리였어.'
),
(
    '허리 좌우로 10초 움직이기',
    '앉은 자리에서 허리를 좌우로 천천히 10초 움직여보세요.',
    'BODY_CARE',
    'EASY',
    10,
    '허리도 오늘 너를 버티느라 꽤 오래 일했을 거야.',
    '움직이고 나서 허리가 조금 달라졌어?',
    '몸을 살짝 풀어준 일을 별조각으로 남겨둘게.'
),
(
    '손가락 쭉 펴기',
    '손가락을 쭉 펴고 천천히 접었다 펴보세요.',
    'BODY_CARE',
    'EASY',
    10,
    '손가락도 잠깐 쉬게 해주면 좋아.',
    '손가락을 펴고 나서 느낌이 어땠어?',
    '작은 움직임으로 몸에게 안부를 전했어.'
),
(
    '종아리 10초 늘리기',
    '서 있거나 앉은 자리에서 종아리를 10초 정도 늘려보세요.',
    'BODY_CARE',
    'EASY',
    10,
    '다리에게도 잠깐 숨 돌릴 시간을 주자.',
    '종아리를 늘렸을 때 시원한 느낌이 있었어?',
    '다리를 챙긴 짧은 시간을 잘 남겨둘게.'
),
(
    '턱 힘 빼기',
    '이를 꽉 물고 있지 않은지 확인하고 턱에 힘을 빼보세요.',
    'BODY_CARE',
    'EASY',
    10,
    '나도 모르게 힘이 들어간 곳을 알아차리는 건 꽤 중요해.',
    '턱에 힘이 들어가 있었어?',
    '긴장 하나를 알아차린 것도 좋은 회복이야.'
),
(
    '천천히 물 한 모금 마시기',
    '물을 한 모금만 천천히 마셔보세요.',
    'BODY_CARE',
    'EASY',
    10,
    '한 모금만 천천히 마셔도 몸이 신호를 받을 수 있어.',
    '천천히 마셔보니 어땠어?',
    '몸에게 작은 신호를 보낸 일을 기억할게.'
),
(
    '현관문 밖 소리 듣기',
    '현관이나 창문 근처에서 바깥 소리를 잠깐 들어보세요.',
    'OUTDOOR_LIGHT',
    'EASY',
    10,
    '밖의 소리를 듣는 것만으로도 하루가 조금 넓어질 수 있어.',
    '가장 먼저 들린 소리는 뭐였어?',
    '오늘 바깥을 잠깐 만난 일을 별조각으로 남겨둘게.'
),
(
    '가까운 초록색 찾기',
    '주변이나 창밖에서 초록색을 하나 찾아보세요.',
    'OUTDOOR_LIGHT',
    'EASY',
    10,
    '초록색 하나를 찾는 것도 눈을 쉬게 하는 작은 방법이야.',
    '어떤 초록색을 찾았어?',
    '눈이 쉬어갈 작은 색을 찾아낸 일이야.'
),
(
    '문 열고 공기 느끼기',
    '문이나 창문을 잠깐 열고 공기를 한 번 느껴보세요.',
    'OUTDOOR_LIGHT',
    'EASY',
    10,
    '공기를 느끼는 짧은 순간이 오늘을 살짝 바꿀 수 있어.',
    '공기가 차가웠어, 따뜻했어?',
    '바깥 공기를 확인한 일을 오늘의 별로 남겨둘게.'
),
(
    '복도나 마당 한 번 보기',
    '복도, 마당, 베란다처럼 방 밖 공간을 잠깐 바라보세요.',
    'OUTDOOR_LIGHT',
    'EASY',
    10,
    '방 밖을 보는 것만으로도 시야가 조금 바뀔 수 있어.',
    '방 밖에서 뭐가 보였어?',
    '조금 넓어진 시야를 별조각으로 저장했어.'
),
(
    '오늘 먹은 것 하나 적기',
    '오늘 먹었거나 마신 것 하나를 짧게 적어보세요.',
    'MIND_RECORD',
    'EASY',
    10,
    '먹은 것을 하나 떠올리면 오늘을 더 잘 기억할 수 있어.',
    '무엇을 적었어?',
    '오늘을 구성한 작은 조각 하나를 기록했어.'
),
(
    '지금 떠오른 걱정 한 단어 적기',
    '지금 떠오르는 걱정을 한 단어로만 적어보세요.',
    'MIND_RECORD',
    'EASY',
    10,
    '걱정도 이름을 붙이면 조금 덜 커질 때가 있어.',
    '어떤 단어를 적었어?',
    '걱정을 알아차린 일도 너를 돕는 기록이야.'
),
(
    '오늘 고생한 일 하나 인정하기',
    '오늘 조금이라도 고생한 일 하나를 떠올려보세요.',
    'MIND_RECORD',
    'EASY',
    10,
    '고생한 걸 알아주는 건 꽤 다정한 일이야.',
    '어떤 일이 떠올랐어?',
    '그 고생을 그냥 지나치지 않은 걸 기억해둘게.'
),
(
    '지금 있는 장소 한 단어로 적기',
    '지금 있는 장소의 느낌을 한 단어로 적어보세요.',
    'MIND_RECORD',
    'EASY',
    10,
    '장소의 느낌을 적으면 지금이 조금 선명해져.',
    '장소를 어떤 단어로 적었어?',
    '지금 있는 곳을 알아차린 일을 별조각으로 남길게.'
),
(
    '오늘 미룬 일 하나 작게 쪼개기',
    '미룬 일 하나를 아주 작은 다음 행동으로 쪼개 적어보세요.',
    'MIND_RECORD',
    'EASY',
    10,
    '큰 일도 아주 작은 다음 행동으로 줄이면 덜 무서울 수 있어.',
    '어떤 다음 행동으로 쪼갰어?',
    '일을 작게 만든 것도 충분히 중요한 시작이야.'
),
(
    '따뜻한 물건 만져보기',
    '컵, 담요, 옷처럼 따뜻한 물건을 잠깐 만져보세요.',
    'REST_RECOVERY',
    'EASY',
    10,
    '따뜻함을 느끼는 짧은 순간이 몸을 조금 안심시킬 수 있어.',
    '어떤 따뜻한 물건을 만졌어?',
    '몸이 알아차린 작은 따뜻함을 저장해둘게.'
),
(
    '눈 주변 힘 빼기',
    '눈을 감거나 멀리 보면서 눈 주변 힘을 잠깐 빼보세요.',
    'REST_RECOVERY',
    'EASY',
    10,
    '눈 주변 힘을 빼는 것만으로도 머리가 조금 조용해질 수 있어.',
    '눈 주변이 조금 편해졌어?',
    '눈에게 준 작은 휴식을 별조각으로 남겨둘게.'
),
(
    '소리 줄이고 30초 있기',
    '주변 소리나 기기 볼륨을 줄이고 30초만 조용히 있어보세요.',
    'REST_RECOVERY',
    'EASY',
    10,
    '조용한 30초는 생각보다 큰 휴식일 수 있어.',
    '30초 동안 어떤 소리가 남아 있었어?',
    '조용함을 만든 일을 오늘의 작은 회복으로 저장했어.'
),
(
    '앉은 자세 바꾸기',
    '지금 앉은 자세를 조금 더 편한 자세로 바꿔보세요.',
    'REST_RECOVERY',
    'EASY',
    10,
    '자세를 바꾸는 작은 선택도 몸을 덜 지치게 해.',
    '어떻게 자세를 바꿨어?',
    '몸이 조금 더 편해질 자리를 찾아준 일이야.'
),
(
    '고마운 물건 하나 떠올리기',
    '오늘 나를 도와준 물건 하나를 떠올려보세요.',
    'SOCIAL_LIGHT',
    'EASY',
    10,
    '사람이 아니어도 고마운 건 꽤 많을 때가 있어.',
    '어떤 물건이 떠올랐어?',
    '너를 도운 작은 존재를 알아차린 일이야.'
),
(
    '짧은 응원 문장 떠올리기',
    '지금의 나에게 해주고 싶은 짧은 응원 문장을 떠올려보세요.',
    'SOCIAL_LIGHT',
    'EASY',
    10,
    '응원은 길지 않아도 힘이 될 수 있어.',
    '어떤 문장을 떠올렸어?',
    '그 문장은 오늘 너에게 남겨둘 만한 별조각이야.'
),
(
    '좋아하는 사람의 좋은 점 하나 떠올리기',
    '좋아하거나 편한 사람의 좋은 점 하나를 떠올려보세요.',
    'SOCIAL_LIGHT',
    'EASY',
    10,
    '좋은 점을 떠올리는 건 마음의 방향을 살짝 바꾸는 일이야.',
    '어떤 좋은 점이 떠올랐어?',
    '좋은 마음 하나를 오늘의 별로 저장해둘게.'
),
(
    '미래의 나에게 한마디 남기기',
    '조금 뒤의 나에게 해주고 싶은 말을 한 문장 떠올려보세요.',
    'SOCIAL_LIGHT',
    'EASY',
    10,
    '미래의 나에게 말을 남기는 건 작지만 든든한 연결이야.',
    '미래의 나에게 뭐라고 말해줬어?',
    '그 한마디가 다음의 너에게 닿을 수 있게 남겨둘게.'
),
(
    '누군가의 수고 떠올리기',
    '오늘 보았거나 떠오른 누군가의 수고를 하나 생각해보세요.',
    'SOCIAL_LIGHT',
    'EASY',
    10,
    '다른 사람의 수고를 알아차리는 것도 따뜻한 루틴이야.',
    '어떤 수고가 떠올랐어?',
    '그 마음을 조용한 별조각으로 남겨둘게.'
);
