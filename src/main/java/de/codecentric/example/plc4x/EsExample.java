/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package de.codecentric.example.plc4x;

import org.apache.edgent.function.Supplier;
import org.apache.edgent.providers.direct.DirectProvider;
import org.apache.edgent.topology.TStream;
import org.apache.edgent.topology.Topology;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.plc4x.edgent.PlcConnectionAdapter;
import org.apache.plc4x.edgent.PlcFunctions;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class EsExample {
    private static final Logger logger = LoggerFactory.getLogger(EsExample.class);

    public static void main(String[] args) {
        EsExample esExample = new EsExample();
        esExample.runExample();
    }

    public void runExample() {

        RestHighLevelClient client = initElasticClient();


        String url = "opcua:tcp://0.0.0.0:4840/freeopcua/server/";
        // Establish a connection to the plc using the url provided as first argument
        try (PlcConnectionAdapter plcAdapter = new PlcConnectionAdapter(url)) {

            // Initialize the Edgent core.
            DirectProvider dp = new DirectProvider();
            Topology top = dp.newTopology();
            /*
            This is buggy:

            PlcReadRequest.Builder builder = plcAdapter.readRequestBuilder();
            builder.addItem("PreStage", "ns=2;i=2");
            builder.addItem("MidStage", "ns=2;i=3");
            builder.addItem("PostStage", "ns=2;i=4");
            builder.addItem("TimeStamp", "ns=2;i=5");
            PlcReadRequest readRequest = builder.build();
            Supplier<PlcReadResponse> plcReadResponseSupplier = PlcFunctions.batchSupplier(plcAdapter, readRequest);
            TStream<PlcReadResponse> poll = top.poll(plcReadResponseSupplier, 100, TimeUnit.MILLISECONDS);
            */
            // Define the event stream.
            // 1) PLC4X source generating a stream of bytes.
            Supplier<Double> plcSupplierPreStage = PlcFunctions.doubleSupplier(plcAdapter, "ns=2;i=3");
            Supplier<Double> plcSupplierMidStage = PlcFunctions.doubleSupplier(plcAdapter, "ns=2;i=4");
            Supplier<Double> plcSupplierPostStage = PlcFunctions.doubleSupplier(plcAdapter, "ns=2;i=5");
            Supplier<Integer> plcSupplierMotor = PlcFunctions.integerSupplier(plcAdapter, "ns=2;i=6");
            //Supplier<LocalDateTime> plcSupplierTs = PlcFunctions.dateTimeSupplier(plcAdapter, "ns=2;i=5");

            // 2) Use polling to get an item from the byte-stream in regular intervals.
            TStream<Double> plcOutputStatesPreStage = top.poll(plcSupplierPreStage, 100, TimeUnit.MILLISECONDS).alias("PreStage");
            TStream<Double> plcOutputStatesMidStage = top.poll(plcSupplierMidStage, 100, TimeUnit.MILLISECONDS).alias("MidStage");
            TStream<Double> plcOutputStatesPostStage = top.poll(plcSupplierPostStage, 100, TimeUnit.MILLISECONDS).alias("PostStage");
            TStream<Integer> plcOutputStatesMotor = top.poll(plcSupplierMotor, 100, TimeUnit.MILLISECONDS).alias("Motor");

            // TStream<LocalDateTime> plcOutputStatesTs = top.poll(plcSupplierTs, 100, TimeUnit.MILLISECONDS).alias("TimeStamp");

            TStream<XContentBuilder> indexDataPreStage = plcOutputStatesPreStage.map(s -> translatePlcInput(s, "PreStage"));
            TStream<XContentBuilder> indexDataMidStage = plcOutputStatesMidStage.map(s -> translatePlcInput(s, "MidStage"));
            TStream<XContentBuilder> indexDataPostStage = plcOutputStatesPostStage.map(s -> translatePlcInput(s, "PostStage"));
            TStream<XContentBuilder> indexDataMotor = plcOutputStatesMotor.map(s -> translatePlcInput(s, "Motor"));

            TStream<IndexResponse> indexResponsesPre = indexDataPreStage.map(content -> indexSensorData(client, content));
            TStream<IndexResponse> indexResponsesMid = indexDataMidStage.map(content -> indexSensorData(client, content));
            TStream<IndexResponse> indexResponsesPost = indexDataPostStage.map(content -> indexSensorData(client, content));
            TStream<IndexResponse> indexResponsesMotor = indexDataMotor.map(content -> indexSensorData(client, content));
            indexResponsesPre.print();
            // Submit the topology and hereby start the event streams.
            dp.submit(top);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private IndexResponse indexSensorData(RestHighLevelClient client, XContentBuilder content) {
        try {
            IndexRequest request = new IndexRequest("plant1").source(content);
            return client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //TODO:
        return null;
    }

    private RestHighLevelClient initElasticClient() {
        final CredentialsProvider credentialsProvider =
                new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("elastic", "ep9qfvrbgvhRQ4BLajHp"));
        RestClientBuilder builder = RestClient.builder(
                // Todos: credentials, ips etc via Env
                new HttpHost("localhost", 9200))
                .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                    @Override
                    public HttpAsyncClientBuilder customizeHttpClient(
                            HttpAsyncClientBuilder httpClientBuilder) {
                        return httpClientBuilder
                                .setDefaultCredentialsProvider(credentialsProvider);
                    }
                });
        return new RestHighLevelClient(builder);
    }

    private XContentBuilder translatePlcInput(PlcReadResponse readResponse) {
        LocalDateTime timeStamp = readResponse.getDateTime("TimeStamp");
        Double preStage = readResponse.getDouble("PreStage");
        Double midStage = readResponse.getDouble("MidStage");
        Double postStage = readResponse.getDouble("PostStage");
        try (XContentBuilder builder = XContentFactory.jsonBuilder()
                .startObject()
                .field("temp1", preStage)
                .field("temp2", midStage)
                .field("temp3", postStage)
                .field("@timestamp", timeStamp)
                .endObject()) {
            return builder;
        } catch (IOException e) {
            logger.error("Error building JSON message.", e);
            return null;
        }
    }

    private XContentBuilder translatePlcInput(Double input, String sensorName) {
        String timestamp = Instant.now().toString();
        try (XContentBuilder builder = XContentFactory.jsonBuilder()
                .startObject()
                .field("temperature", input)
                .field("sensor", sensorName)
                .field("@timestamp", timestamp)
                .endObject()) {
            return builder;
        } catch (IOException e) {
            logger.error("Error building JSON message.", e);
            return null;
        }
    }

    private XContentBuilder translatePlcInput(Integer input, String sensorName) {
        String timestamp = Instant.now().toString();
        try (XContentBuilder builder = XContentFactory.jsonBuilder()
                .startObject()
                .field("degree", input)
                .field("sensor", sensorName)
                .field("@timestamp", timestamp)
                .endObject()) {
            return builder;
        } catch (IOException e) {
            logger.error("Error building JSON message.", e);
            return null;
        }
    }

}