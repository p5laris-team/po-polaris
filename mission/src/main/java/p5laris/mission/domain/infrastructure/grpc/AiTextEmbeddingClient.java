package p5laris.mission.domain.infrastructure.grpc;

import com.p5laris.proto.ai.v1.AiServiceGrpc;
import com.p5laris.proto.ai.v1.GenerateTextEmbeddingRequest;
import com.p5laris.proto.ai.v1.GenerateTextEmbeddingResponse;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * mission 모듈에서 ai 모듈의 text embedding gRPC를 호출하는 adapter다.
 *
 * RAG는 개인화 품질을 높이는 보조 기능이므로, 호출 실패는 미션 생성 실패로 전파하지 않는다.
 */
@Slf4j
@Component
public class AiTextEmbeddingClient {

    @GrpcClient("ai")
    private AiServiceGrpc.AiServiceBlockingStub aiStub;

    public Optional<AiTextEmbeddingResult> generateTextEmbedding(AiTextEmbeddingRequest request) {
        try {
            GenerateTextEmbeddingResponse response = aiStub.generateTextEmbedding(
                    GenerateTextEmbeddingRequest.newBuilder()
                            .setUserId(request.userId())
                            .setText(request.text())
                            .setModel(request.model())
                            .setDimension(request.dimension())
                            .setRequestId(request.requestId())
                            .build()
            );

            return Optional.of(new AiTextEmbeddingResult(
                    response.getModel(),
                    response.getDimension(),
                    response.getValuesList(),
                    response.getRequestId()
            ));
        } catch (StatusRuntimeException e) {
            log.warn("AI text embedding 생성 실패. requestId={}, status={}",
                    request.requestId(), e.getStatus().getCode(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AI text embedding 생성 실패. requestId={}", request.requestId(), e);
            return Optional.empty();
        }
    }
}
