package com.rideflow.notification.application.port.out;

/** Port quản lý và xử lý lại message trong Dead Letter Queue. */
public interface DlqPort {

    int getDlqCount();

    int reprocess();
}
