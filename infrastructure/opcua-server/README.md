# OPC-UA Demo Server for PLC4x

WIP: This project provides an OPC-UA Demo Server for the PLC4x ElasticSearch Demo.

The demo server emits temperature sensor data with periodically induced peaks.

### Requirements

- Python 3.7
- asyncua

### Install

    pip3 install -r requirements.txt

### Running
    
    python3 src/demo_server.py <optional: hostname>

or with Docker:

    docker build -t demo-server:latest .

### License

Apache 2.0
 