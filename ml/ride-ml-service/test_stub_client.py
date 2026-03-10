import requests
import json

URL = "http://127.0.0.1:8001/ml/v1/predict"

payload = {
    "trip_distance": 12.4,
    "PULocationID": "161",
    "DOLocationID": "132",
    "pickup_hour": 18,
    "pickup_dow": 0,     # 0 = Monday
    "pickup_month": 3
}

try:
    response = requests.post(URL, json=payload, timeout=30)

    print("Status code:", response.status_code)
    print("Response JSON:")
    print(json.dumps(response.json(), indent=2))

except Exception as e:
    print("Request failed:", str(e))