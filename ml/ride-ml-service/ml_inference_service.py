from fastapi import FastAPI, HTTPException
from fastapi import Request
from pydantic import BaseModel, Field
import pandas as pd
import joblib
from pathlib import Path
import logging
import sys
import traceback
from datetime import datetime
from math import asin, cos, radians, sin, sqrt
import requests
import re

try:
    import geopandas as gpd
    from shapely.geometry import Point
    GEOSPATIAL_IMPORT_ERROR = None
except Exception as exc:
    gpd = None
    Point = None
    GEOSPATIAL_IMPORT_ERROR = exc


class MaxLevelFilter(logging.Filter):
    def __init__(self, level):
        super().__init__()
        self.level = level

    def filter(self, record):
        return record.levelno < self.level


app = FastAPI(title="Ride ML Inference Service", version="1.0")
BASE_DIR = Path(__file__).resolve().parent
ML_OUT_LOG = BASE_DIR / "ml-service.out.log"
ML_ERR_LOG = BASE_DIR / "ml-service.err.log"

logger = logging.getLogger("ml_inference_service")
logger.setLevel(logging.INFO)
logger.handlers.clear()
logger.propagate = False

stdout_handler = logging.StreamHandler(sys.stdout)
stdout_handler.setLevel(logging.INFO)
stdout_handler.addFilter(MaxLevelFilter(logging.ERROR))
stdout_handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))

stderr_handler = logging.StreamHandler(sys.stderr)
stderr_handler.setLevel(logging.ERROR)
stderr_handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))

file_out_handler = logging.FileHandler(ML_OUT_LOG, mode="a", encoding="utf-8")
file_out_handler.setLevel(logging.INFO)
file_out_handler.addFilter(MaxLevelFilter(logging.ERROR))
file_out_handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))

file_err_handler = logging.FileHandler(ML_ERR_LOG, mode="a", encoding="utf-8")
file_err_handler.setLevel(logging.ERROR)
file_err_handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))

logger.addHandler(stdout_handler)
logger.addHandler(stderr_handler)
logger.addHandler(file_out_handler)
logger.addHandler(file_err_handler)

# ---------- Load models ----------
try:
    eta_model = joblib.load(BASE_DIR / "eta_model_xgb.pkl")
    fare_model = joblib.load(BASE_DIR / "fare_model_xgb.pkl")
except Exception as e:
    raise RuntimeError(f"Unable to load ETA/Fare models: {e}")

try:
    tolls_model = joblib.load(BASE_DIR / "tolls_model_xgb.pkl")
    TOLLS_ENABLED = True
except Exception:
    tolls_model = None
    TOLLS_ENABLED = False

MODEL_VERSION = "xgb_v1"
TAXI_ZONES_PATH = BASE_DIR.parent / "taxizones" / "taxi_zones.shp"
NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
NOMINATIM_HEADERS = {
    "Accept": "application/json",
    "User-Agent": "vianova-ml-service/1.0"
}
TAXI_ZONES = None
NYC_METRO_VIEWBOX = "-74.35,40.45,-73.65,40.95"
BOROUGH_TOKENS = ("manhattan", "brooklyn", "queens", "bronx", "staten island")
AIRPORT_TOKENS = ("jfk", "kennedy", "newark", "ewr", "laguardia", "la guardia", "lga", "airport")


if GEOSPATIAL_IMPORT_ERROR is None:
    try:
        TAXI_ZONES = gpd.read_file(TAXI_ZONES_PATH)
        logger.info(
            "Loaded taxi zones from %s with %s rows and columns=%s",
            TAXI_ZONES_PATH,
            len(TAXI_ZONES.index),
            list(TAXI_ZONES.columns),
        )
    except Exception as exc:
        GEOSPATIAL_IMPORT_ERROR = exc
        logger.error("Unable to load taxi zones from %s\n%s", TAXI_ZONES_PATH, traceback.format_exc())


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


class AddressPredictRequest(BaseModel):
    source_address: str = Field(..., min_length=3)
    destination_address: str = Field(..., min_length=3)
    departure_time: datetime


class AddressPredictResponse(PredictResponse):
    source_address: str
    destination_address: str
    source_lat: float
    source_lon: float
    destination_lat: float
    destination_lon: float
    PULocationID: str
    DOLocationID: str
    trip_distance: float


@app.get("/health")
def health():
    logger.info("health check model_version=%s tolls_enabled=%s", MODEL_VERSION, TOLLS_ENABLED)
    return {
        "status": "ok",
        "model_version": MODEL_VERSION,
        "tolls_enabled": TOLLS_ENABLED,
        "geospatial_ready": GEOSPATIAL_IMPORT_ERROR is None and TAXI_ZONES is not None
    }


def predict_internal(flow_id: str, trip_distance: float, pickup_zone_id: str, dropoff_zone_id: str,
                     pickup_hour: int, pickup_dow: int, pickup_month: int) -> PredictResponse:
    base_features = pd.DataFrame([{
        "trip_distance": float(trip_distance),
        "PULocationID": str(pickup_zone_id),
        "DOLocationID": str(dropoff_zone_id),
        "pickup_hour": float(pickup_hour),
        "pickup_dow": float(pickup_dow),
        "pickup_month": float(pickup_month),
    }])

    eta_pred_sec = float(eta_model.predict(base_features)[0])
    eta_pred_sec = max(0.0, eta_pred_sec)

    fare_features = base_features.copy()
    fare_features["eta_pred_sec"] = eta_pred_sec

    fare_pred = float(fare_model.predict(fare_features)[0])
    fare_pred = max(0.0, fare_pred)

    if TOLLS_ENABLED and tolls_model is not None:
        tolls_pred = float(tolls_model.predict(fare_features)[0])
        tolls_pred = max(0.0, tolls_pred)
    else:
        tolls_pred = 0.0

    congestion_surcharge = 2.75
    improvement_surcharge = 0.30

    total_no_tip = fare_pred + tolls_pred + congestion_surcharge + improvement_surcharge
    total_no_tip = max(0.0, total_no_tip)
    logger.info(
        "flow_id=%s prediction output eta_pred_sec=%.2f fare_pred=%.2f tolls_pred=%.2f total_no_tip=%.2f model_version=%s",
        flow_id,
        eta_pred_sec,
        fare_pred,
        tolls_pred,
        total_no_tip,
        MODEL_VERSION,
    )

    return PredictResponse(
        eta_pred_sec=round(eta_pred_sec, 2),
        fare_pred=round(fare_pred, 2),
        tolls_pred=round(tolls_pred, 2),
        total_no_tip=round(total_no_tip, 2),
        model_version=MODEL_VERSION
    )


def ensure_geospatial_ready():
    if GEOSPATIAL_IMPORT_ERROR is not None:
        raise RuntimeError(
            "Geospatial dependencies are not ready. Install geopandas/shapely/pyogrio. Root cause: "
            + str(GEOSPATIAL_IMPORT_ERROR)
        )
    if TAXI_ZONES is None:
        raise RuntimeError(f"Taxi zones shapefile could not be loaded from {TAXI_ZONES_PATH}")
    if "LocationID" not in TAXI_ZONES.columns:
        raise RuntimeError("Taxi zones shapefile is missing the LocationID column")


def geocode_address(address: str) -> tuple[float, float]:
    query = normalize_address_query(address)
    response = requests.get(
        NOMINATIM_URL,
        params={
            "format": "jsonv2",
            "limit": 5,
            "addressdetails": 1,
            "dedupe": 1,
            "countrycodes": "us",
            "viewbox": NYC_METRO_VIEWBOX,
            "q": query,
        },
        headers=NOMINATIM_HEADERS,
        timeout=10,
    )
    response.raise_for_status()
    data = response.json()
    if not data:
        raise RuntimeError(f"No geocoding result for address: {address}")
    ranked = sorted(
        ((score_geocode_candidate(address, candidate), candidate) for candidate in data),
        key=lambda item: item[0],
        reverse=True,
    )
    top_score, top = ranked[0]
    logger.info(
        "geocode query=%r normalized_query=%r selected_score=%s selected_display_name=%r candidate_scores=%s",
        address,
        query,
        top_score,
        top.get("display_name", ""),
        [
            {
                "score": score,
                "display_name": candidate.get("display_name", "")
            }
            for score, candidate in ranked
        ],
    )
    return float(top["lat"]), float(top["lon"])


def normalize_address_query(address: str) -> str:
    query = " ".join((address or "").strip().split())
    lower = query.lower()
    if re.search(r"\bny\b", lower) and "new york" not in lower:
        query = re.sub(r"\bNY\b", "New York, NY", query, flags=re.IGNORECASE)
        lower = query.lower()
    if any(token in lower for token in ("jfk", "kennedy")) and "airport" not in lower:
        query = query + ", airport"
        lower = query.lower()
    if "newark airport" in lower and "new jersey" not in lower:
        query = query + ", Newark, New Jersey"
    elif not any(token in lower for token in ("new york", "new jersey", "queens", "brooklyn", "bronx", "manhattan", "staten island", "newark")):
        query = query + ", New York, USA"
    return query


def score_geocode_candidate(original_query: str, candidate: dict) -> int:
    query = (original_query or "").strip().lower()
    display_name = (candidate.get("display_name") or "").lower()
    address = candidate.get("address") or {}
    address_text = " ".join(str(value).lower() for value in address.values())
    score = 0

    zip_match = re.search(r"\b(\d{5})\b", query)
    candidate_postcode = str(address.get("postcode", "")).strip().lower()
    if zip_match:
        expected_zip = zip_match.group(1)
        if candidate_postcode.startswith(expected_zip):
            score += 120
        else:
            score -= 80

    house_match = re.search(r"\b(\d+)\b", query)
    candidate_house = str(address.get("house_number", "")).strip().lower()
    if house_match:
        if candidate_house == house_match.group(1):
            score += 60
        elif candidate_house:
            score -= 20

    road = str(address.get("road", "")).strip().lower()
    road_hint = extract_road_hint(query)
    if road_hint:
        if road_hint == road:
            score += 70
        elif road_hint in road or road in road_hint:
            score += 35
        else:
            score -= 25

    if "new york" in query or re.search(r"\bny\b", query):
        if "new york" in address_text or "new york" in display_name:
            score += 35
        else:
            score -= 30

    if "newark" in query:
        if "newark" in address_text or "newark" in display_name:
            score += 40
        else:
            score -= 35

    for token in BOROUGH_TOKENS:
        if token in query:
            if token in address_text or token in display_name:
                score += 30
            else:
                score -= 20

    for token in AIRPORT_TOKENS:
        if token in query:
            if token in address_text or token in display_name:
                score += 20

    if candidate.get("type") in {"aerodrome", "airport"} and "airport" in query:
        score += 25

    importance = candidate.get("importance")
    if isinstance(importance, (float, int)):
        score += int(float(importance) * 10)

    return score


def extract_road_hint(query: str) -> str:
    without_zip = re.sub(r"\b\d{5}\b", "", query)
    without_city = re.sub(r"\b(new york|new jersey|ny|nj|united states|usa)\b", "", without_zip)
    parts = [part.strip() for part in without_city.split(",") if part.strip()]
    if not parts:
        return ""
    first = parts[0]
    first = re.sub(r"^\d+\s+", "", first).strip()
    return first


def location_id_for_coordinates(lat: float, lon: float) -> str:
    point = gpd.GeoDataFrame(
        {"lat": [lat], "lon": [lon]},
        geometry=[Point(lon, lat)],
        crs="EPSG:4326",
    ).to_crs(TAXI_ZONES.crs)
    zones = TAXI_ZONES[["LocationID", "geometry"]]
    joined = gpd.sjoin(point, zones, how="left", predicate="within")
    if joined["LocationID"].notna().any():
        return str(int(joined.iloc[0]["LocationID"]))

    # Handle points near polygon boundaries by selecting the nearest zone.
    point_geometry = point.iloc[0].geometry
    distances = zones.geometry.distance(point_geometry)
    nearest_index = distances.idxmin()
    nearest_location_id = zones.loc[nearest_index, "LocationID"]
    return str(int(nearest_location_id))


def haversine_miles(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius_miles = 3958.7613
    dlat = radians(lat2 - lat1)
    dlon = radians(lon2 - lon1)
    a = sin(dlat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(dlon / 2) ** 2
    c = 2 * asin(sqrt(a))
    return radius_miles * c


@app.post("/ml/v1/predict", response_model=PredictResponse)
def predict(req: PredictRequest, request: Request):
    flow_id = request.headers.get("X-Flow-Id", "no-flow-id")
    logger.info(
        "flow_id=%s received prediction request trip_distance=%.2f source_zone=%s destination_zone=%s pickup_hour=%s pickup_dow=%s pickup_month=%s",
        flow_id,
        req.trip_distance,
        req.PULocationID,
        req.DOLocationID,
        req.pickup_hour,
        req.pickup_dow,
        req.pickup_month,
    )
    try:
        return predict_internal(
            flow_id=flow_id,
            trip_distance=req.trip_distance,
            pickup_zone_id=req.PULocationID,
            dropoff_zone_id=req.DOLocationID,
            pickup_hour=req.pickup_hour,
            pickup_dow=req.pickup_dow,
            pickup_month=req.pickup_month,
        )
    except Exception as e:
        logger.error(
            "flow_id=%s prediction failed: %s\n%s",
            flow_id,
            e,
            traceback.format_exc(),
        )
        raise HTTPException(status_code=500, detail=f"Prediction failed: {str(e)}")


@app.post("/ml/v1/predict-addresses", response_model=AddressPredictResponse)
def predict_from_addresses(req: AddressPredictRequest, request: Request):
    flow_id = request.headers.get("X-Flow-Id", "no-flow-id")
    logger.info(
        "flow_id=%s received address prediction request source_address=%r destination_address=%r departure_time=%s",
        flow_id,
        req.source_address,
        req.destination_address,
        req.departure_time.isoformat(),
    )
    try:
        ensure_geospatial_ready()
        source_lat, source_lon = geocode_address(req.source_address)
        destination_lat, destination_lon = geocode_address(req.destination_address)
        pickup_zone_id = location_id_for_coordinates(source_lat, source_lon)
        dropoff_zone_id = location_id_for_coordinates(destination_lat, destination_lon)
        trip_distance = round(
            max(0.1, haversine_miles(source_lat, source_lon, destination_lat, destination_lon)),
            2,
        )
        logger.info(
            "flow_id=%s resolved source_lat=%.6f source_lon=%.6f destination_lat=%.6f destination_lon=%.6f source_zone=%s destination_zone=%s trip_distance=%.2f",
            flow_id,
            source_lat,
            source_lon,
            destination_lat,
            destination_lon,
            pickup_zone_id,
            dropoff_zone_id,
            trip_distance,
        )
        prediction = predict_internal(
            flow_id=flow_id,
            trip_distance=trip_distance,
            pickup_zone_id=pickup_zone_id,
            dropoff_zone_id=dropoff_zone_id,
            pickup_hour=req.departure_time.hour,
            pickup_dow=req.departure_time.weekday(),
            pickup_month=req.departure_time.month,
        )
        return AddressPredictResponse(
            source_address=req.source_address,
            destination_address=req.destination_address,
            source_lat=round(source_lat, 6),
            source_lon=round(source_lon, 6),
            destination_lat=round(destination_lat, 6),
            destination_lon=round(destination_lon, 6),
            PULocationID=pickup_zone_id,
            DOLocationID=dropoff_zone_id,
            trip_distance=trip_distance,
            eta_pred_sec=prediction.eta_pred_sec,
            fare_pred=prediction.fare_pred,
            tolls_pred=prediction.tolls_pred,
            total_no_tip=prediction.total_no_tip,
            model_version=prediction.model_version,
        )
    except Exception as e:
        logger.error(
            "flow_id=%s address prediction failed: %s\n%s",
            flow_id,
            e,
            traceback.format_exc(),
        )
        raise HTTPException(status_code=500, detail=f"Address prediction failed: {str(e)}")
