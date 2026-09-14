package com.tradeoperationsplatform.apiserver.domain.identity.service;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.*;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {
    @Mock OrganizationRepository organizationRepository;
    @Mock AppUserRepository appUserRepository;
    @Mock OrganizationMemberRepository memberRepository;
    @InjectMocks IdentityService service;
    @Test void unknownOrganizationIsNotAccessible() {
        assertThatThrownBy(() -> service.requireOrganization(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void inactiveOrMissingMembershipIsNotAccepted() {
        var user = new AppUser("test@example.com", "담당자", null);
        org.mockito.Mockito.when(appUserRepository.findByPublicId(user.getPublicId())).thenReturn(java.util.Optional.of(user));
        var organization = new Organization("화주", Organization.Type.SHIPPER, null, null, null);
        assertThatThrownBy(() -> service.requireMember(user.getPublicId(), organization)).isInstanceOf(BusinessException.class);
    }
}
