package com.app.resource.dto;

import java.util.List;

import com.app.resource.enumtype.ResourceAction;

public class ResourceActionRequest {

    private ResourceAction action;

    private List<String> ids;

    private String destination;

    public ResourceAction getAction() {
        return action;
    }

    public List<String> getIds() {
        return ids;
    }

    public String getDestination() {
        return destination;
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
}