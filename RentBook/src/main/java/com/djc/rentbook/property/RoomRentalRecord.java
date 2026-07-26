package com.djc.rentbook.property;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RoomRentalRecord {
    private Long id;
    private Long roomId;
    private String status;
    private LocalDate leaseStartDate;
    private LocalDate leaseEndDate;
    private LocalDate actualEndDate;
    private BigDecimal rentAmount;
    private BigDecimal depositAmount;
    private Integer payCycleMonths;
    private LocalDate nextCollectionDate;
    private Integer collectionDay;
    private LocalDate nextPeriodStartDate;
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getLeaseStartDate() { return leaseStartDate; }
    public void setLeaseStartDate(LocalDate leaseStartDate) { this.leaseStartDate = leaseStartDate; }
    public LocalDate getLeaseEndDate() { return leaseEndDate; }
    public void setLeaseEndDate(LocalDate leaseEndDate) { this.leaseEndDate = leaseEndDate; }
    public LocalDate getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(LocalDate actualEndDate) { this.actualEndDate = actualEndDate; }
    public BigDecimal getRentAmount() { return rentAmount; }
    public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public Integer getPayCycleMonths() { return payCycleMonths; }
    public void setPayCycleMonths(Integer payCycleMonths) { this.payCycleMonths = payCycleMonths; }
    public LocalDate getNextCollectionDate() { return nextCollectionDate; }
    public void setNextCollectionDate(LocalDate nextCollectionDate) { this.nextCollectionDate = nextCollectionDate; }
    public Integer getCollectionDay() { return collectionDay; }
    public void setCollectionDay(Integer collectionDay) { this.collectionDay = collectionDay; }
    public LocalDate getNextPeriodStartDate() { return nextPeriodStartDate; }
    public void setNextPeriodStartDate(LocalDate nextPeriodStartDate) { this.nextPeriodStartDate = nextPeriodStartDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
