from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import pandas as pd
import joblib

app = FastAPI(title="Ride ML Inference Service", version="1.0")

# ---------- Load models ----------
try:
    eta_model = joblib.load("eta_model_xgb.pkl")
    fare_model = joblib.load("fare_model_xgb.pkl")
except Exception as e:
    raise RuntimeError(f"Unable to load ETA/Fare models: {e}")

try:
    tolls_model = joblib.load("tolls_model_xgb.pkl")
    TOLLS_ENABLED = True
except Exception:
    tolls_model = None
    TOLLS_ENABLED = False

MODEL_VERSION = "xgb_v1"


# ---------- Contracts ----------
class PredictRequest(BaseModel):
    trip_distance: float = Field(..., ge=0.0)
    PULocationID: str
    DOLocationID: str
    pickup_hour: int = Field(..., ge=0, le=23)
    pickup_dow: int = Field(..., ge=0, le=6)   # 0=Monday ... 6=Sunday
    pickup_month: int = Field(..., ge=1, le=12)


class PredictResponse(BaseModel):
    eta_pred_sec: float
    fare_pred: float
    tolls_pred: float
    total_no_tip: float
    model_version: str


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model_version": MODEL_VERSION,
        "tolls_enabled": TOLLS_ENABLED
    }


@app.post("/ml/v1/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    try:
        base_features = pd.DataFrame([{
            "trip_distance": float(req.trip_distance),
            "PULocationID": str(req.PULocationID),
            "DOLocationID": str(req.DOLocationID),
            "pickup_hour": float(req.pickup_hour),
            "pickup_dow": float(req.pickup_dow),
            "pickup_month": float(req.pickup_month),
        }])

        # Stage 1: ETA
        eta_pred_sec = float(eta_model.predict(base_features)[0])
        eta_pred_sec = max(0.0, eta_pred_sec)

        # Stage 2: Fare
        fare_features = base_features.copy()
        fare_features["eta_pred_sec"] = eta_pred_sec

        fare_pred = float(fare_model.predict(fare_features)[0])
        fare_pred = max(0.0, fare_pred)

        # Stage 3: Tolls
        if TOLLS_ENABLED and tolls_model is not None:
            tolls_pred = float(tolls_model.predict(fare_features)[0])
            tolls_pred = max(0.0, tolls_pred)
        else:
            tolls_pred = 0.0

        # Business-rule additions
        congestion_surcharge = 2.75
        improvement_surcharge = 0.30

        total_no_tip = fare_pred + tolls_pred + congestion_surcharge + improvement_surcharge
        total_no_tip = max(0.0, total_no_tip)

        return PredictResponse(
            eta_pred_sec=round(eta_pred_sec, 2),
            fare_pred=round(fare_pred, 2),
            tolls_pred=round(tolls_pred, 2),
            total_no_tip=round(total_no_tip, 2),
            model_version=MODEL_VERSION
        )

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Prediction failed: {str(e)}")