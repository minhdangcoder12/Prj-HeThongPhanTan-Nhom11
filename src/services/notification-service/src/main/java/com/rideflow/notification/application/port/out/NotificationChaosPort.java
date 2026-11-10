package com.rideflow.notification.application.port.out;

/** Port kiểm tra trạng thái lỗi nhân tạo (chaos) cho notification-service. */
public interface NotificationChaosPort {

    boolean shouldFail();
}
