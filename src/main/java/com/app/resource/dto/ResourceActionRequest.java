package com.app.resource.dto;

import java.util.List;

import com.app.resource.enumtype.ResourceAction;

public class ResourceActionRequest {

    private ResourceAction action;

    private List<String> ids;

    private String destination;

    // optional name (for rename or folder name)
    private String name;

    public ResourceAction getAction() {
        return action;
    }

    public List<String> getIds() {
        return ids;
    }

    public String getDestination() {
        return destination;
    }

    public String getName() {
        return name;
    }

    public void setAction(ResourceAction action) {
        this.action = action;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public void setName(String name) {
        this.name = name;
    }
}