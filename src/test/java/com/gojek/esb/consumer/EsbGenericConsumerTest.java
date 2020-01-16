package com.gojek.esb.consumer;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import com.gojek.esb.config.KafkaConsumerConfig;
import com.gojek.esb.filter.EsbFilterException;
import com.gojek.esb.filter.Filter;
import com.gojek.esb.metrics.Instrumentation;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EsbGenericConsumerTest {
    @Mock
    private KafkaConsumer kafkaConsumer;

    @Mock
    private ConsumerRecords consumerRecords;

    @Mock
    private Offsets offsets;

    @Mock
    private Filter filter;

    @Mock
    private Instrumentation instrumentation;

    @Mock
    private KafkaConsumerConfig consumerConfig;

    private TestMessage message;

    private TestKey key;

    private EsbGenericConsumer esbGenericConsumer;

    @Before
    public void setUp() {
        message = TestMessage.newBuilder().setOrderNumber("123").setOrderUrl("abc").setOrderDetails("details").build();
        key = TestKey.newBuilder().setOrderNumber("123").setOrderUrl("abc").build();
        esbGenericConsumer = new EsbGenericConsumer(kafkaConsumer, consumerConfig, filter, offsets, instrumentation);
        when(consumerConfig.getPollTimeOut()).thenReturn(500L);
        when(kafkaConsumer.poll(Duration.ofMillis(500L))).thenReturn(consumerRecords);
    }

    @Test
    public void getsMessagesFromEsbLog() throws EsbFilterException {
        ConsumerRecord<byte[], byte[]> record1 = new ConsumerRecord<>("topic1", 1, 0, key.toByteArray(), message.toByteArray());
        ConsumerRecord<byte[], byte[]> record2 = new ConsumerRecord<>("topic2", 1, 0, key.toByteArray(), message.toByteArray());
        when(consumerRecords.iterator()).thenReturn(Arrays.asList(record1, record2).iterator());

        EsbMessage expectedMsg1 = new EsbMessage(key.toByteArray(), message.toByteArray(), "topic1", 0, 100);
        EsbMessage expectedMsg2 = new EsbMessage(key.toByteArray(), message.toByteArray(), "topic2", 0, 100);

        when(filter.filter(any())).thenReturn(Arrays.asList(expectedMsg1, expectedMsg2));

        List<EsbMessage> messages = esbGenericConsumer.readMessages();

        assertNotNull(messages);
        assertThat(messages.size(), is(2));
        assertEquals(expectedMsg1, messages.get(0));
        assertEquals(expectedMsg2, messages.get(1));
    }

    @Test
    public void getsMessagesFromEsbLogWithHeadersIfKafkaHeadersAreSet() throws EsbFilterException {
        Headers headers = new RecordHeaders();
        ConsumerRecord<byte[], byte[]> record1 = new ConsumerRecord<>("topic1", 1, 0, 0, TimestampType.CREATE_TIME, 0L, 0, 0, key.toByteArray(), message.toByteArray(), headers);
        ConsumerRecord<byte[], byte[]> record2 = new ConsumerRecord<>("topic2", 1, 0, 0, TimestampType.CREATE_TIME, 0L, 0, 0, key.toByteArray(), message.toByteArray(), headers);
        when(consumerRecords.iterator()).thenReturn(Arrays.asList(record1, record2).iterator());

        EsbMessage expectedMsg1 = new EsbMessage(key.toByteArray(), message.toByteArray(), "topic1", 0, 100, headers, 1L, 1L);
        EsbMessage expectedMsg2 = new EsbMessage(key.toByteArray(), message.toByteArray(), "topic2", 0, 100, headers, 1L, 1L);

        when(filter.filter(any())).thenReturn(Arrays.asList(expectedMsg1, expectedMsg2));

        List<EsbMessage> messages = esbGenericConsumer.readMessages();

        assertNotNull(messages);
        assertThat(messages.size(), is(2));
        assertEquals(expectedMsg1, messages.get(0));
        assertEquals(expectedMsg2, messages.get(1));
    }

    @Test
    public void getsFilteredMessagesFromEsbLog() throws EsbFilterException {
        ConsumerRecord<byte[], byte[]> record1 = new ConsumerRecord<>("topic1", 1, 0, key.toByteArray(), message.toByteArray());
        ConsumerRecord<byte[], byte[]> record2 = new ConsumerRecord<>("topic2", 1, 0, key.toByteArray(), message.toByteArray());
        when(consumerRecords.iterator()).thenReturn(Arrays.asList(record1, record2).iterator());
        when(consumerConfig.getFilterExpression()).thenReturn("test");

        EsbMessage expectedMsg1 = new EsbMessage(key.toByteArray(), message.toByteArray(), "topic1", 0, 100);

        when(filter.filter(any())).thenReturn(Arrays.asList(expectedMsg1));

        List<EsbMessage> messages = esbGenericConsumer.readMessages();

        assertNotNull(messages);
        assertThat(messages.size(), is(1));
        assertEquals(expectedMsg1, messages.get(0));
        verify(instrumentation, times(1)).captureFilteredMessageCount(1, "test");

    }

    @Test
    public void shouldCallCommitOnOffsets() {
        esbGenericConsumer.commit();

        verify(offsets, times(1)).commit(any());
    }

    @Test
    public void shouldCallCloseOnConsumer() {
        esbGenericConsumer.close();

        verify(kafkaConsumer).close();
    }

    @Test
    public void shouldSuppressExceptionOnClose() {
        doThrow(new RuntimeException()).when(kafkaConsumer).close();

        try {
            esbGenericConsumer.close();
        } catch (Exception kafkaConsumerException) {
            fail("Failed to supress exception on close");
        }
    }
}
