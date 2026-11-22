package com.rideflow.notification.adapter.out.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rideflow.notification.infrastructure.MessagingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    void shouldReprocessMessagesSafelyUntilDlqEmpty() throws Exception {
        Channel channel = mock(Channel.class);
        GetResponse r1 = mock(GetResponse.class);
        GetResponse r2 = mock(GetResponse.class);
        Envelope e1 = mock(Envelope.class);
        Envelope e2 = mock(Envelope.class);
        AMQP.BasicProperties props = mock(AMQP.BasicProperties.class);

        when(e1.getDeliveryTag()).thenReturn(101L);
        when(e2.getDeliveryTag()).thenReturn(102L);
        when(r1.getEnvelope()).thenReturn(e1);
        when(r2.getEnvelope()).thenReturn(e2);
        when(r1.getProps()).thenReturn(props);
        when(r2.getProps()).thenReturn(props);
        when(r1.getBody()).thenReturn(new byte[]{1, 2});
        when(r2.getBody()).thenReturn(new byte[]{3, 4});

        when(channel.basicGet(RabbitDlqAdapter.DLQ_QUEUE, false))
                .thenReturn(r1)
                .thenReturn(r2)
                .thenReturn(null);

        when(rabbitTemplate.execute(any())).thenAnswer(invocation -> {
            ChannelCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(channel);
        });

        int count = adapter.reprocess();

        assertThat(count).isEqualTo(2);
        verify(channel, times(2)).basicPublish(eq(""), eq(MessagingConfig.TRIP_EVENTS_QUEUE), eq(props), any(byte[].class));
        verify(channel).basicAck(101L, false);
        verify(channel).basicAck(102L, false);
    }

    @Test
    void shouldNackAndRequeueIfPublishFails() throws Exception {
        Channel channel = mock(Channel.class);
        GetResponse r1 = mock(GetResponse.class);
        Envelope e1 = mock(Envelope.class);
        AMQP.BasicProperties props = mock(AMQP.BasicProperties.class);

        when(e1.getDeliveryTag()).thenReturn(999L);
        when(r1.getEnvelope()).thenReturn(e1);
        when(r1.getProps()).thenReturn(props);
        when(r1.getBody()).thenReturn(new byte[]{1});

        when(channel.basicGet(RabbitDlqAdapter.DLQ_QUEUE, false)).thenReturn(r1);
        doThrow(new RuntimeException("Simulated broker error"))
                .when(channel).basicPublish(eq(""), eq(MessagingConfig.TRIP_EVENTS_QUEUE), eq(props), any(byte[].class));

        when(rabbitTemplate.execute(any())).thenAnswer(invocation -> {
            ChannelCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(channel);
        });

        assertThatThrownBy(adapter::reprocess).isInstanceOf(RuntimeException.class);
        verify(channel).basicNack(999L, false, true);
    }
}
