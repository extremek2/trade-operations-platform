package com.tradeoperationsplatform.apiserver.domain.auth.dto;
import com.tradeoperationsplatform.apiserver.domain.auth.entity.RefreshSession;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record ContextRequest(@NotNull RefreshSession.Kind kind, UUID organizationId) {}
