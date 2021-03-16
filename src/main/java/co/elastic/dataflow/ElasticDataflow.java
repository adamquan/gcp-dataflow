package co.elastic.dataflow;

import com.google.cloud.pubsublite.CloudRegion;
import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.ProjectId;
import com.google.cloud.pubsublite.SubscriptionName;
import com.google.cloud.pubsublite.SubscriptionPath;
import com.google.cloud.pubsublite.cloudpubsub.FlowControlSettings;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsublite.PubsubLiteIO;
import org.apache.beam.sdk.io.gcp.pubsublite.SubscriberOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ElasticDataflow} pipeline is a streaming pipeline which ingest
 * data in JSON format from Cloud Pub/Sub into an Elasticsearch index.
 *
 * <pre>
 * Build and execute:
 * mvn compile exec:java \
 * -Dexec.mainClass=co.elastic.dataflow.ElasticDataflow -Dexec.args=" \
 * --runner=DataflowRunner \
 * --project=[PROJECT_ID] \
 * --inputSubscription=[INPUT_PUBSUB_SUBSCRIPTION] \
 * --addresses=[ELASTIC_SEARCH_ADDRESSES_COMMA_SEPARATED] \
 * --index=[INDEX_NAME]
 * </pre>
 */
public class ElasticDataflow {

	// Instantiate Logger.
	private static final Logger LOG = LoggerFactory.getLogger(ElasticDataflow.class);

	/**
	 * Main entry point of the pipeline
	 *
	 * @param args
	 *            The command-line args passed by the executor.
	 */
	public static void main(String[] args) {
		PipelineOptionsFactory.register(IndexerOptions.class);
		IndexerOptions options = PipelineOptionsFactory.fromArgs(args).withValidation().as(IndexerOptions.class);

		options.setStreaming(true);
		
		run(options);
	}

	/**
	 * Runs the pipeline to completion with the specified options.
	 *
	 * @param options
	 *            The execution options.
	 * @return The pipeline result.
	 */
	private static void run(IndexerOptions options) {
		Pipeline pipeline = Pipeline.create(options);
 
		/**
		 * The pipeline executes the following steps: 
		 * 1. Read messages as Strings from PubSub. 
		 * 2. Group messages into fixed size windows.
		 * 3. Write logs into elasticsearch index.
		 */
		pipeline
			// 1). Read messages as Strings from PubSub.
			//.apply("Read PubSub Messages", PubsubIO.readStrings().fromTopic(options.getInputTopic()))
			.apply("Read logs from PubSub",
					PubsubIO.readStrings().fromSubscription(options.getInputSubscription()))
					//PubsubLiteIO.read(getSubscriberOptions()))

	        // 2) Group the messages into fixed-sized minute intervals.
//			.apply(Window.<String>into(FixedWindows.of(Duration.standardSeconds(options.getWindowSize().get()))))
			.apply( "Group logs into internals",
					Window.<String>into(FixedWindows.of(Duration.standardSeconds(1))))

			// 3). Window of logs are written to Elasticsearch index.
			.apply("Write logs to elasticsearch", new IngestIntoElasticsearch());

	    // Execute the pipeline and wait until it finishes running.
		try {
			pipeline.run().waitUntilFinish();
		}
		catch (UnsupportedOperationException e) {
			// Do nothing
		} catch (Exception e) {
			LOG.error(e.getMessage());
		    e.printStackTrace();
		}
	}
	
	// experimenting with pub/sub lite
	private static SubscriberOptions getSubscriberOptions() {
		SubscriptionPath subscriptionPath = SubscriptionPath.newBuilder()
				.setLocation(CloudZone.of(CloudRegion.of("us-central1"), 'a')).setProject(ProjectId.of("1059491012611"))
				.setName(SubscriptionName.of("es-logs-export-sub-lite")).build();

		FlowControlSettings flowControlSettings = FlowControlSettings.builder()
				// 10 MiB. Must be greater than the allowed size of the largest
				// message (1 MiB)
				.setBytesOutstanding(10 * 1024 * 1024L)
				// 1,000 outstanding messages. Must be >0
				.setMessagesOutstanding(1000L).build();

		SubscriberOptions subscriberOptions = SubscriberOptions.newBuilder().setSubscriptionPath(subscriptionPath)
				.setFlowControlSettings(flowControlSettings).build();

		return subscriberOptions;
	}
}
