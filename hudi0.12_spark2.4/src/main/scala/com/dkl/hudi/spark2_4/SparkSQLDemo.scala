package com.dkl.hudi.spark2_4

import org.apache.spark.sql.SparkSession

/**
 * Hudi Spark SQL Demo
 */
object SparkSQLDemo {
  val tableName = "test_hudi_table"

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().
      master("local[*]").
      appName("SparkSQLDemo").
      config("spark.serializer", "org.apache.spark.serializer.KryoSerializer").
      // 扩展Spark SQL，使Spark SQL支持Hudi
      config("spark.sql.extensions", "org.apache.spark.sql.hudi.HoodieSparkSessionExtension").
      config("hive.metastore.uris", "thrift://localhost:9083").
      // 适配不同版本hive, https://docs.databricks.com/data/metastores/external-hive-metastore.html
      config("spark.sql.hive.metastore.version", "2.3.3").
      config("spark.sql.hive.metastore.jars", "/project/spark_module/spark-2.4.4-bin-hadoop2.7/hive-metastore-jar/*:/root/.m2/repository/org/apache/hudi/hudi-spark-bundle_2.11/0.12.2/*").
      // config("spark.sql.hive.metastore.jars", "maven").
      // 支持Hive
      enableHiveSupport().
      getOrCreate()

    spark.sql(s"show databases").show()
    spark.sql(s"use xiamen").show()

//    // spark2.4 drop table if exists not work
//    spark.sql(s"drop table if exists $tableName").show()

    testCreateTable(spark)

    testInsertTable(spark)
    testUpdateTable(spark)
    testDeleteTable(spark)
    testMergeTable(spark)

    testQueryTable(spark)

    spark.stop()
  }

  def testCreateTable(spark: SparkSession): Unit = {
    spark.sql(
      s"""
         |create table IF NOT EXISTS $tableName (
         |  id int,
         |  name string,
         |  price double,
         |  ts long,
         |  dt string
         |) using hudi
         | partitioned by (dt)
         | options (
         |  primaryKey = 'id',
         |  preCombineField = 'ts',
         |  type = 'cow'
         | )
         |""".stripMargin)
  }
//  ,
//  hive_sync.enable = 'false'
  def testInsertTable(spark: SparkSession): Unit = {
    spark.sql(s"insert into $tableName values (1,'hudi',10,100,'2022-09-05'),(2,'hudi',10,100,'2022-09-05')")
    spark.sql(
      s"""insert into $tableName
         |select 3 as id, 'hudi' as name, 10 as price, 100 as ts, '2022-09-25' as dt union
         |select 4 as id, 'hudi' as name, 10 as price, 100 as ts, '2022-09-25' as dt
         |""".stripMargin)

  }

  def testQueryTable(spark: SparkSession): Unit = {
    spark.sql(s"select * from $tableName").show()
  }

  def testUpdateTable(spark: SparkSession): Unit = {
    spark.sql(s"update $tableName set price = 20.0 where id = 1")
  }

  def testDeleteTable(spark: SparkSession): Unit = {
    spark.sql(s"delete from $tableName where id = 1")
  }

  def testMergeTable(spark: SparkSession): Unit = {
    spark.sql(
      s"""
         |merge into $tableName as t0
         |using (
         |  select 1 as id, 'hudi' as name, 112 as price, 98 as ts, '2022-09-05' as dt,'INSERT' as opt_type union
         |  select 2 as id, 'hudi_2' as name, 10 as price, 100 as ts, '2022-09-05' as dt,'UPDATE' as opt_type union
         |  select 3 as id, 'hudi' as name, 10 as price, 100 as ts, '2021-09-25' as dt ,'DELETE' as opt_type
         | ) as s0
         |on t0.id = s0.id
         |when matched and opt_type!='DELETE' then update set *
         |when matched and opt_type='DELETE' then delete
         |when not matched and opt_type!='DELETE' then insert *
         |""".stripMargin)
  }
}
