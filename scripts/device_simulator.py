#!/usr/bin/env python3
"""Tiny MCU simulator. Requires: pip install paho-mqtt"""
import argparse
import json
import random
import time

import paho.mqtt.client as mqtt

parser = argparse.ArgumentParser()
parser.add_argument("--device-id", required=True)
parser.add_argument("--host", default="localhost")
parser.add_argument("--port", type=int, default=1883)
parser.add_argument("--interval", type=float, default=5.0)
parser.add_argument("--fridge-temp", type=float, default=4.0, help="set > 10 to trigger the over-temperature alert")
args = parser.parse_args()

topic_root = "appliance/%s" % args.device_id

try:
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="sim-" + args.device_id[:8])
except AttributeError:
    client = mqtt.Client(client_id="sim-" + args.device_id[:8])


def on_connect(c, userdata, flags, rc, props=None):
    print("connected:", rc)
    c.subscribe(topic_root + "/commands", qos=1)
    c.subscribe(topic_root + "/ota/command", qos=1)


def on_message(c, userdata, msg):
    payload = json.loads(msg.payload.decode())
    print("<-", msg.topic, payload)
    if msg.topic.endswith("/ota/command"):
        job_id = payload.get("jobId")
        c.publish(topic_root + "/ota/status", json.dumps({"jobId": job_id, "status": "DOWNLOADING"}), qos=1)
        time.sleep(2)
        c.publish(topic_root + "/ota/status",
                  json.dumps({"jobId": job_id, "version": payload.get("version"), "status": "COMPLETED"}), qos=1)
    elif payload.get("command") == "FORCE_DEFROST":
        c.publish(topic_root + "/state", json.dumps({"state": "DEFROST_CYCLE"}), qos=1)


client.on_connect = on_connect
client.on_message = on_message
client.connect(args.host, args.port, 30)
client.loop_start()

try:
    while True:
        reading = {
            "freezerTemp": round(random.uniform(-19.5, -17.5), 1),
            "fridgeTemp": round(args.fridge_temp + random.uniform(-0.4, 0.4), 1),
            "compressorOn": random.random() > 0.4,
            "doorOpen": False,
        }
        client.publish(topic_root + "/telemetry", json.dumps(reading), qos=1)
        print("->", reading)
        time.sleep(args.interval)
except KeyboardInterrupt:
    client.loop_stop()
