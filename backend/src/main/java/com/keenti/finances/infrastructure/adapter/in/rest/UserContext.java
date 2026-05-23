package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class UserContext {

    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
