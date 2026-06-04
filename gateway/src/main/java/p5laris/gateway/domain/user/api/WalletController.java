package p5laris.gateway.domain.user.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.user.api.dto.WalletDto;
import p5laris.gateway.domain.user.infrastructure.grpc.WalletGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletGatewayService walletGatewayService;

    /**
     * 별조각 잔액 조회
     * @param userId
     * @return
     */
    @GetMapping("/v1/wallets/me")
    public ApiResponse<WalletDto.Response> getMyWallet(@LoginUserId Long userId) {
        return ApiResponse.success(walletGatewayService.getMyWallet(userId));
    }

    /**
     * 별조각 거래내역 조회
     * @param userId
     * @param cursor
     * @param size
     * @return
     */
    @GetMapping("/v1/wallets/me/transactions")
    public ApiResponse<WalletDto.TransactionListResponse> getTransactions(
            @LoginUserId Long userId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String cursor,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(walletGatewayService.getTransactions(userId, cursor, size));
    }
}
