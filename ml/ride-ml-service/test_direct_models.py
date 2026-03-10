import pandas as pd
import joblib
from pathlib import Path
import os

print("Current working directory:", os.getcwd())
print("Script directory:", Path(__file__).resolve().parent)

BASE_DIR = Path(__file__).resolve().parent

# Load models
eta_model = joblib.load(BASE_DIR / "eta_model_xgb.pkl")
fare_model = joblib.load(BASE_DIR / "fare_model_xgb.pkl")

try:
    tolls_model = joblib.load(BASE_DIR /"tolls_model_xgb.pkl")
    tolls_enabled = True
except Exception:
    tolls_model = None
    tolls_enabled = False

# Stub input
base_features = pd.DataFrame([{
    "trip_distance": 12.4,
    "PULocationID": "161",
    "DOLocationID": "132",
    "pickup_hour": 18.0,
    "pickup_dow": 0.0,
    "pickup_month": 3.0
}])

# ETA
eta_pred_sec = float(eta_model.predict(base_features)[0])
eta_pred_sec = max(0.0, eta_pred_sec)

# Fare
fare_features = base_features.copy()
fare_features["eta_pred_sec"] = eta_pred_sec

fare_pred = float(fare_model.predict(fare_features)[0])
fare_pred = max(0.0, fare_pred)

# Tolls
if tolls_enabled and tolls_model is not None:
    tolls_pred = float(tolls_model.predict(fare_features)[0])
    tolls_pred = max(0.0, tolls_pred)
else:
    tolls_pred = 0.0

# Final total
congestion_surcharge = 2.75
improvement_surcharge = 0.30

total_no_tip = fare_pred + tolls_pred + congestion_surcharge + improvement_surcharge

print("Stub input:")
print(base_features.to_dict(orient="records")[0])

print("\nPredictions:")
print({
    "eta_pred_sec": round(eta_pred_sec, 2),
    "fare_pred": round(fare_pred, 2),
    "tolls_pred": round(tolls_pred, 2),
    "total_no_tip": round(total_no_tip, 2),
    "model_version": "xgb_v1"
})