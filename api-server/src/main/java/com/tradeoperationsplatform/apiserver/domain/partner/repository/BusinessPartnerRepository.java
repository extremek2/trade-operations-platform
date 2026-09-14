package com.tradeoperationsplatform.apiserver.domain.partner.repository;

import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner, Long> {
    @Query("select distinct p from BusinessPartner p join fetch p.roles where p.ownerOrganization.id=:organizationId order by p.name")
    List<BusinessPartner> findAllOwnedWithRoles(@Param("organizationId") Long organizationId);

    @Query("select distinct p from BusinessPartner p join fetch p.roles where p.publicId=:publicId and p.ownerOrganization.id=:organizationId")
    Optional<BusinessPartner> findOwnedWithRoles(@Param("publicId") UUID publicId,
                                                  @Param("organizationId") Long organizationId);

    @Query("select (count(p)>0) from BusinessPartner p where p.ownerOrganization.id=:organizationId and lower(p.name)=lower(:name)")
    boolean existsOwnedName(@Param("organizationId") Long organizationId, @Param("name") String name);
}
