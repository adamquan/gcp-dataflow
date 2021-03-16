package co.elastic.dataflow;

import org.apache.beam.sdk.options.Default;
// import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.StreamingOptions;

/**
 * Options interface for specifying the inputs to the {@link com.google.cloud.pso.IndexerMain}
 * pipeline.
 */
//public interface IndexerOptions extends DataflowPipelineOptions {
public interface IndexerOptions extends PipelineOptions, StreamingOptions {
  @Description(
      "The Cloud Pub/Sub subscription to consume from. "
          + "The name should be in the format of "
          + "projects/<project-id>/subscriptions/<subscription-name>.")
  @Default.String("projects/elastic-sa/subscriptions/es-logs-export-sub")
  @Validation.Required
  String getInputSubscription();

  void setInputSubscription(String inputSubscription);

  @Description("Elasticsearch cluster cloud ID")
  @Default.String ("710:dXMtY2VudHJhbDEuZ2NwLmNsb3VkLmVzLmlvJGM0ZDIxYTA0MzI3ZjRhNTI5MmY2OGEzMDBiMjQ2M2NmJDE2NDVlMGI1MjRhMjQ1MzU4NDEyZGE1NmQ3MjM3MzQ1")
  @Validation.Required
  ValueProvider<String> getCloudID();

  void setCloudID(ValueProvider<String> cloudID);

  @Description("Elasticsearch cluster API key")
  @Default.String ("zguey3cBbr2DTKe7jJpN:LYA4e2zDRvySUFz2H_yE2A")
  @Validation.Required
  ValueProvider<String> getApiKey();

  void setApiKey(ValueProvider<String> apiKey);
/**
  @Description("Name of the Elasticsearch index")
  @Default.String ("filebeat-7.10.2-2021.01.26-000001")
  @Validation.Required
  ValueProvider<String> getIndex();

  void setIndex(ValueProvider<String> index);
  
  @Description("Name of the ingestion pipeline")
  @Default.String ("filebeat-7.10.2-googlecloud-vpcflow-pipeline")
  @Validation.Required
  ValueProvider<String> getPipeline();

  void setPipeline(ValueProvider<String> pipeline);
**/
  @Description("Streaming window size in number of seconds")
  @Default.Integer(1)
  ValueProvider<Integer> getWindowSize();

  void setWindowSize(ValueProvider<Integer> value);
}