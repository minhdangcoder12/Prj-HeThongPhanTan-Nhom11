package com.rideflow.payment.adapter.in.web;

import com.rideflow.payment.application.PaymentQueries;
import com.rideflow.payment.domain.model.Payment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
class PaymentController {

    private final PaymentQueries queries;

    PaymentController(PaymentQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    List<Payment> recent() {
        return queries.recent();
    }

    @GetMapping("/{id}")
    Payment byId(@PathVariable UUID id) {
        return queries.byId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment " + id + " not found"));
    }
}
