package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class UserContext {

    private Long userId;
    private String workosId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getWorkosId() {
        return workosId;
    }

    public void setWorkosId(String workosId) {
        this.workosId = workosId;
    }
}
