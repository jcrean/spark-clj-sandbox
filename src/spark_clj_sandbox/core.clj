(ns spark-clj-sandbox.core
  (:require 
   [clojure.tools.nrepl.server :as nrepl-server]
   [cider.nrepl                :refer [cider-nrepl-handler]]
   [sparkling.core             :as spark]
   [sparkling.conf             :as conf]
   [amazonica.aws.s3           :as s3]
   [amazonica.core             :refer [defcredential]]
   [clojure.java.io            :as io]
   [cheshire.core              :as json])
  (:import
   [org.apache.spark.sql SQLContext DataFrame Column])
  (:gen-class
   :main true))


(comment

  (def sc (spark/spark-context (-> (conf/spark-conf)
                                   (conf/master "local[*]")
                                   (conf/app-name "testing123"))))


  (spark/text-file sc "emrfs://com.relaynetwork/jc/foo")

  ;; hadoop config for s3n access
  (do
    (.set (.hadoopConfiguration sc) "fs.s3n.awsAccessKeyId"     "the-key")
    (.set (.hadoopConfiguration sc) "fs.s3n.awsSecretAccessKey" "the-secret")
    )
)

(defn s3-object-seq [^java.util.Map opts]
  (lazy-seq
   (let [listing (s3/list-objects {:profile (or (:aws-profile opts) "default")} opts)
         objs    (:object-summaries listing)]
     (if (:truncated? listing)
       (concat objs (s3-object-seq (merge opts {:marker (:next-marker listing)})))
       objs))))

(defn example-job [^java.util.Map opts]
  (let [conf           (let [conf (conf/spark-conf)]
                         (when (:in-dev-mode? opts)
                           (conf/master conf "local[*]"))
                         (conf/app-name conf (or (:app-name opts) "testing123"))
                         conf)
        
        event-bucket   (:bucket-name opts)
        event-prefix   (:prefix      opts)
        
        sc             (let [c (spark/spark-context conf)]
                         (doseq [[k v] (-> opts (get "hadoop-config"))]
                           (.set (.hadoopConfiguration c) k v))
                         c)

        obj-summaries  (s3-object-seq
                        (merge
                         opts
                         {:bucket-name event-bucket
                          :prefix      event-prefix}))
        
        rdd            (spark/parallelize sc (map :key obj-summaries))
        
        json-lines-rdd (->> rdd
                            (spark/flat-map
                             (fn [k]
                               (with-open [rdr (io/reader (:input-stream (s3/get-object {:bucket-name event-bucket :key k})))]
                                 (into [] (line-seq rdr))))))
        sql-ctx        (SQLContext. sc)
        df             (-> sql-ctx .read (.json json-lines-rdd))
        event-name-col (-> df (.col "event-name"))]
    (println "CLJ: Attempting group-by operation")
    (println (-> df (.groupBy (into-array Column [event-name-col])) .count (.show 100)))
    (println "CLJ: All done")))


(defn example-job2 [^java.util.Map opts]
  (let [conf           (let [conf (conf/spark-conf)]
                         (when (:in-dev-mode? opts)
                           (conf/master conf "local[*]"))
                         (conf/app-name conf (or (:app-name opts) "testing123"))
                         conf)
        
        event-bucket   (:bucket-name opts)
        event-prefix   (:prefix      opts)
        
        sc             (let [c (spark/spark-context conf)]
                         (doseq [[k v] (-> opts :hadoop-config)]
                           (.set (.hadoopConfiguration c) (name k) v))
                         c)

        sql-ctx        (SQLContext. sc)
        df             (-> sql-ctx .read (.json (-> opts :events-s3-path)))
        event-name-col (-> df (.col "event-name"))
        grouped-df     (-> df (.groupBy (into-array Column [event-name-col])) .count)]

    (println "Writing data to s3")

    (-> grouped-df .write (.json (-> opts :output-s3-path)))
    
    (println "CLJ: All done")))

(comment

  (example-job {:in-dev-mode? true})

  (json/parse-string (slurp (io/resource "job-config.json")) true)
  )

(defn load-config-file [filename]
  (try
    (json/parse-string
     (slurp
      (io/resource filename))
     true)
    (catch Exception ex
      {})))

(defn -main [& args]
  (println "in core.main")
  (example-job2 (merge
                 (load-config-file "job-config.json")
                 {:in-dev-mode? false}))
  (println "core.main ran"))
