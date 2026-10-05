package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String name;
    private String email;
    private String role;
}