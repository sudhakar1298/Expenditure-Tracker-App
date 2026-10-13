from pyspark.sql import SparkSession
from pyspark.sql.functions import col, trim, when, udf, to_timestamp
from pyspark.sql.types import StringType
from databricks.config import config, save_spark_df_safely, load_spark_df_safely

def _normalize_merchant(merchant_str: str) -> str:
    if not merchant_str:
        return "UNKNOWN"
    clean = merchant_str.lower().strip()
    norm_map = {
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
    for pattern, normalized in norm_map.items():
        if pattern in clean:
            return normalized
    return merchant_str.strip().upper()

normalize_merchant_udf = udf(_normalize_merchant, StringType())

def run_silver_cleaning(spark: SparkSession) -> str:
    """
    Reads Bronze raw expenses, cleans strings, normalizes merchants, and outputs to Silver layer.
    """
    bronze_df = load_spark_df_safely(spark, config.BRONZE_TABLE_PATH)

    cleaned_df = bronze_df.withColumn("merchant_normalized", normalize_merchant_udf(col("merchant"))) \
                          .withColumn("amount_cleaned", col("amount").cast("double")) \
                          .withColumn("category_cleaned", when(col("category").isNull() | (trim(col("category")) == ""), "Others")
                                                         .otherwise(trim(col("category")))) \
                          .withColumn("transaction_time", to_timestamp(col("transaction_date")))

    silver_df = cleaned_df.select(
        col("id").alias("expense_id"),
        col("amount_cleaned").alias("amount"),
        col("merchant_normalized").alias("merchant"),
        col("category_cleaned").alias("category"),
        col("source"),
        col("transaction_time").alias("transaction_timestamp")
    ).filter(col("amount") > 0)

    save_spark_df_safely(silver_df, config.SILVER_TABLE_PATH, spark)
    print(f"[Silver] Cleaned and saved {silver_df.count()} records to {config.SILVER_TABLE_PATH}")
    return config.SILVER_TABLE_PATH
