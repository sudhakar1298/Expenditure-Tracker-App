"""Run on Databricks with --api-url and --schema catalog.schema.

The API must be reachable from the cluster. Schedule this task in a Databricks
Job; local pipeline checks do not update workspace tables.
"""
import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from databricks.config import config, fetch_expenses_from_api


def run(spark, api_url, schema):
    from delta.tables import DeltaTable
    from pyspark.sql import functions as F

    if not re.fullmatch(r"[A-Za-z_][\w]*(\.[A-Za-z_][\w]*)?", schema):
        raise ValueError("Use schema or catalog.schema")
    config.API_SOURCE_URL = api_url
    rows = fetch_expenses_from_api(limit=500)
    if not rows:
        print("Source API is empty; existing tables preserved")
        return 0

    # Explicit schema supports nullable reason and all-null optional API fields.
    fields = ["id", "amount", "merchant", "category", "source", "transaction_date", "reason"]
    data = [tuple(float(row[k]) if k == "amount" else row.get(k) for k in fields) for row in rows]
    bronze = spark.createDataFrame(data,
        "id string, amount double, merchant string, category string, source string, transaction_date string, reason string"
    ).withColumn("ingestion_timestamp", F.current_timestamp()).withColumn("data_quality_status", F.lit("RAW"))
    silver = bronze.select(
        F.col("id").alias("expense_id"), "amount", "merchant", "category", "source",
        F.to_timestamp("transaction_date").alias("transaction_timestamp"), "reason"
    )
    # Merge by API record ID: repeated job runs update entries without removing
    # older/imported records that do not appear in this API snapshot.
    for frame, name, key in [(bronze, "bronze_expenses", "id"), (silver, "silver_expenses", "expense_id")]:
        table = f"{schema}.{name}"
        frame.limit(0).write.format("delta").mode("ignore").saveAsTable(table)
        if "reason" not in spark.table(table).columns:
            spark.sql(f"ALTER TABLE {table} ADD COLUMNS (reason STRING)")
        (DeltaTable.forName(spark, table).alias("target")
         .merge(frame.alias("source"), f"target.{key} = source.{key}")
         .whenMatchedUpdateAll().whenNotMatchedInsertAll().execute())
    print(f"Merged {len(rows)} API expenses into {schema}.bronze_expenses and silver_expenses")
    return len(rows)


if __name__ == "__main__":
    from pyspark.sql import SparkSession
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-url", required=True)
    parser.add_argument("--schema", required=True)
    args = parser.parse_args()
    run(SparkSession.builder.getOrCreate(), args.api_url, args.schema)
