package com.rideflow.user.application.port.out;

import com.rideflow.user.domain.model.User;

public interface TokenIssuer {

    String issue(User user);
}
