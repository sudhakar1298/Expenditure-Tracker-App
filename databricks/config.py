import os
import json
from urllib.request import Request, urlopen
from urllib.parse import urlsplit, urlunsplit, parse_qsl, urlencode

class PipelineConfig:
    # Local storage paths for development/testing or DBFS paths on Databricks cluster
    BASE_DATA_DIR = os.getenv("DATABRICKS_DATA_DIR", "./delta_lake_data")

    BRONZE_TABLE_PATH = os.path.join(BASE_DATA_DIR, "bronze", "expenses")
    SILVER_TABLE_PATH = os.path.join(BASE_DATA_DIR, "silver", "expenses")
    GOLD_DAILY_PATH = os.path.join(BASE_DATA_DIR, "gold", "daily_expenses")
    GOLD_WEEKLY_PATH = os.path.join(BASE_DATA_DIR, "gold", "weekly_expenses")
    GOLD_CATEGORY_PATH = os.path.join(BASE_DATA_DIR, "gold", "category_summary")
    GOLD_MERCHANT_PATH = os.path.join(BASE_DATA_DIR, "gold", "merchant_summary")

    # FastAPI / PostgreSQL source API URL
    API_SOURCE_URL = os.getenv("API_SOURCE_URL", "http://localhost:8000/api/v1/expenses/")

    # Merchant normalization mapping dict
    MERCHANT_NORM_MAP = {
        "swiggy india": "SWIGGY",
        "swiggy-rest": "SWIGGY",
        "upi-swiggy": "SWIGGY",
        "zomato limited": "ZOMATO",
        "zomato-pay": "ZOMATO",
        "amazon pay": "AMAZON",
        "amazon seller": "AMAZON",
        "flipkart pvt": "FLIPKART",
        "uber india": "UBER",
        "ola cabs": "OLA",
        "bescom online": "BESCOM",
    }

config = PipelineConfig()

def fetch_expenses_from_api(limit: int = 10000) -> list:
    """Read the same API that receives Android/Web writes.

    This keeps the Databricks job on the real application data path instead
    of silently reading a different local SQLite file.
    """
    if limit <= 0:
        raise ValueError("Page size must be positive")
    parts = urlsplit(config.API_SOURCE_URL)
    params = dict(parse_qsl(parts.query))
    records = {}
    skip = 0
    while True:
        params.update(skip=skip, limit=limit)
        url = urlunsplit(parts._replace(query=urlencode(params)))
        with urlopen(Request(url, headers={"Accept": "application/json"}), timeout=15) as response:
            payload = json.loads(response.read().decode("utf-8"))
        if not isinstance(payload, list):
            raise ValueError("Expense API returned a non-list payload")
        if not payload:
            return list(records.values())
        previous_count = len(records)
        for row in payload:
            records[row["id"]] = row
        if len(records) == previous_count:
            raise ValueError("Expense API is not advancing pagination")
        skip += len(payload)

def save_spark_df_safely(df, path: str, spark_session):
    """
    Saves PySpark DataFrame to local path safely across all OS platforms (Linux, macOS, Windows).
    """
    dir_path = os.path.dirname(os.path.abspath(path))
    os.makedirs(dir_path, exist_ok=True)
    try:
        df.write.format("parquet").mode("overwrite").save(path)
    except Exception:
        # Cross-platform fallback saving as JSON
        json_file = f"{path}.json" if not path.endswith(".json") else path
        rows = [json.loads(row) for row in df.toJSON().collect()]
        with open(json_file, "w", encoding="utf-8") as f:
            json.dump(rows, f, indent=2)

def load_spark_df_safely(spark_session, path: str):
    """
    Reads PySpark DataFrame safely from local path.
    """
    json_file = f"{path}.json" if not path.endswith(".json") else path
    if os.path.isfile(json_file):
        return spark_session.read.option("multiLine", True).json(json_file)

    return spark_session.read.format("parquet").load(path)
