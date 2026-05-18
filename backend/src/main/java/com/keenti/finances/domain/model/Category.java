package com.keenti.finances.domain.model;

public class Category {

    private Long id;
    private String name;
    private String type;
    private String color;

    public Category(Long id, String name, String type, String color) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.color = color;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getColor() { return color; }
}
