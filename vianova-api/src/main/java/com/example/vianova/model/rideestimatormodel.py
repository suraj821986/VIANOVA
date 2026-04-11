import csv
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from sklearn.linear_model import LinearRegression


@dataclass
class RideEstimate:
    source: str
    destination: str
    departure_time: str
    estimated_fare_usd: float
    estimated_distance_km: float
    estimated_total_time_min: int

    def to_dict(self) -> dict:
        return {
            "source": self.source,
            "destination": self.destination,
            "departureTime": self.departure_time,
            "estimatedFareUsd": self.estimated_fare_usd,
            "estimatedDistanceKm": self.estimated_distance_km,
            "estimatedTotalTimeMin": self.estimated_total_time_min,
        }


class RideEstimatorModel:
    """
    Simple ML-style ride estimator for a college project.

    It uses:
    - a CSV dataset
    - feature extraction from source, destination, and time
    - scikit-learn LinearRegression models

    Inputs:
    - source
    - destination
    - departure_time

    Outputs:
    - estimated fare
    - estimated total time
    - estimated distance
    """

    def __init__(self, dataset_path: str | None = None):
        self.dataset_path = Path(dataset_path) if dataset_path else Path(__file__).with_name(
            "ride_estimator_training_data.csv"
        )
        self.time_model = LinearRegression()
        self.fare_model = LinearRegression()
        self._train_models()

    def estimate_ride(self, source: str, destination: str, departure_time: str) -> RideEstimate:
        source = source.strip()
        destination = destination.strip()

        if not source or not destination:
            raise ValueError("Source and destination are required.")

        distance_km = self._estimate_distance(source, destination)
        peak_flag, night_flag = self._extract_time_flags(departure_time)
        features = [[distance_km, peak_flag, night_flag]]

        predicted_time = self.time_model.predict(features)[0]
        predicted_fare = self.fare_model.predict(features)[0]

        total_time_min = max(5, round(predicted_time))
        fare_usd = max(4.0, round(predicted_fare, 2))

        return RideEstimate(
            source=source,
            destination=destination,
            departure_time=departure_time,
            estimated_fare_usd=fare_usd,
            estimated_distance_km=round(distance_km, 2),
            estimated_total_time_min=total_time_min,
        )

    def _train_models(self) -> None:
        if not self.dataset_path.exists():
            raise FileNotFoundError(f"Training dataset not found: {self.dataset_path}")

        feature_rows = []
        time_targets = []
        fare_targets = []

        with self.dataset_path.open("r", newline="", encoding="utf-8") as csv_file:
            reader = csv.DictReader(csv_file)
            for row in reader:
                distance_km = float(row["distance_km"])
                peak_flag = float(row["peak_flag"])
                night_flag = float(row["night_flag"])
                feature_rows.append([distance_km, peak_flag, night_flag])
                time_targets.append(float(row["estimated_time_min"]))
                fare_targets.append(float(row["estimated_fare_usd"]))

        self.time_model.fit(feature_rows, time_targets)
        self.fare_model.fit(feature_rows, fare_targets)

    def _estimate_distance(self, source: str, destination: str) -> float:
        route_text = f"{source.lower()}|{destination.lower()}"
        route_score = sum(ord(ch) for ch in route_text if ch.isalnum())
        return 2.0 + (route_score % 1800) / 100

    def _extract_time_flags(self, departure_time: str) -> tuple[float, float]:
        hour = self._extract_hour(departure_time)
        peak_flag = 1.0 if 7 <= hour <= 9 or 17 <= hour <= 19 else 0.0
        night_flag = 1.0 if hour >= 22 or hour < 6 else 0.0
        return peak_flag, night_flag

    def _extract_hour(self, departure_time: str) -> int:
        try:
            return datetime.fromisoformat(departure_time).hour
        except ValueError:
            pass

        try:
            return datetime.strptime(departure_time, "%H:%M").hour
        except ValueError as exc:
            raise ValueError(
                "departure_time must be ISO format (YYYY-MM-DDTHH:MM) or HH:MM."
            ) from exc


if __name__ == "__main__":
    model = RideEstimatorModel()
    sample = model.estimate_ride(
        source="UTD Campus",
        destination="Downtown Dallas",
        departure_time="2026-02-28T18:30",
    )
    print(sample.to_dict())
