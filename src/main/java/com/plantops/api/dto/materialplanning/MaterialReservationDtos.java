package com.plantops.api.dto.materialplanning;

import java.time.LocalDate;
import java.util.List;

public final class MaterialReservationDtos {

    private MaterialReservationDtos() {
    }

    public record PeriodDemandRowDto(
            String demandId,
            String sourceType,
            LocalDate needDate,
            double quantity,
            double peggedQty,
            double unpeggedQty,
            String pispId,
            String periodId) {
    }

    public record PeriodDemandListDto(
            String pispId,
            String periodFrom,
            String periodTo,
            List<PeriodDemandRowDto> demands) {
    }

    public record EligibleSupplyRowDto(
            String supplyId,
            String supplyType,
            LocalDate availableDate,
            double availableQty,
            double peggedQty,
            double unpeggedQty) {
    }

    public record EligibleSupplyListDto(
            String demandId,
            List<EligibleSupplyRowDto> supplies) {
    }

    public record CreateFulfillmentRequest(
            String demandId,
            String supplyId,
            Double quantity,
            String source) {
    }

    public record FulfillmentDto(
            String fulfillmentId,
            String demandId,
            String supplyId,
            double quantity,
            String type,
            double demandUnpeggedQty,
            double supplyUnpeggedQty) {
    }

    public record AutoReservationRequest(
            String anchorType,
            String anchorId,
            Double maxQty) {
    }

    public record AutoReservationResultDto(
            List<FulfillmentDto> fulfillments,
            double reservedQty,
            double remainingUnpeggedQty) {
    }

    public record ReservationAlertDto(
            String alertType,
            String demandId,
            String supplyId,
            String periodId,
            String message) {
    }
}
