package org.hobbit.core.components;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.apache.commons.io.IOUtils;
import org.hobbit.core.Commands;
import org.hobbit.core.Constants;
import org.hobbit.core.data.RabbitQueue;
import org.hobbit.core.rabbit.RabbitMQUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;

/**
 * This abstract class implements basic functions that can be used to implement
 * a task generator.
 * 
 * The following environment variables are expected:
 * <ul>
 * <li>{@link Constants#GENERATOR_ID_KEY}</li>
 * <li>{@link Constants#GENERATOR_COUNT_KEY}</li>
 * </ul>
 * 
 * @author Michael R&ouml;der (roeder@informatik.uni-leipzig.de)
 *
 */
public abstract class AbstractTaskGenerator extends AbstractCommandReceivingComponent implements
        GeneratedDataReceivingComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTaskGenerator.class);

    // /**
    // * Default value of the {@link #maxParallelProcessedMsgs} attribute.
    // */
//    private static final int DEFAULT_MAX_PARALLEL_PROCESSED_MESSAGES = 100;

    /**
     * Mutex used to wait for the start signal after the component has been
     * started and initialized.
     */
    private Semaphore startTaskGenMutex = new Semaphore(0);
    // /**
    // * Mutex used to wait for the terminate signal.
    // */
    // private Semaphore terminateMutex = new Semaphore(0);
    // /**
    // * Semaphore used to control the number of messages that can be processed
    // in
    // * parallel.
    // */
    // private Semaphore currentlyProcessedMessages;
    /**
     * The id of this generator.
     */
    private int generatorId;
    /**
     * The number of task generators created by the benchmark controller.
     */
    private int numberOfGenerators;
    /**
     * The task id that will be assigned to the next task generated by this
     * generator.
     */
    private long nextTaskId;
    // /**
    // * The maximum number of incoming messages that are processed in parallel.
    // * Additional messages have to wait.
    // */
    // private int maxParallelProcessedMsgs =
    // DEFAULT_MAX_PARALLEL_PROCESSED_MESSAGES;
    // /**
    // * Name of the incoming queue with which the task generator can receive
    // data
    // * from the data generators.
    // */
    // protected String dataGen2TaskGenQueueName;
    // /**
    // * The Channel of the incoming queue with which the task generator can
    // * receive data from the data generators.
    // */
    // protected Channel dataGen2TaskGen;
    // /**
    // * Name of the queue to the system.
    // */
    // protected String taskGen2SystemQueueName;
    // /**
    // * Channel of the queue to the system.
    // */
    // protected Channel taskGen2System;
    // /**
    // * Name of the queue to the evaluation storage.
    // */
    // protected String taskGen2EvalStoreQueueName;
    // /**
    // * Channel of the queue to the evaluation storage.
    // */
    // protected Channel taskGen2EvalStore;
    protected RabbitQueue taskGen2SystemQueue;
    protected RabbitQueue taskGen2EvalStoreQueue;
    protected RabbitQueue dataGen2TaskGenQueue;

    protected QueueingConsumer consumer;
    protected boolean runFlag;

    @Override
    public void init() throws Exception {
        super.init();
        Map<String, String> env = System.getenv();

        if (!env.containsKey(Constants.GENERATOR_ID_KEY)) {
            throw new IllegalArgumentException("Couldn't get \"" + Constants.GENERATOR_ID_KEY
                    + "\" from the environment. Aborting.");
        }
        try {
            generatorId = Integer.parseInt(env.get(Constants.GENERATOR_ID_KEY));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Couldn't get \"" + Constants.GENERATOR_ID_KEY
                    + "\" from the environment. Aborting.", e);
        }
        nextTaskId = generatorId;

        if (!env.containsKey(Constants.GENERATOR_COUNT_KEY)) {
            throw new IllegalArgumentException("Couldn't get \"" + Constants.GENERATOR_COUNT_KEY
                    + "\" from the environment. Aborting.");
        }
        try {
            numberOfGenerators = Integer.parseInt(env.get(Constants.GENERATOR_COUNT_KEY));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Couldn't get \"" + Constants.GENERATOR_COUNT_KEY
                    + "\" from the environment. Aborting.", e);
        }

        taskGen2SystemQueue = createDefaultRabbitQueue(generateSessionQueueName(Constants.TASK_GEN_2_SYSTEM_QUEUE_NAME));
        taskGen2EvalStoreQueue = createDefaultRabbitQueue(generateSessionQueueName(Constants.TASK_GEN_2_EVAL_STORAGE_QUEUE_NAME));

        // currentlyProcessedMessages = new Semaphore(maxParallelProcessedMsgs);
        // currentlyProcessedMessages = new
        // Semaphore(DEFAULT_MAX_PARALLEL_PROCESSED_MESSAGES);

        // @SuppressWarnings("resource")
        // GeneratedDataReceivingComponent receiver = this;

        // dataGen2TaskGenQueueName =
        // generateSessionQueueName(Constants.DATA_GEN_2_TASK_GEN_QUEUE_NAME);
        // dataGen2TaskGen = connection.createChannel();
        // dataGen2TaskGen.queueDeclare(dataGen2TaskGenQueueName, false, false,
        // true, null);
        // consumer = new QueueingConsumer(dataGen2TaskGen);
        // dataGen2TaskGen.basicConsume(dataGen2TaskGenQueueName, false,
        // consumer);
        dataGen2TaskGenQueue = createDefaultRabbitQueue(generateSessionQueueName(Constants.DATA_GEN_2_TASK_GEN_QUEUE_NAME));
        consumer = new QueueingConsumer(dataGen2TaskGenQueue.channel);
        dataGen2TaskGenQueue.channel.basicConsume(dataGen2TaskGenQueue.name, false, consumer);

        // dataGen2TaskGen.basicConsume(dataGen2TaskGenQueueName, true, new
        // DefaultConsumer(dataGen2TaskGen) {
        // @Override
        // public void handleDelivery(String consumerTag, Envelope envelope,
        // BasicProperties properties, byte[] body)
        // throws IOException {
        // LOGGER.info("Received data " + dataCount);
        // ++dataCount;
        // try {
        // currentlyProcessedMessages.acquire();
        // try {
        // receiver.receiveGeneratedData(body);
        // } catch (Exception e) {
        // LOGGER.error("Got exception while trying to process incoming data.",
        // e);
        // } finally {
        // currentlyProcessedMessages.release();
        // }
        // } catch (InterruptedException e) {
        // throw new IOException("Interrupted while waiting for mutex.", e);
        // }
        // }
        // });
    }

    @Override
    public void run() throws Exception {
        sendToCmdQueue(Commands.TASK_GENERATOR_READY_SIGNAL);
        // Wait for the start message
        startTaskGenMutex.acquire();
        runFlag = true;

        // terminateMutex.acquire();
        // // wait until all messages have been read from the queue
        // while (dataGen2TaskGen.messageCount(dataGen2TaskGenQueueName) > 0) {
        // LOGGER.info("Waiting for remaining data to be processed: "
        // + dataGen2TaskGen.messageCount(dataGen2TaskGenQueueName));
        // Thread.sleep(1000);
        // }
        // // Collect all open mutex counts to make sure that there is no
        // message
        // // that is still processed
        // Thread.sleep(1000);
        // LOGGER.info("Waiting data processing to finish... (" + debugCount +
        // " tasks generated. "
        // + currentlyProcessedMessages.availablePermits() + " are available)");
        // currentlyProcessedMessages.acquire(DEFAULT_MAX_PARALLEL_PROCESSED_MESSAGES);

        Delivery delivery;
        int count = 0;
        while (runFlag || (dataGen2TaskGenQueue.messageCount() > 0)) {
            delivery = consumer.nextDelivery(3000);
            if (delivery != null) {
                generateTask(delivery.getBody());
                ++count;
            }
        }

//        // Unfortunately, we have to wait until all messages are consumed
//        while ((taskGen2SystemQueue.messageCount() + taskGen2EvalStoreQueue.messageCount()) > 0) {
//            Thread.sleep(500);
//        }
        LOGGER.info("Terminating after " + count + " processed messages.");
    }

    @Override
    public void receiveGeneratedData(byte[] data) {
        try {
            generateTask(data);
        } catch (Exception e) {
            LOGGER.error("Exception while generating task.", e);
        }
    }

    /**
     * Generates a task from the given data, sends it to the system, takes the
     * timestamp of the moment at which the message has been sent to the system
     * and sends it together with the expected response to the evaluation
     * storage.
     * 
     * @param data
     *            incoming data generated by a data generator
     * @throws Exception
     *             if a sever error occurred
     */
    protected abstract void generateTask(byte[] data) throws Exception;

    /**
     * Generates the next unique ID for a task.
     * 
     * @return the next unique task ID
     */
    protected synchronized String getNextTaskId() {
        String taskIdString = Long.toString(nextTaskId);
        nextTaskId += numberOfGenerators;
        return taskIdString;
    }

    @Override
    public void receiveCommand(byte command, byte[] data) {
        // If this is the signal to start the data generation
        if (command == Commands.TASK_GENERATOR_START_SIGNAL) {
            LOGGER.info("Received signal to start.");
            // release the mutex
            startTaskGenMutex.release();
        } else if (command == Commands.DATA_GENERATION_FINISHED) {
            LOGGER.info("Received signal to finish.");
            // release the mutex
            // terminateMutex.release();
            runFlag = false;
        }
    }

    /**
     * This method sends the given data and the given timestamp of the task with
     * the given task id to the evaluation storage.
     * 
     * @param taskIdString
     *            the id of the task
     * @param timestamp
     *            the timestamp of the moment in which the task has been sent to
     *            the system
     * @param data
     *            the expected response for the task with the given id
     * @throws IOException
     *             if there is an error during the sending
     */
    protected void sendTaskToEvalStorage(String taskIdString, long timestamp, byte[] data) throws IOException {
        // taskGen2EvalStore.basicPublish("", taskGen2EvalStoreQueueName,
        // MessageProperties.PERSISTENT_BASIC,
        // RabbitMQUtils.writeByteArrays(null, new byte[][] {
        // RabbitMQUtils.writeString(taskIdString), data },
        // RabbitMQUtils.writeLong(timestamp)));
        taskGen2EvalStoreQueue.channel.basicPublish("", taskGen2EvalStoreQueue.name,
                MessageProperties.PERSISTENT_BASIC, RabbitMQUtils.writeByteArrays(null,
                        new byte[][] { RabbitMQUtils.writeString(taskIdString), data },
                        RabbitMQUtils.writeLong(timestamp)));
    }

    /**
     * Sends the given task with the given task id and data to the system.
     * 
     * @param taskIdString
     *            the id of the task
     * @param data
     *            the data of the task
     * @throws IOException
     *             if there is an error during the sending
     */
    protected void sendTaskToSystemAdapter(String taskIdString, byte[] data) throws IOException {
        // taskGen2System.basicPublish("", taskGen2SystemQueueName,
        // MessageProperties.PERSISTENT_BASIC,
        // RabbitMQUtils.writeByteArrays(new byte[][] {
        // RabbitMQUtils.writeString(taskIdString), data }));
        taskGen2SystemQueue.channel.basicPublish("", taskGen2SystemQueue.name, MessageProperties.PERSISTENT_BASIC,
                RabbitMQUtils.writeByteArrays(new byte[][] { RabbitMQUtils.writeString(taskIdString), data }));
    }

    public int getGeneratorId() {
        return generatorId;
    }

    public int getNumberOfGenerators() {
        return numberOfGenerators;
    }

    @Override
    public void close() throws IOException {
        IOUtils.closeQuietly(dataGen2TaskGenQueue);
        IOUtils.closeQuietly(taskGen2EvalStoreQueue);
        IOUtils.closeQuietly(taskGen2SystemQueue);
        super.close();
    }
}
