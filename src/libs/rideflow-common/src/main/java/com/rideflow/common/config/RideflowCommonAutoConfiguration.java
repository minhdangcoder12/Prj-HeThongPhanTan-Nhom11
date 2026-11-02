package com.rideflow.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.common.event.RideflowMessaging;
import com.rideflow.common.outbox.InboxGuard;
import com.rideflow.common.outbox.OutboxRelay;
import com.rideflow.common.outbox.OutboxWriter;
import com.rideflow.common.web.CorrelationIdFilter;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

/** Wires the technical building blocks into every service that depends on rideflow-common. */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        RabbitAutoConfiguration.class})
@EnableScheduling
public class RideflowCommonAutoConfiguration {

    @Bean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    TopicExchange rideflowEventsExchange() {
        return new TopicExchange(RideflowMessaging.EVENTS_EXCHANGE);
    }

    @Bean
    TopicExchange rideflowDeadLetterExchange() {
        return new TopicExchange(RideflowMessaging.DEAD_LETTER_EXCHANGE);
    }

    /** Lets @RabbitListener methods take EventEnvelope of a payload type directly. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    OutboxWriter outboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new OutboxWriter(jdbc, objectMapper);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbit, TransactionTemplate tx) {
        return new OutboxRelay(jdbc, rabbit, tx);
    }

    @Bean
    InboxGuard inboxGuard(JdbcTemplate jdbc) {
        return new InboxGuard(jdbc);
    }
}
