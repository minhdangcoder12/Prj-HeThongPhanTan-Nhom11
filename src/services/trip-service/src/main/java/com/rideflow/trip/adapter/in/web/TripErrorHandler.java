package com.rideflow.trip.adapter.in.web;

import com.rideflow.trip.domain.model.InvalidTripTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/** Maps domain/application errors to RFC 7807 ProblemDetail (rule R2). */
@RestControllerAdvice
class TripErrorHandler {

    @ExceptionHandler(InvalidTripTransitionException.class)
    ProblemDetail wrongStatus(InvalidTripTransitionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
