package com.tradeoperationsplatform.apiserver.domain.auth.dto;

import jakarta.validation.constraints.*;

public record SignupRequest(@NotBlank @Size(max=200) String organizationName, @Size(max=100) String businessNumber, @NotBlank @Size(max=200) String name,
                            @NotBlank @Email String email, @NotBlank @Size(min = 8, max = 72) String password,
                            @Size(max=100) String phone) {}
