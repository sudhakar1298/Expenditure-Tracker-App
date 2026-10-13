from pyspark.sql import SparkSession
from pyspark.sql.functions import current_timestamp, lit
from databricks.config import config, save_spark_df_safely

def run_bronze_ingestion(spark: SparkSession, raw_data_list: list) -> str:
    """
    Ingests raw expense dictionaries into the Bronze Delta/Data Lake layer.
    """
    if not raw_data_list:
        print("[Bronze] No raw data provided for ingestion.")
        return config.BRONZE_TABLE_PATH

    # Convert list of dicts directly to PySpark DataFrame
    raw_df = spark.createDataFrame(raw_data_list)

    # Add ingestion metadata
    bronze_df = raw_df.withColumn("ingestion_timestamp", current_timestamp()) \
                       .withColumn("data_quality_status", lit("RAW"))

    # Write to Bronze path safely across OS platforms
    save_spark_df_safely(bronze_df, config.BRONZE_TABLE_PATH, spark)
    print(f"[Bronze] Successfully ingested {bronze_df.count()} raw records to {config.BRONZE_TABLE_PATH}")
    return config.BRONZE_TABLE_PATH
