package com.tradeoperationsplatform.apiserver.domain.shipment.entity;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ShipmentCaseTest {
    private ShipmentCase shipment() {
        Organization organization = new Organization("화주 A", Organization.Type.SHIPPER, null, null, null);
        AppUser user = new AppUser("owner@example.com", "담당자", null);
        return new ShipmentCase(organization, user, "CASE-001", ShipmentCase.Direction.IMPORT,
                ShipmentCase.TransportMode.SEA, null, null);
    }

    @Test
    void appliesMvpDefaultsAndUpdatesOperationalState() {
        ShipmentCase shipment = shipment();
        assertThat(shipment.getCurrentStage()).isEqualTo(ShipmentCase.Stage.PREPARATION);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentCase.Status.OPEN);
        assertThat(shipment.getPriority()).isEqualTo(ShipmentCase.Priority.NORMAL);

        shipment.update(ShipmentCase.Stage.ARRIVED, ShipmentCase.Status.ON_HOLD, ShipmentCase.Priority.URGENT,
                null, null, null, null, null, null, null, null, "검사 대기");

        assertThat(shipment.getCurrentStage()).isEqualTo(ShipmentCase.Stage.ARRIVED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentCase.Status.ON_HOLD);
        assertThat(shipment.getPriority()).isEqualTo(ShipmentCase.Priority.URGENT);
    }

    @Test
    void archivesWithoutPhysicalDeletion() {
        ShipmentCase shipment = shipment();
        shipment.archive();

        assertThat(shipment.getStatus()).isEqualTo(ShipmentCase.Status.ARCHIVED);
        assertThat(shipment.getArchivedAt()).isNotNull();
    }

    @Test
    void archiveStatusCannotBeSetThroughGeneralUpdate() {
        ShipmentCase shipment = shipment();

        assertThatThrownBy(() -> shipment.update(null, ShipmentCase.Status.ARCHIVED, null,
                null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
