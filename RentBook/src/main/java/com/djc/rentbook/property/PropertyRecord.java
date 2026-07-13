package com.djc.rentbook.property;

public class PropertyRecord {
    private Long id;
    private String name;
    private String address;
    private String district;
    private String landlordName;
    private String landlordPhone;
    private String manager;
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getLandlordName() { return landlordName; }
    public void setLandlordName(String landlordName) { this.landlordName = landlordName; }
    public String getLandlordPhone() { return landlordPhone; }
    public void setLandlordPhone(String landlordPhone) { this.landlordPhone = landlordPhone; }
    public String getManager() { return manager; }
    public void setManager(String manager) { this.manager = manager; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
