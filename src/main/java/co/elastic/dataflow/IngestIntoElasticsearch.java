package co.elastic.dataflow;

import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.transforms.PTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.ingest.GetPipelineRequest;
import org.elasticsearch.action.ingest.GetPipelineResponse;
import org.elasticsearch.action.ingest.PutPipelineRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.apache.beam.sdk.values.PDone;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.DoFn;

public class IngestIntoElasticsearch extends PTransform<PCollection<String>, PDone> {

	private static final long serialVersionUID = 1L;
	
	// Instantiate Logger.
	private static final Logger LOG = LoggerFactory.getLogger(ElasticDataflow.class);
	
	private static final String PIPELINE = "gcp-audit-logs";
	
    // The ID of your GCP project
	private static final String projectId = "elastic-sa";

    // The ID of your GCS bucket
	private static final String bucketName = "adam_quan_elastic_gcp_dataflow";

	private static final String kibanaURL = "https://1645e0b524a245358412da56d7237345.us-central1.gcp.cloud.es.io:9243";
	
    private static final String nextLine = "\r\n";
    private static final String twoHyphens = "--";
    
    private static boolean dashboardLoaded = false;

	@Override
	public PDone expand(PCollection<String> logs) {
		logs.apply(
			ParDo.of(
				new DoFn<String, String>() {
					private static final long serialVersionUID = 1L;

					@ProcessElement
			          public void processElement(ProcessContext c) {
						IndexerOptions options = c.getPipelineOptions().as(IndexerOptions.class);
						
						// String cloudId = "710:dXMtY2VudHJhbDEuZ2NwLmNsb3VkLmVzLmlvJGM0ZDIxYTA0MzI3ZjRhNTI5MmY2OGEzMDBiMjQ2M2NmJDE2NDVlMGI1MjRhMjQ1MzU4NDEyZGE1NmQ3MjM3MzQ1";
						String cloudId = options.getCloudID().get();
						//LOG.info("Cloud ID: " + cloudId);

						//String apiKey = "zguey3cBbr2DTKe7jJpN:LYA4e2zDRvySUFz2H_yE2A";
						String apiKey = options.getApiKey().get();
						//LOG.info("API Key: " + apiKey);

						//String index = options.getIndex().get();
						//LOG.info("Index: " + index);

						//String pipeline = options.getPipeline().get();
						//LOG.info("Pipeline: " + pipeline);

						String log = c.element();
			            // LOG.info("A log entry: " + log);
			            
			            //index(cloudId, apiKey, index, pipeline, log);
			            index(cloudId, apiKey, log);
			          }
				}
			)
		);

		return PDone.in(logs.getPipeline());
	}
	
	
//    private void index (String cloudId, String apiKey, String index, String pipeline, String log) {
    private void index (String cloudId, String apiKey, String log) {
			/**
			// Use user name & password authentication
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
        		new UsernamePasswordCredentials("adam", "changeme"));

		// Elasticsearch URL
        //RestClientBuilder builder = RestClient.builder(new HttpHost("c4d21a04327f4a5292f68a300b2463cf.us-central1.gcp.cloud.es.io", 9243, "https"));
		
        builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
            @Override
            public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
        });

        **/

		// Using cloud ID for authentication
		final RestClientBuilder builder = RestClient.builder(cloudId);
    		String apiKeyAuth = Base64.getEncoder().encodeToString(
	    	        (apiKey).getBytes(StandardCharsets.UTF_8));
	    	Header header = new BasicHeader("Authorization", "ApiKey " + apiKeyAuth);
	    	Header[] defaultHeaders = new Header[]{ header };
	    	builder.setDefaultHeaders(defaultHeaders);    	
		
	    	RestHighLevelClient client = new RestHighLevelClient(builder);
	    	
	    	// Load the ingestion pipeline if it does not exist
	    	if (!piplelineExists(client)) {
	    		createPipeline(client);
	    		LOG.info("Created ingestion pipeline: " + PIPELINE);
	    	}
        
	    	// Load the dashboard if it does not exist
	    if (!dashboardLoaded ) {
	    		loadDashboard();
	    		dashboardLoaded = true;
	    }
	    	
        IndexRequest request = new IndexRequest(getIndex()); 
        
        request.source(log, XContentType.JSON);
        request.setPipeline(PIPELINE);
        
        IndexResponse indexResponse;
		try {
			indexResponse = client.index(request, RequestOptions.DEFAULT);

			String id = indexResponse.getId();
	        LOG.info("Document ID: " + id );	        
		} catch (IOException e) {
			LOG.error(e.getMessage());
			e.printStackTrace();
		}
        
		try {
			client.close();
		} catch (IOException e) {
			LOG.error(e.getMessage());
			e.printStackTrace();
		}
    }
    
    // see https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/_other_authentication_methods.html#_elasticsearch_api_keys
    /**
    private void setAPIKey ( RestClientBuilder builder ) {
	    	String apiKeyId = "zguey3cBbr2DTKe7jJpN";
	    	String apiKey = "HxHWk2m4RN-V_qg9cDpuX";
	    	String apiKeyAuth =
	    	    Base64.getEncoder().encodeToString(
	    	        (apiKeyId + ":" + apiKey).getBytes(StandardCharsets.UTF_8));
	    	Header header = new BasicHeader("Authorization", "ApiKey " + apiKeyAuth);
	    	Header[] defaultHeaders = new Header[]{ header };
	    	builder.setDefaultHeaders(defaultHeaders);    	
    }
	**/
    
    private void createPipeline (RestHighLevelClient client) {
	    	PutPipelineRequest request = new PutPipelineRequest(
	    	    PIPELINE, 
	    	    new BytesArray(readPipeline().getBytes(StandardCharsets.UTF_8)), XContentType.JSON 
	    	);
	    	
	    	
	    	try {
			AcknowledgedResponse response = client.ingest().putPipeline(request, RequestOptions.DEFAULT);
		} catch (IOException e) {
			//TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private boolean piplelineExists ( RestHighLevelClient client ) {
    		boolean found = false;
    		GetPipelineRequest request = new GetPipelineRequest(PIPELINE);
    		GetPipelineResponse response = null;
    		try {
				response = client.ingest().getPipeline(request, RequestOptions.DEFAULT);
				found = response.isFound();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
    		return found;
    }
    
    private String readPipeline () {
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();

        // Select all fields. Fields can be selected individually e.g. Storage.BucketField.NAME
        Bucket bucket =
            storage.get(bucketName, Storage.BucketGetOption.fields(Storage.BucketField.values()));
        Blob blob = bucket.get("pipeline");
    		String pipelineString = new String(blob.getContent());
    		// LOG.info(pipelineString);
    		
    		return pipelineString;
    }
    
    private void loadDashboard() {
    	
        // creates a unique boundary based on time stamp
        String boundary = "===" + System.currentTimeMillis() + "===";

    		URL url = null;
    		HttpsURLConnection con = null;
		try {
			url = new URL (kibanaURL + "/api/saved_objects/_import?overwrite=true");
			con = (HttpsURLConnection)url.openConnection();
			con.setRequestMethod("POST");
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Set required header for dashboard upload
		// see https://www.skedler.com/blog/easy-way-export-import-dashboards-searches-visualizations-kibana/
		con.setRequestProperty("kbn-xsrf", "true");
        //Enable long connection to continue transmission
        con.setRequestProperty("Connection", "keep-alive");
        //Set the request parameter format and boundary dividing line
    		con.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        //Set the format of the received return value
    		con.setRequestProperty("Accept", "application/json");
		con.setDoOutput(true);
    		
    		String auth = "adam:changeme";
    		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    		String authHeaderValue = "Basic " + new String(encodedAuth);
    		con.setRequestProperty("Authorization", authHeaderValue);
/**    		
	    	String apiKeyAuth =
	    	    Base64.getEncoder().encodeToString((apiKey).getBytes(StandardCharsets.UTF_8));
	    	con.setRequestProperty("Authorization", "ApiKey " + apiKeyAuth);
	    	**/
    		
        //Get HTTP write stream
        OutputStream outputStream = null;
        try {
            //Open the connection
            con.connect();

            outputStream = new DataOutputStream(con.getOutputStream());

            //Separator head
            String header = twoHyphens + boundary + nextLine;
            
            //Separator parameter setting
            header += "Content-Disposition: form-data;name=\"file\";" + "filename=\"gcp-audit-dashboard.ndjson\"" + nextLine + nextLine;
            
            //Write to output stream
            outputStream.write(header.getBytes());

	    		// Exported Kibana dashboard in ndjson format 
	    		String exportedDashboard = readDashboard();
	    		byte[] bytes = exportedDashboard.getBytes();
            outputStream.write(bytes, 0, bytes.length);
            
            //After the file is written, press Enter
            outputStream.write(nextLine.getBytes());

            //Write end separator
            String footer = nextLine + twoHyphens + boundary + twoHyphens + nextLine;
            outputStream.write(footer.getBytes());
            outputStream.flush();
            
            // Dashboard upload completed
            /**
            InputStream response = con.getInputStream();
            InputStreamReader reader = new InputStreamReader(response);
            while (reader.read() != -1){
                LOG.info("Dashboard loading response: " + new String(bytes, "UTF-8"));
            }
            **/
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK){
                LOG.info("Successfully loaded dashboard: " + con.getResponseMessage());
            }else {
                LOG.error("Dashboard loading failed");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null){
                    outputStream.close();
                }
                if (con != null){
                    con.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private String readDashboard () {
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();

        // Select all fields. Fields can be selected individually e.g. Storage.BucketField.NAME
        Bucket bucket =
            storage.get(bucketName, Storage.BucketGetOption.fields(Storage.BucketField.values()));
        Blob blob = bucket.get("gcp-audit-dashboard.ndjson");
    		String dashboard = new String(blob.getContent());
		LOG.info("Loading dashboard: " + dashboard.length());
    		
    		return dashboard;
    }
    
    private String getIndex () {
	    	SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");
	
	    	String dateString = format.format( new java.util.Date()   );
	    	return "filebeat-7.11.1-" + dateString; 
    }
}
