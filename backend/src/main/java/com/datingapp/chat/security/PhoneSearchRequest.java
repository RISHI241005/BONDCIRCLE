package com.datingapp.chat.security;

public class PhoneSearchRequest {
    private String phone;

    public PhoneSearchRequest() {}

    public PhoneSearchRequest(String phone) {
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}