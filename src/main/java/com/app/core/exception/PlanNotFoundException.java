package com.app.core.exception;

public class PlanNotFoundException extends AppException {

    public PlanNotFoundException() {
        super(ErrorCode.PLAN_NOT_FOUND, "Plan not found");
    }
}