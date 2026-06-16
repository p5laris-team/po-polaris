package p5laris.ai.domain.application.generator;

import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;

import java.util.function.Consumer;

public interface CharacterTalkGenerator {

    AiTokenUsage stream(CharacterTalkGenerationCommand command, Consumer<String> chunkConsumer);
}
