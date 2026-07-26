package com.djc.rentbook.property;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RoomRecord {
    private Long id;
    private Long propertyId;
    private String roomNo;
    private String floor;
    private BigDecimal area;
    private BigDecimal rentAmount;
    private BigDecimal depositAmount;
    private String status;
    private Integer payCycleMonths;
    private LocalDate nextDueDate;
    private LocalDate nextPeriodStartDate;
    private Integer collectionDay;
    private Long currentRentalId;
    private LocalDate lastPaidDate;
    private LocalDate leaseStartDate;
    private LocalDate leaseEndDate;
    private String orientation;
    private String tags;
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }
    public BigDecimal getArea() { return area; }
    public void setArea(BigDecimal area) { this.area = area; }
    public BigDecimal getRentAmount() { return rentAmount; }
    public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPayCycleMonths() { return payCycleMonths; }
    public void setPayCycleMonths(Integer payCycleMonths) { this.payCycleMonths = payCycleMonths; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
    public LocalDate getNextPeriodStartDate() { return nextPeriodStartDate; }
    public void setNextPeriodStartDate(LocalDate nextPeriodStartDate) { this.nextPeriodStartDate = nextPeriodStartDate; }
    public Integer getCollectionDay() { return collectionDay; }
    public void setCollectionDay(Integer collectionDay) { this.collectionDay = collectionDay; }
    public Long getCurrentRentalId() { return currentRentalId; }
    public void setCurrentRentalId(Long currentRentalId) { this.currentRentalId = currentRentalId; }
    public LocalDate getLastPaidDate() { return lastPaidDate; }
    public void setLastPaidDate(LocalDate lastPaidDate) { this.lastPaidDate = lastPaidDate; }
    public LocalDate getLeaseStartDate() { return leaseStartDate; }
    public void setLeaseStartDate(LocalDate leaseStartDate) { this.leaseStartDate = leaseStartDate; }
    public LocalDate getLeaseEndDate() { return leaseEndDate; }
    public void setLeaseEndDate(LocalDate leaseEndDate) { this.leaseEndDate = leaseEndDate; }
    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
