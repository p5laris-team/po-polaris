package p5laris.user.domain.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.repository.StarPieceTransactionRepository;
import p5laris.user.domain.domain.repository.WalletRepository;
import p5laris.user.domain.exception.UserException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private StarPieceTransactionRepository transactionRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    @DisplayName("earnStarPiece - 정상적인 지급 케이스")
    void earnStarPiece_success() {
        // given
        Long userId = 1L;
        int amount = 100;
        Wallet wallet = Wallet.builder().id(10L).userId(userId).starPiece(500).build();
        
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(StarPieceTransaction.class))).thenAnswer(invocation -> {
            StarPieceTransaction tx = invocation.getArgument(0);
            return tx; // Mock saving by returning the object itself
        });

        // when
        StarPieceTransaction result = walletService.earnStarPiece(userId, amount, "TEST_EARN", "TEST", 100L, "key-123");

        // then
        assertThat(wallet.getStarPiece()).isEqualTo(600); // 500 + 100
        assertThat(result.getAmount()).isEqualTo(amount);
        assertThat(result.getBalanceAfter()).isEqualTo(600);
        assertThat(result.getIdempotencyKey()).isEqualTo("key-123");

        verify(walletRepository, times(1)).findByUserIdForUpdate(userId);
        verify(transactionRepository, times(1)).save(any(StarPieceTransaction.class));
    }

    @Test
    @DisplayName("earnStarPiece - 멱등키 중복 요청 시 지급 우회 및 기존 트랜잭션 반환")
    void earnStarPiece_duplicateIdempotencyKey_returnsExisting() {
        // given
        Long userId = 1L;
        String key = "dup-key";
        StarPieceTransaction existingTx = StarPieceTransaction.builder()
                .id(999L)
                .userId(userId)
                .amount(100)
                .balanceAfter(600)
                .idempotencyKey(key)
                .build();

        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existingTx));

        // when
        StarPieceTransaction result = walletService.earnStarPiece(userId, 100, "TEST_EARN", "TEST", 100L, key);

        // then
        assertThat(result.getId()).isEqualTo(999L);
        assertThat(result.getIdempotencyKey()).isEqualTo(key);

        verify(walletRepository, never()).findByUserId(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("spendStarPiece - 정상 차감 케이스")
    void spendStarPiece_success() {
        // given
        Long userId = 1L;
        int amount = 150;
        Wallet wallet = Wallet.builder().id(10L).userId(userId).starPiece(500).build();

        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(StarPieceTransaction.class))).thenAnswer(invocation -> {
            StarPieceTransaction tx = invocation.getArgument(0);
            return tx;
        });

        // when
        StarPieceTransaction result = walletService.spendStarPiece(userId, amount, "ITEM_PURCHASE", "ITEM", 200L, "spend-key");

        // then
        assertThat(wallet.getStarPiece()).isEqualTo(350); // 500 - 150
        assertThat(result.getAmount()).isEqualTo(-amount);
        assertThat(result.getBalanceAfter()).isEqualTo(350);

        verify(walletRepository, times(1)).findByUserIdForUpdate(userId);
        verify(transactionRepository, times(1)).save(any(StarPieceTransaction.class));
    }

    @Test
    @DisplayName("spendStarPiece - 잔액 부족으로 차감 실패 및 예외 발생")
    void spendStarPiece_insufficientFunds_throwsException() {
        // given
        Long userId = 1L;
        int amount = 1000;
        Wallet wallet = Wallet.builder().id(10L).userId(userId).starPiece(500).build();

        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        // when & then
        assertThatThrownBy(() -> walletService.spendStarPiece(userId, amount, "ITEM_PURCHASE", "ITEM", 200L, "spend-key"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("별조각이 부족합니다.");

        assertThat(wallet.getStarPiece()).isEqualTo(500); // Balance should remain unchanged

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("spendStarPiece - 지갑이 존재하지 않을 시 예외 발생")
    void spendStarPiece_walletNotFound_throwsException() {
        // given
        Long userId = 1L;
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> walletService.spendStarPiece(userId, 100, "ITEM_PURCHASE", "ITEM", 200L, "spend-key"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("지갑을 찾을 수 없습니다.");
    }
}
