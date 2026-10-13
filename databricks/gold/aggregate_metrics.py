from pyspark.sql import SparkSession
from pyspark.sql.functions import col, sum as _sum, count, date_format, weekofyear, year, round as _round
from databricks.config import config, save_spark_df_safely, load_spark_df_safely

def run_gold_aggregations(spark: SparkSession) -> dict:
    """
    Reads Silver cleaned expenses and produces analytical aggregate datasets in Gold layer.
    """
    silver_df = load_spark_df_safely(spark, config.SILVER_TABLE_PATH)

    # 1. Daily Summary
    daily_df = silver_df.withColumn("date", date_format(col("transaction_timestamp"), "yyyy-MM-dd")) \
                        .groupBy("date") \
                        .agg(_round(_sum("amount"), 2).alias("total_spent"), count("*").alias("transaction_count")) \
                        .orderBy(col("date").desc())
    save_spark_df_safely(daily_df, config.GOLD_DAILY_PATH, spark)

    # 2. Weekly Summary
    weekly_df = silver_df.withColumn("year", year("transaction_timestamp")) \
                         .withColumn("week", weekofyear("transaction_timestamp")) \
                         .groupBy("year", "week") \
                         .agg(_round(_sum("amount"), 2).alias("total_spent"), count("*").alias("transaction_count")) \
                         .orderBy(col("year").desc(), col("week").desc())
    save_spark_df_safely(weekly_df, config.GOLD_WEEKLY_PATH, spark)

    # 3. Category Summary
    category_df = silver_df.groupBy("category") \
                           .agg(_round(_sum("amount"), 2).alias("total_spent"), count("*").alias("transaction_count")) \
                           .orderBy(col("total_spent").desc())
    save_spark_df_safely(category_df, config.GOLD_CATEGORY_PATH, spark)

    # 4. Merchant Summary
    merchant_df = silver_df.groupBy("merchant") \
                           .agg(_round(_sum("amount"), 2).alias("total_spent"), count("*").alias("transaction_count")) \
                           .orderBy(col("total_spent").desc())
    save_spark_df_safely(merchant_df, config.GOLD_MERCHANT_PATH, spark)

    print("[Gold] Aggregated Gold metrics successfully created:")
    print(f"  - Daily Path: {config.GOLD_DAILY_PATH}")
    print(f"  - Weekly Path: {config.GOLD_WEEKLY_PATH}")
    print(f"  - Category Path: {config.GOLD_CATEGORY_PATH}")
    print(f"  - Merchant Path: {config.GOLD_MERCHANT_PATH}")

    return {
        "daily": daily_df,
        "weekly": weekly_df,
        "category": category_df,
        "merchant": merchant_df
    }
