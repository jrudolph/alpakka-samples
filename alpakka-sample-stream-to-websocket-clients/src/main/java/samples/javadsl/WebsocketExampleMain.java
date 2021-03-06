/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package samples.javadsl;

// #imports

import akka.Done;
import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.kafka.ConsumerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.BroadcastHub;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
// #imports

public class WebsocketExampleMain extends AllDirectives {

    private static final Logger log = LoggerFactory.getLogger(WebsocketExampleMain.class);

    private final Helper helper;
    private final String kafkaBootstrapServers;

    private final String topic = "message-topic";
    private final String groupId = "docs-group";

    private ActorSystem actorSystem;
    private Materializer materializer;

    public WebsocketExampleMain(Helper helper) {
        helper.startContainers();
        this.kafkaBootstrapServers = helper.kafkaBootstrapServers;
        this.helper = helper;
    }

    public static void main(String[] args) throws Exception {
        Helper helper = new Helper();
        WebsocketExampleMain main = new WebsocketExampleMain(helper);
        main.run();
        helper.stopContainers();
    }

    private void run() throws Exception {
        actorSystem = ActorSystem.create("KafkaToWebSocket");
        materializer = ActorMaterializer.create(actorSystem.classicSystem());
        Http http = Http.get(actorSystem.classicSystem());

        Flow<Message, Message, ?> webSocketHandler =
            Flow.fromSinkAndSource(
                Sink.ignore(),
                topicSource().map(TextMessage::create));

        final Flow<HttpRequest, HttpResponse, ?> routeFlow = createRoute(webSocketHandler).flow(actorSystem.classicSystem(), materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
                ConnectHttp.toHost("localhost", 8081), materializer);

        binding.toCompletableFuture().get(10, TimeUnit.SECONDS);

        System.out.println("Server online at http://localhost:8081/\nPress RETURN to stop...");
        System.in.read(); // let it run until user presses return
    }

    private Route createRoute(Flow<Message, Message, ?> webSocketHandler) {
        return concat(
                path("events", () -> handleWebSocketMessages(webSocketHandler)),
                path("push", () -> parameter("value", v -> {
                    CompletionStage<Done> written = helper.writeToKafka(topic, v, actorSystem);
                    return onSuccess(written, done -> complete("Ok"));
                }))
        );
    }

    private Source<String, ?> topicSource() {
        ConsumerSettings<Integer, String> kafkaConsumerSettings =
        ConsumerSettings.create(actorSystem.classicSystem(), new IntegerDeserializer(), new StringDeserializer())
                .withBootstrapServers(kafkaBootstrapServers)
                .withGroupId(groupId)
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .withStopTimeout(Duration.ofSeconds(5));

        return
            Consumer.plainSource(kafkaConsumerSettings, Subscriptions.topics(topic))
                    .map(consumerRecord -> consumerRecord.value())
                    // using a broadcast hub here, ensures that all websocket clients will use the same
                    // consumer
                    .runWith(BroadcastHub.of(String.class), materializer);
    }
}
