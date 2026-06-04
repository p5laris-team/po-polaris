CREATE TABLE items (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    item_type VARCHAR(50) NOT NULL,
    price INT NOT NULL,
    effect INT,
    effect_type VARCHAR(50),
    image_url VARCHAR(555),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    character_type_id BIGINT
);

CREATE TABLE user_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL REFERENCES items(id),
    quantity INT NOT NULL DEFAULT 1,
    equipped BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, item_id)
);

-- Seed Data
INSERT INTO items (name, description, item_type, price, effect, effect_type, image_url, active) VALUES
('말랑 별빛 스킨', '말랑말랑하게 빛나는 스킨입니다.', 'SKIN', 60, NULL, NULL, 'https://cdn.polaris.app/items/skin-soft-star.png', TRUE),
('은하수 오로라 스킨', '아름다운 은하수 오로라 무늬의 스킨입니다.', 'SKIN', 100, NULL, NULL, 'https://cdn.polaris.app/items/skin-milky-way.png', TRUE),
('별사탕밥', '캐릭터의 배고픔을 채워주는 달콤한 별사탕밥입니다.', 'CONSUMABLE', 10, 30, 'FOOD', 'https://cdn.polaris.app/items/candy-rice.png', TRUE),
('구름 베개', '캐릭터의 피로를 풀어주는 푹신한 구름 베개입니다.', 'CONSUMABLE', 15, 40, 'REST', 'https://cdn.polaris.app/items/cloud-pillow.png', TRUE),
('별 장난감', '캐릭터의 애정을 올려주는 귀여운 별 장난감입니다.', 'CONSUMABLE', 15, 40, 'PLAY', 'https://cdn.polaris.app/items/star-toy.png', TRUE);
