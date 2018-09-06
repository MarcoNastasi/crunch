package org.pragmaticminds.crunch.chronicle;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.pragmaticminds.crunch.chronicle.consumers.MemoryManager;
import org.pragmaticminds.crunch.serialization.Deserializer;
import org.pragmaticminds.crunch.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author julian
 * Created by julian on 16.08.18
 */
public class ChronicleConsumerProducerTest {

    private static final Logger logger = LoggerFactory.getLogger(ChronicleConsumerProducerTest.class);

    @Test
    public void produceAndConsume() throws Exception {
        String basePath = System.getProperty("java.io.tmpdir");
        String path = Files.createTempDirectory(Paths.get(basePath), "chronicle-")
                .toAbsolutePath()
                .toString();
        logger.info("Using temp path '{}'", path);

        Properties properties = new Properties();
        properties.put(ChronicleConsumer.CHRONICLE_PATH_KEY, path);
        properties.put(ChronicleConsumer.CHRONICLE_CONSUMER_KEY, "asdf");

        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        Serializer<MyPojo> serializer = getSerializer(mapper);
        Deserializer<MyPojo> deserializer = getDeserializer(mapper);

        // Create before, because moves to end
        MyPojo pojo;
        try (ChronicleConsumer<MyPojo> consumer = new ChronicleConsumer<>(properties, new MemoryManager(), deserializer)) {
            try (ChronicleProducer<MyPojo> producer = new ChronicleProducer<>(properties, serializer)) {
                // Write
                assertTrue(producer.send(new MyPojo("Julian", 123)));
                // Read
                pojo = consumer.poll();
            }
        }

        assertEquals("Julian", pojo.getItem1());
        assertEquals(123, pojo.getItem2());
    }

    @NotNull
    private Serializer<MyPojo> getSerializer(ObjectMapper mapper) {
        return new Serializer<MyPojo>() {
            @Override
            public byte[] serialize(MyPojo data) {
                try {
                    return mapper.writeValueAsBytes(data);
                } catch (JsonProcessingException e) {
                    return new byte[0];
                }
            }

            @Override
            public void close() {
                // do nothing.
            }
        };
    }

    @NotNull
    private Deserializer<MyPojo> getDeserializer(ObjectMapper mapper) {
        return new Deserializer<MyPojo>() {
            @Override
            public MyPojo deserialize(byte[] bytes) {
                try {
                    return mapper.readValue(bytes, MyPojo.class);
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            public void close() {
                // Do nothing
            }
        };
    }

    public static class MyPojo {
        private String item1;
        private int item2;

        public MyPojo() {
            // Default for Jackson
        }

        public MyPojo(String item1, int item2) {
            this.item1 = item1;
            this.item2 = item2;
        }

        public String getItem1() {
            return item1;
        }

        public int getItem2() {
            return item2;
        }
    }
}