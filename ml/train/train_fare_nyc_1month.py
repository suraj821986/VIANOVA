import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
import joblib

from sklearn.model_selection import train_test_split
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import OneHotEncoder
from sklearn.pipeline import Pipeline
from sklearn.metrics import mean_absolute_error, mean_squared_error
from sklearn.ensemble import HistGradientBoostingRegressor


def load_and_clean(parquet_path: Path) -> pd.DataFrame:
    df = pd.read_parquet(parquet_path)

    # Normalize column names (TLC sometimes changes casing/spacing)
    df.columns = [c.strip() for c in df.columns]

    required = [
        "tpep_pickup_datetime",
        "tpep_dropoff_datetime",
        "PULocationID",
        "DOLocationID",
        "trip_distance",
        "total_amount",
    ]
    missing = [c for c in required if c not in df.columns]
    if missing:
        raise ValueError(
            f"Missing columns: {missing}\n"
            f"Found columns: {list(df.columns)}"
        )

    # Parse datetimes safely
    df["tpep_pickup_datetime"] = pd.to_datetime(df["tpep_pickup_datetime"], errors="coerce")
    df["tpep_dropoff_datetime"] = pd.to_datetime(df["tpep_dropoff_datetime"], errors="coerce")

    # Duration in seconds
    df["trip_duration_sec"] = (
            df["tpep_dropoff_datetime"] - df["tpep_pickup_datetime"]
    ).dt.total_seconds()

    # Time features
    df["pickup_hour"] = df["tpep_pickup_datetime"].dt.hour
    df["pickup_dow"] = df["tpep_pickup_datetime"].dt.dayofweek  # 0=Mon
    df["pickup_month"] = df["tpep_pickup_datetime"].dt.month

    # Drop nulls in key fields
    df = df.dropna(subset=[
        "trip_duration_sec", "trip_distance", "total_amount",
        "PULocationID", "DOLocationID", "pickup_hour", "pickup_dow", "pickup_month"
    ]).copy()

    # Basic "student-ish" filtering (avoid junk/outliers)
    df = df[
        (df["trip_duration_sec"] >= 60) &
        (df["trip_duration_sec"] <= 3 * 60 * 60) &
        (df["trip_distance"] >= 0.1) &
        (df["trip_distance"] <= 60) &
        (df["total_amount"] >= 3) &
        (df["total_amount"] <= 300)
        ].copy()

    # Convert zone IDs to string so OneHotEncoder treats them as categories
    df["PULocationID"] = df["PULocationID"].astype(int).astype(str)
    df["DOLocationID"] = df["DOLocationID"].astype(int).astype(str)

    # Ensure numerics are proper
    df["trip_distance"] = df["trip_distance"].astype(float)
    df["trip_duration_sec"] = df["trip_duration_sec"].astype(float)
    df["total_amount"] = df["total_amount"].astype(float)

    return df


def train_model(df: pd.DataFrame, random_state: int = 42) -> tuple[Pipeline, dict]:
    features = [
        "trip_distance",
        "trip_duration_sec",
        "pickup_hour",
        "pickup_dow",
        "pickup_month",
        "PULocationID",
        "DOLocationID",
    ]
    target = "total_amount"

    X = df[features]
    y = df[target]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=random_state
    )

    numeric_features = [
        "trip_distance", "trip_duration_sec", "pickup_hour", "pickup_dow", "pickup_month"
    ]
    categorical_features = ["PULocationID", "DOLocationID"]

    pre = ColumnTransformer(
        transformers=[
            ("num", "passthrough", numeric_features),
            ("cat", OneHotEncoder(handle_unknown="ignore", sparse_output=False), categorical_features),
        ]
    )

    model = HistGradientBoostingRegressor(
        max_depth=7,
        learning_rate=0.08,
        max_iter=250,
        random_state=random_state,
    )

    pipe = Pipeline(steps=[("pre", pre), ("model", model)])
    pipe.fit(X_train, y_train)

    preds = pipe.predict(X_test)

    mae = mean_absolute_error(y_test, preds)
    rmse = np.sqrt(mean_squared_error(y_test, preds))

    metrics = {
        "rows_total": int(len(df)),
        "rows_train": int(len(X_train)),
        "rows_test": int(len(X_test)),
        "mae_usd": float(mae),
        "rmse_usd": float(rmse),
        "features": features,
        "target": target,
        "model": "HistGradientBoostingRegressor",
    }

    return pipe, metrics


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Path to NYC TLC yellow parquet file (one month)")
    parser.add_argument("--output", default="fare_model.pkl", help="Output path for saved model .pkl")
    parser.add_argument("--metrics", default="fare_model_metrics.json", help="Output path for metrics JSON")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        raise FileNotFoundError(f"Input file not found: {input_path}")

    df = load_and_clean(input_path)
    model, metrics = train_model(df)

    joblib.dump(model, args.output)
    with open(args.metrics, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)

    print("✅ Training complete")
    print(f"Saved model:   {Path(args.output).resolve()}")
    print(f"Saved metrics: {Path(args.metrics).resolve()}")
    print(f"Rows used: {metrics['rows_total']:,}")
    print(f"MAE ($):  {metrics['mae_usd']:.2f}")
    print(f"RMSE ($): {metrics['rmse_usd']:.2f}")


if __name__ == "__main__":
    main()
