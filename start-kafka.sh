#!/bin/bash
if [ ! -f /tmp/kraft-combined-logs/meta.properties ]; then
    UUID=$(/home/alca/kafka_2.13-3.7.0/bin/kafka-storage.sh random-uuid)
    /home/alca/kafka_2.13-3.7.0/bin/kafka-storage.sh format -t "$UUID" -c /home/alca/kafka_2.13-3.7.0/config/kraft/server.properties
fi
exec /home/alca/kafka_2.13-3.7.0/bin/kafka-server-start.sh /home/alca/kafka_2.13-3.7.0/config/kraft/server.properties
