package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.OnboardingService;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class OnboardingGrpcController extends OnboardingServiceGrpc.OnboardingServiceImplBase {

    private static final String INTERNAL_ERROR_DESCRIPTION = "온보딩 서비스 처리 중 오류가 발생했습니다.";

    private final OnboardingService onboardingService;

    @Override
    public void getQuestions(GetQuestionsRequest request, StreamObserver<GetQuestionsResponse> responseObserver) {
        try {
            responseObserver.onNext(onboardingService.getQuestions());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("온보딩 질문 조회", e));
        }
    }

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<GetProfileResponse> responseObserver) {
        try {
            responseObserver.onNext(onboardingService.getProfile(request.getUserId()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("온보딩 프로필 조회", e));
        }
    }

    @Override
    public void saveProfile(SaveProfileRequest request, StreamObserver<SaveProfileResponse> responseObserver) {
        try {
            responseObserver.onNext(onboardingService.saveProfile(request.getUserId(), request.getProfile()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("온보딩 프로필 저장", e));
        }
    }

    private RuntimeException internalError(String operation, Exception e) {
        log.error("온보딩 gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", operation, e);
        return io.grpc.Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException();
    }
}
