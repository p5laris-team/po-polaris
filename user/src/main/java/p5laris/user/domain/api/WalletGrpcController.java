package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.GetMyWalletRequest;
import com.p5laris.proto.user.v1.WalletResponse;
import com.p5laris.proto.user.v1.WalletServiceGrpc;
import com.p5laris.proto.user.v1.SpendStarPieceRequest;
import com.p5laris.proto.user.v1.SpendStarPieceResponse;
import com.p5laris.proto.user.v1.EarnStarPieceRequest;
import com.p5laris.proto.user.v1.EarnStarPieceResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.WalletService;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.exception.UserException;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class WalletGrpcController extends WalletServiceGrpc.WalletServiceImplBase {

    private static final String INTERNAL_ERROR_DESCRIPTION = "지갑 서비스 처리 중 오류가 발생했습니다.";

    private final WalletService walletService;

    @Override
    public void getMyWallet(GetMyWalletRequest request, StreamObserver<WalletResponse> responseObserver) {
        try {
            Wallet wallet = walletService.getMyWallet(request.getUserId());
            
            WalletResponse response = WalletResponse.newBuilder()
                    .setStarPiece(wallet.getStarPiece())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("내 지갑 조회", e));
        }
    }

    @Override
    public void spendStarPiece(SpendStarPieceRequest request, StreamObserver<SpendStarPieceResponse> responseObserver) {
        try {
            StarPieceTransaction tx = walletService.spendStarPiece(
                    request.getUserId(),
                    request.getAmount(),
                    request.getReason(),
                    request.getRefType().isEmpty() ? null : request.getRefType(),
                    request.getRefId() == 0 ? null : request.getRefId(),
                    request.getIdempotencyKey().isEmpty() ? null : request.getIdempotencyKey()
            );
            
            Wallet wallet = walletService.getMyWallet(request.getUserId());
            
            SpendStarPieceResponse response = SpendStarPieceResponse.newBuilder()
                    .setStarPiece(wallet.getStarPiece())
                    .setTransactionId(tx.getId())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("별조각 사용", e));
        }
    }

    @Override
    public void earnStarPiece(EarnStarPieceRequest request, StreamObserver<EarnStarPieceResponse> responseObserver) {
        try {
            StarPieceTransaction tx = walletService.earnStarPiece(
                    request.getUserId(),
                    request.getAmount(),
                    request.getReason(),
                    request.getRefType().isEmpty() ? null : request.getRefType(),
                    request.getRefId() == 0 ? null : request.getRefId(),
                    request.getIdempotencyKey().isEmpty() ? null : request.getIdempotencyKey()
            );
            
            Wallet wallet = walletService.getMyWallet(request.getUserId());
            
            EarnStarPieceResponse response = EarnStarPieceResponse.newBuilder()
                    .setStarPiece(wallet.getStarPiece())
                    .setTransactionId(tx.getId())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("별조각 적립", e));
        }
    }

    @Override
    public void getTransactions(com.p5laris.proto.user.v1.GetTransactionsRequest request, io.grpc.stub.StreamObserver<com.p5laris.proto.user.v1.GetTransactionsResponse> responseObserver) {
        try {
            com.p5laris.proto.user.v1.GetTransactionsResponse response = walletService.getTransactions(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("별조각 거래 내역 조회", e));
        }
    }

    @Override
    public void checkTransaction(com.p5laris.proto.user.v1.CheckTransactionRequest request, io.grpc.stub.StreamObserver<com.p5laris.proto.user.v1.CheckTransactionResponse> responseObserver) {
        try {
            java.util.Optional<StarPieceTransaction> txOpt = walletService.findTransactionByIdempotencyKey(request.getIdempotencyKey());
            
            com.p5laris.proto.user.v1.CheckTransactionResponse.Builder builder = com.p5laris.proto.user.v1.CheckTransactionResponse.newBuilder();
            if (txOpt.isPresent()) {
                builder.setExists(true)
                       .setAmount(txOpt.get().getAmount())
                       .setTransactionId(txOpt.get().getId());
            } else {
                builder.setExists(false);
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("별조각 거래 멱등키 확인", e));
        }
    }

    private RuntimeException internalError(String operation, Exception e) {
        log.error("지갑 gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", operation, e);
        return io.grpc.Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException();
    }
}
