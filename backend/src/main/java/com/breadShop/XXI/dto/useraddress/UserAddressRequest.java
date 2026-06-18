// dto/useraddress/UserAddressRequest.java
package com.breadShop.XXI.dto.useraddress;

/**
 * ใช้สำหรับรับข้อมูลการสร้างหรือแก้ไขที่อยู่ของผู้ใช้จากลูกค้า | reviewed by peak
 */
public class UserAddressRequest {

    private String name;
    private String recipientName;
    private String phone;
    private String address;
    private String province;
    private String district;
    private String subdistrict;
    private String postcode;
    private Boolean isDefault = false;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getSubdistrict() { return subdistrict; }
    public void setSubdistrict(String subdistrict) { this.subdistrict = subdistrict; }

    public String getPostcode() { return postcode; }
    public void setPostcode(String postcode) { this.postcode = postcode; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
}