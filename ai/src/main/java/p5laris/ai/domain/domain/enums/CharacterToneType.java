package p5laris.ai.domain.domain.enums;

/**
 * MVP 캐릭터 3종의 말투 규칙이다.
 *
 * 외부 AI가 붙기 전에는 이 enum이 rule-based fallback 역할을 한다.
 * 외부 AI가 붙은 뒤에도 provider 실패 시 안전하게 사용할 수 있는 기준 문구가 된다.
 */
public enum CharacterToneType {
    // 노바: 느리고 조심스럽고 다정한 별알 말투.
    NOVA {
        @Override
        public String characterMessage(String baseTitle, long variationSeed) {
            return shorten(baseTitle, 28) + "... 천천히 해보자. 작은 빛 하나면 충분해.";
        }

        @Override
        public String completionQuestion(long variationSeed) {
            return "끝나고 나서 마음이 조금 달라졌어?";
        }

        @Override
        public String completionResponse(long variationSeed) {
            return "잘했어... 오늘의 작은 빛을 별조각으로 남겨둘게.";
        }
    },
    // 무무: 실제 발화는 "무..." 중심이고, 괄호 안에 시스템 해석을 함께 제공한다.
    MUMU {
        @Override
        public String characterMessage(String baseTitle, long variationSeed) {
            return pick(variationSeed, 0, MUMU_CHARACTER_MESSAGE_PATTERNS)
                    .formatted(shorten(baseTitle, 18));
        }

        @Override
        public String completionQuestion(long variationSeed) {
            return pick(variationSeed, 1, MUMU_COMPLETION_QUESTIONS);
        }

        @Override
        public String completionResponse(long variationSeed) {
            return pick(variationSeed, 2, MUMU_COMPLETION_RESPONSES);
        }
    },
    // 쪼리: 건조하고 짧게 농담하는 말투.
    JJORY {
        @Override
        public String characterMessage(String baseTitle, long variationSeed) {
            return pick(variationSeed, 0, JJORY_CHARACTER_MESSAGE_PATTERNS)
                    .formatted(shorten(baseTitle, 24));
        }

        @Override
        public String completionQuestion(long variationSeed) {
            return pick(variationSeed, 1, JJORY_COMPLETION_QUESTIONS);
        }

        @Override
        public String completionResponse(long variationSeed) {
            return pick(variationSeed, 2, JJORY_COMPLETION_RESPONSES);
        }
    };

    private static final int VARIANT_SALT_UNIT = 1_000_003;

    private static final String[] MUMU_CHARACTER_MESSAGE_PATTERNS = {
            "무... 무무... (해석: 무무가 %s 해보자고 하는 것 같아요.)",
            "무우... 무...? (해석: 무무가 %s, 지금 살짝 해보면 어떠냐고 하네요.)",
            "무...! 무무! (해석: 무무가 %s 하면 옆에서 같이 반짝여 주겠다고 해요.)",
            "무무... 무... (해석: 무무가 %s부터 작게 시작해보자고 하는 것 같아요.)",
            "무...? 무우... (해석: 무무가 %s, 부담 없이 한 번만 해봐도 괜찮다고 하네요.)"
    };

    private static final String[] MUMU_COMPLETION_QUESTIONS = {
            "무...? 무무... (해석: 무무가 해보고 나서 어땠는지 궁금해하는 것 같아요.)",
            "무우...? (해석: 무무가 끝내고 나니 기분이 조금 달라졌냐고 묻고 있어요.)",
            "무... 무무? (해석: 무무가 해본 뒤 제일 먼저 든 생각을 듣고 싶어 하네요.)",
            "무무...? 무... (해석: 무무가 작은 변화가 있었는지 살짝 물어보는 것 같아요.)",
            "무...? (해석: 무무가 지금 상태를 한 줄로 남겨보자고 하네요.)"
    };

    private static final String[] MUMU_COMPLETION_RESPONSES = {
            "무... 무무... (해석: 무무가 조용히 좋아하고 있는 것 같아요.)",
            "무우...! 무무... (해석: 무무가 잘했다고, 오늘 일을 반짝 기억하겠다고 하네요.)",
            "무...! (해석: 무무가 작은 완료도 충분히 멋졌다고 하는 것 같아요.)",
            "무무... 무... (해석: 무무가 오늘의 한 걸음을 기억해두겠다고 해요.)",
            "무...? 무무! (해석: 무무가 방금 해낸 걸 분명히 봤다고 하네요.)"
    };

    private static final String[] JJORY_CHARACTER_MESSAGE_PATTERNS = {
            "%s. 이 정도면 시작 각임. 가보자고.",
            "%s. 귀찮아 보여도 1회차는 가능함. 인정.",
            "%s. 오늘의 작전은 이거임. 부담은 작게.",
            "%s. 크게 안 해도 됨. 선방 루트로 가자.",
            "%s. 이건 좀 가능. 나쁘지 않음."
    };

    private static final String[] JJORY_COMPLETION_QUESTIONS = {
            "해보니까 어땠음? 한 줄이면 됨.",
            "완료 소감 있음? 짧게만 남겨도 인정.",
            "이 미션, 생각보다 할 만했음?",
            "끝내고 나니 상태 어때? 솔직 후기 받음.",
            "오늘의 작전 결과 어땠음?"
    };

    private static final String[] JJORY_COMPLETION_RESPONSES = {
            "완료했네. 이 정도면 선방. 인정.",
            "작전 성공임. 방금 한 거 꽤 괜찮았음.",
            "오, 해냈음. 오늘의 나 꽤 괜찮음.",
            "이걸 해내네. 작은 승리로 기록함.",
            "나쁘지 않음. 아니, 꽤 좋음."
    };

    public abstract String characterMessage(String baseTitle, long variationSeed);

    public abstract String completionQuestion(long variationSeed);

    public abstract String completionResponse(long variationSeed);

    public String characterMessage(String baseTitle) {
        return characterMessage(baseTitle, 0L);
    }

    public String completionQuestion() {
        return completionQuestion(0L);
    }

    public String completionResponse() {
        return completionResponse(0L);
    }

    // 같은 요청에서는 같은 말투가 나오고, 미션이 바뀌면 다른 패턴도 섞이도록 seed 기반으로 고른다.
    private static String pick(long variationSeed, int salt, String[] candidates) {
        int index = Math.floorMod(Long.hashCode(variationSeed + ((long) salt * VARIANT_SALT_UNIT)), candidates.length);
        return candidates[index];
    }

    // 제목이 너무 길면 말풍선 전체가 100자를 넘을 수 있으므로 앞부분만 사용한다.
    private static String shorten(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "작은 미션";
        }

        String trimmed = text.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
