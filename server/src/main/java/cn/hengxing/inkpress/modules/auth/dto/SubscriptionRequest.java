package cn.hengxing.inkpress.modules.auth.dto;

import jakarta.validation.constraints.NotNull;

public record SubscriptionRequest(@NotNull Boolean digestSubscribed) {
}
