package com.rideflow.notification.adapter.in.web;

import com.rideflow.notification.application.port.out.DlqPort;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDlqControllerTest {

    private final DlqPort dlqPort = mock(DlqPort.class);
    private final NotificationDlqController controller = new NotificationDlqController(dlqPort);

    @Test
    void shouldReturnDlqStatus() {
        when(dlqPort.getDlqCount()).thenReturn(3);

        Map<String, Object> status = controller.status();

        assertThat(status).containsEntry("count", 3);
        assertThat(status).containsKey("queue");
    }

    @Test
    void shouldReprocessDlqMessages() {
        when(dlqPort.reprocess()).thenReturn(5);
        when(dlqPort.getDlqCount()).thenReturn(0);

        Map<String, Object> result = controller.reprocess();

        verify(dlqPort).reprocess();
        assertThat(result).containsEntry("reprocessed", 5);
        assertThat(result).containsEntry("remaining", 0);
    }
}

