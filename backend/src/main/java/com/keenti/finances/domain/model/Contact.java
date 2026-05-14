package com.keenti.finances.domain.model;

public class Contact {

    private Long id;
    private String name;
    private String phone;
    private String email;

    public Contact(Long id, String name, String phone, String email) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
}
