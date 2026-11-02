package com.rideflow.gateway.monitor;

import com.rideflow.gateway.RideflowServices;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Chaos routes exist only in the demo profile (rule R8); services implement /api/chaos/** themselves. */
@Configuration
@Profile("demo")
class DemoRoutes {

    @Bean
    RouteLocator chaosRoutes(RouteLocatorBuilder routes, RideflowServices services) {
        return routes.routes()
                .route("chaos-trip", r -> r.path("/api/chaos/trip/**").uri(services.services().get("trip")))
                .route("chaos-notification", r -> r.path("/api/chaos/notification/**")
                        .uri(services.services().get("notification")))
                .build();
    }
}
