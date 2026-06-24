package com.app.billing.controller;

import org.springframework.web.bind.annotation.*;

import com.app.billing.dto.AssignPlanRequest;
import com.app.billing.dto.CreatePlanRequest;
import com.app.billing.entity.Plan;
import com.app.billing.entity.Subscription;
import com.app.billing.service.BillingService;

@RestController
@RequestMapping("/billing")
public class BillingController {

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    @PostMapping("/plan")
    public Plan createPlan(@RequestBody CreatePlanRequest request) {
        return service.createPlan(request);
    }

    @PostMapping("/assign")
    public Subscription assign(@RequestBody AssignPlanRequest request) {
        return service.assignPlan(request);
    }
}