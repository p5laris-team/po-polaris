package p5laris.gateway.domain.user.api.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

public class WalletDto {

    @Getter
    @Builder
    public static class Response {
        private int starPiece;
    }

    @Getter
    @Builder
    public static class TransactionListResponse {
        private List<TransactionItem> items;
        private PageInfo pageInfo;
    }

    @Getter
    @Builder
    public static class TransactionItem {
        private Long id;
        private int amount;
        private int balanceAfter;
        private String reason;
        private String description;
        private String sourceType;
        private Long sourceId;
        private String occurredAt;
    }

    @Getter
    @Builder
    public static class PageInfo {
        private String nextCursor;
        private boolean hasNext;
        private int size;
    }
}
