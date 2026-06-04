package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.GetMyWalletRequest;
import com.p5laris.proto.user.v1.WalletResponse;
import com.p5laris.proto.user.v1.WalletServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.user.api.dto.WalletDto;

@Service
public class WalletGatewayService {

    @GrpcClient("user")
    private WalletServiceGrpc.WalletServiceBlockingStub walletServiceStub;

    public WalletDto.Response getMyWallet(Long userId) {
        WalletResponse response = walletServiceStub.getMyWallet(
                GetMyWalletRequest.newBuilder().setUserId(userId).build()
        );

        return WalletDto.Response.builder()
                .starPiece(response.getStarPiece())
                .build();
    }

    public WalletDto.TransactionListResponse getTransactions(Long userId, String cursor, int size) {
        com.p5laris.proto.user.v1.GetTransactionsResponse response = walletServiceStub.getTransactions(
                com.p5laris.proto.user.v1.GetTransactionsRequest.newBuilder()
                        .setUserId(userId)
                        .setCursor(cursor != null ? cursor : "")
                        .setSize(size)
                        .build()
        );

        java.util.List<WalletDto.TransactionItem> items = response.getItemsList().stream()
                .map(tx -> WalletDto.TransactionItem.builder()
                        .id(tx.getId())
                        .amount(tx.getAmount())
                        .balanceAfter(tx.getBalanceAfter())
                        .reason(tx.getReason())
                        .description(tx.getDescription())
                        .sourceType(tx.getSourceType())
                        .sourceId(tx.getSourceId())
                        .occurredAt(tx.getOccurredAt())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        WalletDto.PageInfo pageInfo = WalletDto.PageInfo.builder()
                .nextCursor(response.getPageInfo().getNextCursor())
                .hasNext(response.getPageInfo().getHasNext())
                .size(response.getPageInfo().getSize())
                .build();

        return WalletDto.TransactionListResponse.builder()
                .items(items)
                .pageInfo(pageInfo)
                .build();
    }
}
