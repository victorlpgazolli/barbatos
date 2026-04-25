import requests
import threading
import json

def fetch_stream():
    try:
        r = requests.post("http://127.0.0.1:8080/stream/classes", json={"search_param": "", "app_package": ""}, stream=True)
        print("Status Code:", r.status_code)
        for line in r.iter_lines():
            if line:
                print("Received chunk:", len(json.loads(line)["chunk"]), "classes")
    except Exception as e:
        print("Error:", e)

fetch_stream()
