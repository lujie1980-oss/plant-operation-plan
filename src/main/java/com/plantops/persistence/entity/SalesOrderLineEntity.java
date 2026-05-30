package com.plantops.persistence.entity;



import com.plantops.domain.SalesOrderLineId;

import jakarta.persistence.Column;

import jakarta.persistence.Entity;

import jakarta.persistence.Table;

import jakarta.persistence.UniqueConstraint;



import java.math.BigDecimal;

import java.time.LocalDate;

import java.time.LocalDateTime;

import java.util.List;



@Entity

@Table(name = "sales_order_line", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "sales_order_no", "sales_order_line_no"

}))

public class SalesOrderLineEntity extends WorkspaceScopedEntity {



    @Column(name = "sales_order_no", nullable = false)

    public String salesOrderNo;



    @Column(name = "sales_order_line_no", nullable = false)

    public int salesOrderLineNo;



    public String customerCode;



    @Column(name = "product_code", nullable = false)

    public String productCode;



    @Column(name = "order_qty", nullable = false)

    public BigDecimal orderQty;



    public String uom;



    public LocalDate promiseDate;



    @Column(name = "due_date", nullable = false)

    public LocalDate dueDate;



    public int priority = 5;



    public int expediteLevel = 0;



    @Column(nullable = false)

    public String status = "OPEN";



    public boolean scheduleLockFlag = false;


    public LocalDateTime lastModifiedTs;



    public SalesOrderLineId toId() {

        return new SalesOrderLineId(salesOrderNo, salesOrderLineNo);

    }



    public static List<SalesOrderLineEntity> listInWorkspace() {

        return list("workspaceId", ws());

    }



    public static long countInWorkspace() {

        return count("workspaceId", ws());

    }



    public static SalesOrderLineEntity findByKey(String salesOrderNo, int lineNo) {

        return find("workspaceId = ?1 and salesOrderNo = ?2 and salesOrderLineNo = ?3", ws(), salesOrderNo, lineNo)

                .firstResult();

    }

}


