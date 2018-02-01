package me.lyinlong.tools.carwlcities;

import java.util.List;

public class Item {

    private String name;
    private String code;
    private List<Item> items;

    public Item() {
    }

    public Item(String name, String code, List<Item> items) {
        this.name = name;
        this.code = code;
        this.items = items;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }
}
