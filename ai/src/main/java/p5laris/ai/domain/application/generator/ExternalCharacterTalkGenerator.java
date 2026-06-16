package p5laris.ai.domain.application.generator;

import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.domain.enums.AiProviderType;

import java.util.function.Consumer;

public interface ExternalCharacterTalkGenerator {

    AiProviderType providerType();

    AiTokenUsage stream(CharacterTalkGenerationCommand command, Consumer<String> chunkConsumer);
}
