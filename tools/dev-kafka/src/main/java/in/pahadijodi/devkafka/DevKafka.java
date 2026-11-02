package in.pahadijodi.devkafka;

import java.util.concurrent.CountDownLatch;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/** DEV ONLY. Starts a single-node KRaft Kafka on localhost:9092 inside this JVM and blocks. */
public final class DevKafka {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("port", "9092"));
        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 1);
        broker.kafkaPorts(port);
        broker.brokerProperty("auto.create.topics.enable", "true");
        broker.brokerProperty("offsets.topic.replication.factor", "1");
        broker.brokerProperty("transaction.state.log.replication.factor", "1");
        broker.brokerProperty("transaction.state.log.min.isr", "1");
        broker.afterPropertiesSet();
        System.out.println("DEV KAFKA READY bootstrap=" + broker.getBrokersAsString());
        Runtime.getRuntime().addShutdownHook(new Thread(broker::destroy));
        new CountDownLatch(1).await();
    }
}
