// dto/useraddress/UserAddressResponse.java
package com.breadShop.XXI.dto.useraddress;


// ใช้สำหรับส่งข้อมูลที่อยู่ของผู้ใช้กลับไปยังลูกค้า | reviewed by peak
public class UserAddressResponse {

    private final Integer id;
    private final String name;
    private final String recipientName;
    private final String phone;
    private final String address;
    private final String province;
    private final String district;
    private final String subdistrict;
    private final String postcode;
    private final Boolean isDefault;

    public UserAddressResponse(Integer id, String name, String recipientName,
                                String phone, String address, String province,
                                String district, String subdistrict,
                                String postcode, Boolean isDefault) {
        this.id = id;
        this.name = name;
        this.recipientName = recipientName;
        this.phone = phone;
        this.address = address;
        this.province = province;
        this.district = district;
        this.subdistrict = subdistrict;
        this.postcode = postcode;
        this.isDefault = isDefault;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getProvince() { return province; }
    public String getDistrict() { return district; }
    public String getSubdistrict() { return subdistrict; }
    public String getPostcode() { return postcode; }
    public Boolean getIsDefault() { return isDefault; }
}