ARG ELK_VERSION

# https://github.com/elastic/logstash-docker
FROM docker.elastic.co/logstash/logstash:${ELK_VERSION}

ADD plugin plugins

# Add your logstash plugins setup here
# Example: RUN logstash-plugin install logstash-filter-json
#RUN logstash-plugin install plugins/logstash-input-java_input_example-1.0.0.gem
#RUN logstash-plugin install plugins/plc4j-logstash-plugin-0.5.0-SNAPSHOT.gem