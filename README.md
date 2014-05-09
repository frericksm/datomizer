# Datomizer

## What is this?

Datomizer is a library that helps you store complex data structures
(e.g. nested maps and vectors) in Datomic.  It also implements "variant"
attributes.

SHOUTY DISCLAIMER: THIS IS AN EXPERIMENT.  WE'RE NOT USING THIS IN
PRODUCTION YET, AND YOU PROBABLY SHOULDN'T EITHER.  IT PROBABLY HAS SOME
MISTAKES AND MAY PERFORM TERRRIBLY.

We wouldn't even call this Alpha software yet.

## Why is this necessary?

### Datomic is not a document store.

Using Datomic requires breaking your data into its smallest possible
components: datoms.  A datom is a fact about the value of an attribute
of an entity at a specific time.

Datomic values can be of various types: strings, numbers, keywords, byte
arrays, moments in time, references to other objects, etc.  They
can be a single value or a set of values.  An attribute's
definition specifies the type and cardinality of values assignable to
it.  Values with "multiple" cardinality can hold more that one value,
but they *record no order*.  (That is, they're *sets* of values.)

But what if your data is complex?  What if it's an *ordered* vector of
values?  What if it's a map with keys you don't know in advance?  What
if it's some arbitrarily nested combination of vectors and maps?  What
if the value of an attribute can be of any type?

Datomic doesn't provide built-in support for these use cases, but we can
implement it ourselves.

### Two Strategies

There are two obvious approaches to storing complex data in Datomic:
serialization (converting the data to a string or binary format and
storing it as a single value) and what we will call "datomization":
breaking the structure up into entities that represent the elements of
the collection.

#### Serialization

Serialization is simple: we pass the value to pr-str or the equivalent on
the way into Datomic, and read it with the appropriate reader on the way
back out.  We could do this with EDN, or perhaps JSON, XML or Fressian.

The problem with serialization is that the serialized strings or
byte-arrays are not intelligible to Datomic.  Their contents cannot be
queried without de-serialization with database functions in
the query, which will probably be slow.

#### Datomization

For "Datomization", we traverse the data structure and generate a set of
datoms to represent it.  A map is recorded as a reference attribute with
cardinality many pointing to a set of entities representing pairs,
featuring key and value attribues.  A vector is similar, but is keyed
by numbers.  There is a variety of value attributes to allow
heterogenous collections.  Map and vector attributes allow nesting.
Variants refer to a single entity bearing a value attribute (and no
key).

"Un-datomization" reverses this process, rebuilding a complex data
structure from its representation as Datomic entities.

##### Updates

A tricky bit is updating a previously datomized value.  We *could*
simply retract all of the value's component entities, then create a new
value from scratch, but this would add a lot of noise to the value's
history.  We would see a lot of retractions and additions of equivalent
elements.  This would make understanding a complex value's history (for
instance, tracking changes by annotating transactions with the user's
id) difficult.  

Instead of wholesale retraction and re-addition, we do a diff between
the currently stored value and the new value, then apply the minimum set
of additions and retractions necessary to update the value in place.

In order to guarantee that modifications to existing values are done
correctly, we need to do them in a transaction function.  If we were to
read the current state in a peer process, then make a modification based
on the read, we would have no guarantee that the read state was still
accurate at the time the modification is applied.  This might corrupt
our data structure.

Performing the update correctly requires running it on the transactor.

## OK, this sounds fun!  How do I install it?

Datomizer is a library of Clojure functions designed to run both on the
peer and on the transactor.  It needs to be in each process's
classpath.  To load the library on the transactor Putting the datomizer.jar
file in *root-of-your-datomic-installation*/lib before startup.

Before using Datomizer, you'll need to create the schema it uses.

Datomizer's schema is namespaced under "dmzr"

Add datomizer to your the dependencies in your project.clj.

Get Datomizer from the maven repo, and put it on your datomic transactor's classpath.

```bash
lein deps
cp ~/.m2/repository/com/goodguide/datomizer/0.1.0-SNAPSHOT/datomizer-0.1.0-SNAPSHOT.jar $WHEREVER-YOU-INSTALLED-DATOMIC/lib/
```

(start datomic transactor)

Start a repl for your app and install the Datomizer schema:

```clojure

(require '[datomic.api :as d :refer [db q]])
(require '[goodguide.datomizer.datomize.setup :as setup])

(def db-uri "datomic:mem://example") ; or wherever

(def map-attribute {:db/id (d/tempid :db.part/db)
                    :db/ident :test/map
                    :db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/many
                    :db/unique :db.unique/value
                    :db/doc "A map attribute for Datomization demonstration."
                    :db/isComponent true
                    :dmzr.ref/type :dmzr.type/map
                    :db.install/_attribute :db.part/db})

  (d/create-database db-uri) ; if necessary
  (def conn (d/connect db-uri))
  (setup/install-datomizer conn)
  @(d/transact conn [map-attribute])

```

## OK, it's installed.  How do I use it?

Here's an example of how to use it: src/goodguide/datomizer/example/core.clj

Play around with it in your repl.

```
; CIDER 0.6.0snapshot (Clojure 1.6.0, nREPL 0.2.3)
user>   (use 'goodguide.datomizer.example.core)

nil
user>   (require '[datomic.api :as d :refer [db q]])

nil
user>   (def conn (setup))

#'user/conn
user>   (def id (store conn {:a "hi there" :b [1 :two "three" 4.0]}))

#'user/id
user>   (d/touch (d/entity (db conn) id))

{:test/map #{{:dmzr.element.map/key :b, :dmzr.element.value/vector #{{:dmzr.element.vector/index 0, :dmzr.element.value/long 1, :db/id 17592186045449} {:dmzr.element.vector/index 1, :dmzr.element.value/keyword :two, :db/id 17592186045450} {:dmzr.element.vector/index 2, :dmzr.element.value/string "three", :db/id 17592186045451} {:dmzr.element.vector/index 3, :dmzr.element.value/double 4.0, :db/id 17592186045452}}, :db/id 17592186045448} {:dmzr.element.map/key :a, :dmzr.element.value/string "hi there", :db/id 17592186045453}}, :db/id 17592186045447}

user> (retrieve (db conn) id)
{:test/map {:b [1 :two "three" 4.0], :a "hi there"}, :db/id 17592186045447}
user> 
```

## Wow, cool!  Now we can put everything in maps and pretend Datomic is schemaless!

Er... that's probably a bad idea.

XXXX Talk here about why it's often better to define lots of attributes
rather than using maps.

## What's the deal this "nil" value?

In Datomic, the idiomatic way to represent absence of an attribute-value
on an entity is to... not add a fact assigning the attribute-value to
the entity.  Or to retract it, if it exists.  Absence of a fact is
represented by absence of a fact.

In relational databases using tables, a special "NULL" value is needed
to represent information missing from a record because there's no other
way to omit a field.  Each column is present for every row.

Since Datomic lets us attach arbitrary attribute-values to any entity, a
special NULL value is unneeded.

However: It may be necessary for whatever reason to represent data
structures containing nulls.  Your application might use maps with
keys pointing to nil or vectors containing nils.  Datomizer uses the
:dmzr.element.value/nil attribute with a value of (the keyword) :NIL to
represent these.

(Note that there's probably not a great reason to create a variant with a
:dmzr.element.value/nil value.)

## OK, what about performance?

We haven't used this at any real scale yet.  It may perform poorly for some
use cases.  This is an experiment and we're still playing with it.  Let
us know what you discover.
