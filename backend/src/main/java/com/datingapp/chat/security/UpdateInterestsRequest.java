package com.datingapp.chat.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class UpdateInterestsRequest {

    @Valid
    @Size(max = 10, message = "You can add up to 10 interests")
    private List<@NotBlank @Size(max = 40, message = "Each interest must not exceed 40 characters") String> interests = new ArrayList<>();

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }
}
