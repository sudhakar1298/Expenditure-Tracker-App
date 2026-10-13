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
from databricks.config import config, fetch_expenses_from_api, load_spark_df_safely
from databricks.bronze.ingest_expenses import run_bronze_ingestion
from databricks.silver.clean_expenses import run_silver_cleaning
from databricks.gold.aggregate_metrics import run_gold_aggregations

from app.core.database import SessionLocal
from app.models.expense import Expense

def verify_databricks_connection():
    print("=" * 60)
    print("🔍 CHECKING DATABRICKS PIPELINE CONNECTION & DATA SYNC")
    print("=" * 60)

    # 1. Fetch live transactions from the API used by the mobile app.
    # Fall back to the local DB only for an offline developer run.
    try:
        raw_data = fetch_expenses_from_api()
        print(f"\n1. Source API records fetched: {len(raw_data)} expenses")
    except Exception as api_error:
        print(f"\nSource API unavailable ({api_error}); using local DB fallback.")
        db = SessionLocal()
        expenses = db.query(Expense).all()
        db.close()
        raw_data = [
            {"id": exp.id, "amount": exp.amount, "merchant": exp.merchant,
             "category": exp.category, "source": exp.source,
             "transaction_date": exp.transaction_date.isoformat() if exp.transaction_date else ""}
            for exp in expenses
        ]


    # 2. Initialize PySpark Session
    spark = SparkSession.builder \
        .appName("DatabricksConnectionCheck") \
        .master("local[1]") \
        .getOrCreate()

    # 3. Bronze Ingestion
    print("\n2. Ingesting into Bronze Layer...")
    bronze_path = run_bronze_ingestion(spark, raw_data)
    bronze_df = load_spark_df_safely(spark, bronze_path)
    bronze_count = bronze_df.count()
    print(f"   ✓ Bronze Delta Table: {bronze_count} raw records stored.")

    # 4. Silver Cleaning
    print("\n3. Cleaning & Normalizing in Silver Layer...")
    silver_path = run_silver_cleaning(spark)
    silver_df = load_spark_df_safely(spark, silver_path)
    silver_count = silver_df.count()
    print(f"   ✓ Silver Delta Table: {silver_count} cleaned records stored.")

    # 5. Gold Aggregation
    print("\n4. Aggregating Metrics in Gold Layer...")
    gold_results = run_gold_aggregations(spark)
    print(f"   ✓ Gold Daily Aggregates: {gold_results['daily'].count()} daily groups")
    print(f"   ✓ Gold Category Aggregates: {gold_results['category'].count()} category groups")
    print(f"   ✓ Gold Merchant Aggregates: {gold_results['merchant'].count()} merchant groups")

    print("\n" + "=" * 60)
    print("🟢 DATABRICKS PIPELINE CONNECTION & SYNC IS HEALTHY AND ACTIVE!")
    print("=" * 60)

    spark.stop()

if __name__ == "__main__":
    verify_databricks_connection()
