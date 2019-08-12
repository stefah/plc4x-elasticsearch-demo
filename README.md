# plc4x-elasticsearch-demo
WIP: Code for demo showcase Elasticsearch with PLC4x


# Running Local Elasticsearch

    docker-compose -f infrastructure/infrastructure.yml up
    
Initial creation of passwords:
    
    docker-compose -f infrastructure.yml exec -T elasticsearch bin/elasticsearch-setup-passwords auto --batch
    
# Running the Example

    Todo  