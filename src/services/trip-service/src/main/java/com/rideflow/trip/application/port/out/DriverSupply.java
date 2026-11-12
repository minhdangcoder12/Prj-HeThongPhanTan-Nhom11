package com.rideflow.trip.application.port.out;

public interface DriverSupply {

    /** Số tài xế rảnh trong bán kính quanh điểm đón; ném RuntimeException nếu không hỏi được. */
    int availableDrivers(double lat, double lng, double radiusKm);
}
