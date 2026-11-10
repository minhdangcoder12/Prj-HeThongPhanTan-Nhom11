package com.rideflow.matching.application.port.out;

import java.util.List;
import java.util.UUID;

/** Port tìm kiếm tài xế gần nhất và kiểm tra nguồn cung tài xế cho matching-service. */
public interface DriverLocator {

    /** Tìm danh sách tài xế rảnh gần toạ độ nhất, loại trừ các tài xế trong danh sách excluded. */
    List<UUID> nearestAvailable(double lat, double lng, int limit, List<UUID> excluded);

    /** Đếm số lượng tài xế rảnh trong bán kính radiusKm (dùng cho tính cước động). */
    int countAvailable(double lat, double lng, double radiusKm);
}

