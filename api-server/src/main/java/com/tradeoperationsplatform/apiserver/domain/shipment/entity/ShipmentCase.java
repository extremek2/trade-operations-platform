package com.tradeoperationsplatform.apiserver.domain.shipment.entity;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_case")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentCase {
    public enum Direction { IMPORT, EXPORT }
    public enum TransportMode { SEA, AIR, ROAD, RAIL, MULTIMODAL }
    public enum Stage { PREPARATION, BOOKING, DEPARTED, IN_TRANSIT, ARRIVED, CUSTOMS, DELIVERY, COMPLETED }
    public enum Status { OPEN, ON_HOLD, COMPLETED, CANCELLED, ARCHIVED }
    public enum Priority { NORMAL, ATTENTION, URGENT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_organization_id") private Organization ownerOrganization;
    @Column(name = "case_number", nullable = false) private String caseNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Direction direction;
    @Enumerated(EnumType.STRING) @Column(name = "transport_mode", nullable = false) private TransportMode transportMode;
    @Enumerated(EnumType.STRING) @Column(name = "current_stage", nullable = false) private Stage currentStage;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Priority priority;
    @Column(name = "shipper_reference") private String shipperReference;
    @Column(name = "purchase_order_number") private String purchaseOrderNumber;
    @Column(name = "carrier_name") private String carrierName;
    @Column(name = "vessel_name") private String vesselName;
    @Column(name = "voyage_number") private String voyageNumber;
    @Column(name = "flight_number") private String flightNumber;
    @Column(name = "origin_location_code") private String originLocationCode;
    @Column(name = "origin_location_name") private String originLocationName;
    @Column(name = "destination_location_code") private String destinationLocationCode;
    @Column(name = "destination_location_name") private String destinationLocationName;
    private LocalDateTime etd;
    private LocalDateTime atd;
    private LocalDateTime eta;
    private LocalDateTime ata;
    @Column(name = "cargo_description") private String cargoDescription;
    @Column(name = "package_count") private Integer packageCount;
    @Column(name = "gross_weight") private BigDecimal grossWeight;
    @Column(name = "weight_unit") private String weightUnit;
    @Column(name = "container_count") private Integer containerCount;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "archived_at") private LocalDateTime archivedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Version private Long version;

    public ShipmentCase(Organization organization, AppUser createdBy, String caseNumber, Direction direction,
                        TransportMode transportMode, Stage stage, Priority priority) {
        this.publicId = UUID.randomUUID();
        this.ownerOrganization = organization;
        this.createdBy = createdBy;
        this.caseNumber = caseNumber;
        this.direction = direction;
        this.transportMode = transportMode;
        this.currentStage = stage == null ? Stage.PREPARATION : stage;
        this.status = Status.OPEN;
        this.priority = priority == null ? Priority.NORMAL : priority;
    }

    public void update(Stage stage, Status status, Priority priority, String carrierName, String vesselName,
                       String voyageNumber, String flightNumber, LocalDateTime etd, LocalDateTime atd,
                       LocalDateTime eta, LocalDateTime ata, String cargoDescription) {
        if (status == Status.ARCHIVED) throw new IllegalArgumentException("보관은 archive API를 사용해야 합니다.");
        if (stage != null) this.currentStage = stage;
        if (status != null) this.status = status;
        if (priority != null) this.priority = priority;
        if (carrierName != null) this.carrierName = carrierName;
        if (vesselName != null) this.vesselName = vesselName;
        if (voyageNumber != null) this.voyageNumber = voyageNumber;
        if (flightNumber != null) this.flightNumber = flightNumber;
        if (etd != null) this.etd = etd;
        if (atd != null) this.atd = atd;
        if (eta != null) this.eta = eta;
        if (ata != null) this.ata = ata;
        if (cargoDescription != null) this.cargoDescription = cargoDescription;
    }

    public void fillDetails(String shipperReference, String purchaseOrderNumber, String originCode, String originName,
                            String destinationCode, String destinationName, String cargoDescription,
                            Integer packageCount, BigDecimal grossWeight, String weightUnit, Integer containerCount,
                            String carrierName, String vesselName, String voyageNumber, String flightNumber,
                            LocalDateTime etd, LocalDateTime eta) {
        this.shipperReference = shipperReference; this.purchaseOrderNumber = purchaseOrderNumber;
        this.originLocationCode = originCode; this.originLocationName = originName;
        this.destinationLocationCode = destinationCode; this.destinationLocationName = destinationName;
        this.cargoDescription = cargoDescription; this.packageCount = packageCount; this.grossWeight = grossWeight;
        this.weightUnit = weightUnit; this.containerCount = containerCount; this.carrierName = carrierName;
        this.vesselName = vesselName; this.voyageNumber = voyageNumber; this.flightNumber = flightNumber;
        this.etd = etd; this.eta = eta;
    }

    public void archive() {
        if (status == Status.ARCHIVED) return;
        status = Status.ARCHIVED;
        archivedAt = LocalDateTime.now();
    }

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); if (version == null) version = 0L; }
    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
