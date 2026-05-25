package com.keenti.finances.domain.model;

public class Category {

    private Long id;
    private String name;
    private String type;
    private int hue;

    public Category(Long id, String name, String type, int hue) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.hue = hue;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public int getHue() { return hue; }
}
