package com.smartfridge.config;

import com.smartfridge.mqtt.MqttGateway;
import com.smartfridge.mqtt.MqttMessageRouter;
import com.smartfridge.mqtt.Topics;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Spring Integration MQTT wiring. Disabled entirely when app.mqtt.enabled=false (e.g. in tests).
 */
@Configuration
@IntegrationComponentScan(basePackageClasses = MqttGateway.class)
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    @Bean
    public MqttPahoClientFactory mqttClientFactory(MqttProperties props) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{props.getBrokerUrl()});
        options.setCleanSession(props.isCleanSession());
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(props.getKeepAliveSeconds());
        options.setConnectionTimeout(props.getConnectionTimeoutSeconds());
        if (StringUtils.hasText(props.getUsername())) {
            options.setUserName(props.getUsername());
        }
        if (StringUtils.hasText(props.getPassword())) {
            options.setPassword(props.getPassword().toCharArray());
        }
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    // ------------------------------------------------------------------ inbound

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(MqttPahoClientFactory factory,
                                                                   MqttProperties props,
                                                                   MessageChannel mqttInboundChannel) {
        String group = props.getSharedSubscriptionGroup();
        String[] topics = Topics.inboundFilters().stream()
                .map(filter -> StringUtils.hasText(group) ? "$share/" + group + "/" + filter : filter)
                .toArray(String[]::new);

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId(props, "in"), factory, topics);
        adapter.setCompletionTimeout(props.getCompletionTimeoutMs());
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(props.getQos());
        adapter.setOutputChannel(mqttInboundChannel);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public MessageHandler mqttInboundHandler(MqttMessageRouter router) {
        return message -> {
            String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
            router.route(topic, String.valueOf(message.getPayload()));
        };
    }

    // ----------------------------------------------------------------- outbound

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundHandler(MqttPahoClientFactory factory, MqttProperties props) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientId(props, "out"), factory);
        handler.setAsync(false);
        handler.setDefaultQos(props.getQos());
        handler.setDefaultRetained(false);
        handler.setCompletionTimeout(props.getCompletionTimeoutMs());
        return handler;
    }

    private String clientId(MqttProperties props, String role) {
        return props.getClientIdPrefix() + "-" + role + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
