package com.redhat.example.fact;

import com.fasterxml.jackson.annotation.JsonBackReference;

public class ExampleFactSon {

    @JsonBackReference
    ExampleFactParent parent;

    private String id;
    private String name;

    public ExampleFactParent getParent() {
        return parent;
    }

    public void setParent(ExampleFactParent parent) {
        this.parent = parent;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
