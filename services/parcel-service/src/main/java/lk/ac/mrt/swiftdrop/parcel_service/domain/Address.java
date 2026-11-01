package lk.ac.mrt.swiftdrop.parcel_service.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;

@Embeddable
public class Address {
    @Column(length=120) private String contactName;
    @Column(length=25) private String phone;
    @Column(length=255) private String line1;
    @Column(length=100) private String city;
    @Column(length=20) private String postalCode;
    @Column(length=80) private String zone;

    public String getContactName() { return contactName; }

    public String getPhone() { return phone; }

    public String getLine1() { return line1; }

    public String getCity() { return city; }

    public String getPostalCode() { return postalCode; }

    public String getZone() { return zone; }

    protected Address() {}

    public Address(String contactName, String phone, String line1, String city, String postalCode, String zone) {
        this.contactName = contactName;
        this.phone = phone;
        this.line1 = line1;
        this.city = city;
        this.postalCode = postalCode;
        this.zone = zone;
    }
}
