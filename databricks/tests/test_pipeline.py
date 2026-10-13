import sys
import os

# Set PYTHONPATH environment variable to include project root for PySpark workers
root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
if root_dir not in sys.path:
    sys.path.insert(0, root_dir)

existing_pythonpath = os.environ.get("PYTHONPATH", "")
os.environ["PYTHONPATH"] = f"{root_dir}{os.pathsep}{existing_pythonpath}"

from pyspark.sql import SparkSession
from databricks.config import load_spark_df_safely
from databricks.bronze.ingest_expenses import run_bronze_ingestion
from databricks.silver.clean_expenses import run_silver_cleaning
from databricks.gold.aggregate_metrics import run_gold_aggregations
from databricks.jobs.weekly_email_report import generate_weekly_email_report

def test_medallion_pipeline():
    spark = SparkSession.builder \
        .appName("MedallionPipelineTest") \
        .master("local[1]") \
        .getOrCreate()

    sample_raw_data = [
        {
            "id": "tx-1",
            "amount": 450.0,
            "merchant": "SWIGGY INDIA",
            "category": "Food",
            "source": "GPay",
            "transaction_date": "2026-10-01T12:00:00Z"
        },
        {
            "id": "tx-2",
            "amount": 1250.0,
            "merchant": "amazon pay",
            "category": "Shopping",
            "source": "SMS",
            "transaction_date": "2026-10-02T15:30:00Z"
        },
        {
            "id": "tx-3",
            "amount": 150.0,
            "merchant": "Swiggy-Rest",
            "category": "Food",
            "source": "GPay",
            "transaction_date": "2026-10-03T18:45:00Z"
        }
    ]

    print("\n--- Testing Bronze Ingestion ---")
    bronze_path = run_bronze_ingestion(spark, sample_raw_data)
    assert os.path.exists(bronze_path) or os.path.exists(f"{bronze_path}.json")

    print("\n--- Testing Silver Cleaning & Normalization ---")
    silver_path = run_silver_cleaning(spark)
    assert os.path.exists(silver_path) or os.path.exists(f"{silver_path}.json")

    silver_df = load_spark_df_safely(spark, silver_path)
    merchants = [row.merchant for row in silver_df.select("merchant").collect()]
    assert "SWIGGY" in merchants
    assert "AMAZON" in merchants

    print("\n--- Testing Gold Aggregations ---")
    gold_results = run_gold_aggregations(spark)
    assert gold_results["daily"].count() > 0
    assert gold_results["category"].count() > 0

    print("\n--- Testing Weekly Email Report Generation ---")
    email_html = generate_weekly_email_report(spark)
    assert "Weekly Financial Executive Summary" in email_html
    assert "1,850.00" in email_html or "1850" in email_html

    print("\nALL DATABRICKS MEDALLION PIPELINE TESTS PASSED!")
    spark.stop()

if __name__ == "__main__":
    test_medallion_pipeline()
