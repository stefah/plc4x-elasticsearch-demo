FROM python:3.7
MAINTAINER Stefan Herrmann "stefan.herrmann@codecentric.de"
COPY requirements.txt /
RUN pip install -r /requirements.txt
COPY src/ /app
WORKDIR /app
ENTRYPOINT [ "python" ]

CMD [ "demo_server.py" ]