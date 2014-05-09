(ns goodguide.datomizer.example.core
  (:require
  [datomic.api :as d :refer [db q]]
  [goodguide.datomizer.datomize.setup :refer [install-datomizer]]
  [goodguide.datomizer.datomize.decode :refer [undatomize]]))


(def db-uri "datomic:mem://foo")

(def map-attribute {:db/id (d/tempid :db.part/db)
                    :db/ident :test/map
                    :db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/many
                    :db/unique :db.unique/value
                    :db/doc "A map attribute for Datomization demonstration."
                    :db/isComponent true
                    :dmzr.ref/type :dmzr.type/map
                    :db.install/_attribute :db.part/db})

(defn setup
  "Create a new database and set it up to use Datomizer."
  []
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (install-datomizer conn)
    @(d/transact conn [map-attribute])
    conn))

(defn store
  "Store a map value in Datomic."
  [conn value]
  (let [tempid (d/tempid :db.part/user)
        datoms [[:dmzr/datomize {:db/id tempid :test/map value}]]
        result @(d/transact conn datoms)
        eid (d/resolve-tempid (:db-after result) (:tempids result) tempid)]
    eid))

(defn retrieve
  "Retreive a datomized entity from Datomic."
  [dbval eid]
  (undatomize (d/entity dbval eid)))

(comment

  ;; try this at your repl:
  (use 'goodguide.datomizer.example.core)
  (require '[datomic.api :as d :refer [db q]])
  (def conn (setup))
  (def id (store conn {:a "hi there" :b [1 :two "three" 4.0]}))
  (d/touch (d/entity (db conn) id))
  (retrieve (db conn) id)

  )
