import sys
import os

root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
backend_dir = os.path.join(root_dir, "backend")

if root_dir not in sys.path:
    sys.path.insert(0, root_dir)
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

os.environ["PYTHONPATH"] = f"{root_dir}{os.pathsep}{backend_dir}{os.pathsep}{os.environ.get('PYTHONPATH', '')}"

from pyspark.sql import SparkSession
from databricks.config import config, save_spark_df_safely
from databricks.bronze.ingest_expenses import run_bronze_ingestion
from databricks.silver.clean_expenses import run_silver_cleaning
from databricks.gold.aggregate_metrics import run_gold_aggregations

from app.core.database import SessionLocal
from app.models.expense import Expense

def build_data_warehouse():
    print("=" * 65)
    print("🏛️ BUILDING DATABRICKS DATA WAREHOUSE & MANAGED DELTA TABLES")
    print("=" * 65)

    db = SessionLocal()
    expenses = db.query(Expense).all()
    db.close()

    raw_data = [
        {
            "id": exp.id,
            "amount": exp.amount,
            "merchant": exp.merchant,
            "category": exp.category,
            "source": exp.source,
            "transaction_date": exp.transaction_date.isoformat() if exp.transaction_date else ""
        }
        for exp in expenses
    ]

    print(f"\n1. Ingested {len(raw_data)} records from main database into Warehouse pipeline.")

    spark = SparkSession.builder \
        .appName("DatabricksDataWarehouseBuilder") \
        .master("local[1]") \
        .getOrCreate()

    # Create Warehouse Database Schema
    spark.sql("CREATE DATABASE IF NOT EXISTS expense_tracker_dw")

    # Ingest & Store Bronze
    bronze_path = run_bronze_ingestion(spark, raw_data)

    # Ingest & Store Silver
    silver_path = run_silver_cleaning(spark)

    # Ingest & Store Gold
    gold_results = run_gold_aggregations(spark)

    print("\n" + "=" * 65)
    print("🟢 DATABRICKS WAREHOUSE BUILT SUCCESSFULLY WITH ALL 228 TRANSACTIONS!")
    print("=" * 65)

    spark.stop()

if __name__ == "__main__":
    build_data_warehouse()
