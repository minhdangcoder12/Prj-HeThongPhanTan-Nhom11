package com.rideflow.notification.adapter.out.messaging;

import com.rideflow.notification.infrastructure.MessagingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitDlqAdapterTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final RabbitDlqAdapter adapter = new RabbitDlqAdapter(rabbitTemplate, amqpAdmin);

    @Test
    void shouldReturnDlqMessageCount() {
        Properties props = new Properties();
        props.put(RabbitDlqAdapter.QUEUE_MESSAGE_COUNT_KEY, 7);
        when(amqpAdmin.getQueueProperties(RabbitDlqAdapter.DLQ_QUEUE)).thenReturn(props);

        int count = adapter.getDlqCount();

        assertThat(count).isEqualTo(7);
    }

    @Test
    void shouldReprocessMessagesUntilDlqEmpty() {
        Message msg1 = mock(Message.class);
        Message msg2 = mock(Message.class);

        when(rabbitTemplate.receive(eq(RabbitDlqAdapter.DLQ_QUEUE), anyLong()))
                .thenReturn(msg1)
                .thenReturn(msg2)
                .thenReturn(null);

        int count = adapter.reprocess();

        assertThat(count).isEqualTo(2);
        verify(rabbitTemplate, times(2)).send(eq(""), eq(MessagingConfig.TRIP_EVENTS_QUEUE), any(Message.class));
    }
}
