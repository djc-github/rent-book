package com.djc.rentbook.property;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RentSettlementRecord {
    private Long id;
    private Long rentalId;
    private Long roomId;
    private LocalDate settlementDate;
    private LocalDate moveOutDate;
    private String reason;
    private BigDecimal rentRefundAmount;
    private BigDecimal depositAmount;
    private BigDecimal depositDeductionAmount;
    private BigDecimal depositRefundAmount;
    private BigDecimal totalRefundAmount;
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRentalId() { return rentalId; }
    public void setRentalId(Long rentalId) { this.rentalId = rentalId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }
    public LocalDate getMoveOutDate() { return moveOutDate; }
    public void setMoveOutDate(LocalDate moveOutDate) { this.moveOutDate = moveOutDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public BigDecimal getRentRefundAmount() { return rentRefundAmount; }
    public void setRentRefundAmount(BigDecimal rentRefundAmount) { this.rentRefundAmount = rentRefundAmount; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public BigDecimal getDepositDeductionAmount() { return depositDeductionAmount; }
    public void setDepositDeductionAmount(BigDecimal depositDeductionAmount) { this.depositDeductionAmount = depositDeductionAmount; }
    public BigDecimal getDepositRefundAmount() { return depositRefundAmount; }
    public void setDepositRefundAmount(BigDecimal depositRefundAmount) { this.depositRefundAmount = depositRefundAmount; }
    public BigDecimal getTotalRefundAmount() { return totalRefundAmount; }
    public void setTotalRefundAmount(BigDecimal totalRefundAmount) { this.totalRefundAmount = totalRefundAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
