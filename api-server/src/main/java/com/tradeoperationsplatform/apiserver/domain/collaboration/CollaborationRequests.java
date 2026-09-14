package com.tradeoperationsplatform.apiserver.domain.collaboration;

import jakarta.validation.constraints.*;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import java.util.UUID;

public final class CollaborationRequests {
    public enum Level { VIEWER, CONTRIBUTOR }
    public record Attach(@NotNull UUID businessPartnerId, @NotNull BusinessPartner.Role role) {}
    public record Invite(@NotBlank @Size(max=200) String name, @NotBlank @Email @Size(max=255) String email,
                         @NotNull Level accessLevel) {}
    public record Revoke(@NotBlank @Size(max=500) String reason) {}
    public record RequestLink(@NotNull UUID invitationId, @NotBlank @Email @Size(max=255) String email) {}
    public record Confirm(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}
}
