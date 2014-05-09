(ns goodguide.datomizer.datomize.setup
  (:require [datomic.api :as d :refer [db]]
            [goodguide.datomizer.utility.misc :refer :all]))

(defn load-datomizer-schema
  "Load the schema used by datomizer."
  [conn]
  (load-datoms-from-edn-resource-file conn "datomizer-schema.edn"))

(defn load-datomizer-functions
  "Load datomizer functions.  Requires datomizer jar in transactor lib
  directory."
  [conn]
  (load-datoms-from-edn-resource-file conn "datomizer-functions.edn"))

(defn install-datomizer [conn]
  "Install datomizer schema and functions."
  (load-datomizer-schema conn)
  (load-datomizer-functions conn))
