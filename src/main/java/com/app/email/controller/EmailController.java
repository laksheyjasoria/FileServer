package com.app.email.controller;

import com.app.email.dto.EmailRequest;
import com.app.email.service.EmailService;
import com.app.core.response.ApiResponse;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/email")
public class EmailController {

    private final EmailService service;

    public EmailController(EmailService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public ApiResponse<String> send(@Valid @RequestBody EmailRequest request) {

        service.send(request);

        return ApiResponse.success("Email sent successfully");
    }
}