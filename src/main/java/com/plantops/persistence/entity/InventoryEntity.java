package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;



import java.math.BigDecimal;

import java.time.LocalDate;

import java.util.List;



@Entity

@Table(name = "inventory")

public class InventoryEntity extends WorkspaceScopedEntity {



    public String stockingPointCode;

    public String productCode;

    public BigDecimal onhandQty;

    public BigDecimal reservedQty;

    public BigDecimal qualityHoldQty;

    public BigDecimal inTransitQty;

    public LocalDate etaDate;



    public static List<InventoryEntity> listInWorkspace() {

        return list("workspaceId", ws());

    }



    public static List<InventoryEntity> findByProduct(String productCode) {

        return list("workspaceId = ?1 and productCode = ?2", ws(), productCode);

    }

    public BigDecimal availableQty() {
        BigDecimal reserved = reservedQty != null ? reservedQty : BigDecimal.ZERO;
        BigDecimal hold = qualityHoldQty != null ? qualityHoldQty : BigDecimal.ZERO;
        BigDecimal avail = onhandQty.subtract(reserved).subtract(hold);
        return avail.signum() < 0 ? BigDecimal.ZERO : avail;
    }

}


