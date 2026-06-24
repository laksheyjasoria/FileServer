package com.app.billing.dto;

public class AssignPlanRequest {

    private String userId;

    private String planId;

    private Integer validityDays;

    public String getUserId() {
        return userId;
    }

    public String getPlanId() {
        return planId;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public void setValidityDays(Integer validityDays) {
        this.validityDays = validityDays;
    }
}