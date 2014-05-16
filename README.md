# Datomizer

## What is this?

Datomizer is a library that helps you store complex data structures
(e.g. nested maps and vectors) in Datomic.  It also implements "variant"
attributes.

SHOUTY DISCLAIMER: THIS IS AN EXPERIMENT.  WE'RE NOT USING THIS IN
PRODUCTION YET, AND YOU PROBABLY SHOULDN'T EITHER.  IT PROBABLY HAS SOME
MISTAKES AND MAY PERFORM TERRIBLY.

We wouldn't even call this Alpha software yet.

## Why is this necessary?

### Datomic is not a document store.

Using Datomic requires breaking your data into its smallest possible
components: datoms.  A datom is a fact about the value of an attribute
of an entity at a specific time.

Datomic values can be of various types: strings, numbers, keywords, byte
arrays, moments in time, references to other objects, etc.  They can be
a single value or a set of values.  An attribute's definition specifies
the type and cardinality of values assignable to it.  Values with
"multiple" cardinality can hold more that one value, but note that they
*record no order*.  (That is, they're unordered *sets* of values, not
vectors.)

But what if your data is complex?  What if it's an *ordered* vector of
values?  What if it's a map with keys you don't know in advance?  What
if it's some arbitrarily nested combination of vectors and maps?  What
if the value of an attribute can be of any type?

Datomic doesn't provide built-in support for these use cases, but we can
implement it ourselves.

### Two Strategies

There are two obvious approaches to storing complex data in Datomic:
**serialization**, converting the data to a string or binary format and
storing it as a single value, and what we will call "**datomization**",
breaking the structure up into entities that represent the elements of
the collection.

#### Serialization

Serialization is simple: we pass the value to pr-str or the equivalent on
the way into Datomic, and read it with the appropriate reader on the way
back out.  We could do this with EDN, or perhaps JSON, XML or Fressian.

The problem with serialization is that the serialized strings or
byte-arrays are not intelligible to Datomic.  Their contents cannot be
referenced in queries (without de-serialization using database
functions, which will probably be slow.)

#### Datomization

In "datomization", we traverse the data structure and build a
representation of it as a set of datoms.  A map is recorded as a
reference attribute with cardinality "many" pointing to a set of
entities representing pairs, featuring key and value attributes.  A
vector is similar, but is keyed by numbers instead of keywords.  There
is a variety of value attributes to allow heterogeneous collections.
Map and vector attributes allow nesting.  Variants refer to a single
entity bearing a value attribute (and no key).

"Un-datomization" reverses this process, rebuilding a complex data
structure from its datomized representation.

##### Updates

Updating a previously datomized value is a bit tricky.  We *could*
simply retract all of the value's component entities, then create a new
value from scratch, but this would add a lot of noise to the value's
history.  We would see a lot of retractions and additions of equivalent
elements.  This would make understanding a complex value's history
difficult.

Instead of wholesale retraction and re-addition, we do a diff between
the currently stored value and the new value, then apply the minimum set
of additions and retractions necessary to update the value in place.

In order to guarantee that modifications to existing values are done
correctly, we *must* do them in a transaction function.  If we were to
read the current state in a peer process, then make a modification based
on the read, we would have no guarantee that the read state was still
accurate at the time the modification is applied.  This might corrupt
our data structure.

Performing the update correctly requires running it on the transactor,
which means that Datomizer needs to be on its classpath.

## OK, Datomizer sounds fun!  How do I install it?

Datomizer is a library of Clojure functions designed to run both on the
peer and on the transactor.  It needs to be in each process's classpath.

Add Datomizer to your the dependencies in your project.clj:

```clojure
(defproject datomizer-example "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.datomic/datomic-pro "0.9.4707"]
                 [com.goodguide/datomizer "0.1.0"]])
```

(To make the datomic-pro library available to leiningen, you may need to
install it in your local maven repository by running
$WHEREVER-YOU-INSTALLED-DATOMIC/bin/maven-install)

Get Datomizer from the repository, and put it on your datomic
transactor's classpath:

```bash
lein deps
cp ~/.m2/repository/com/goodguide/datomizer/0.1.0/datomizer-0.1.0.jar $WHEREVER-YOU-INSTALLED-DATOMIC/lib/
```

Then (re-)start the Datomic transactor.

Before using Datomizer, you'll need to create the schema it uses.
Datomizer's schema is name-spaced under "dmzr"

Start a repl for your app and install the Datomizer schema:

```clojure

(require '[datomic.api :as d :refer [db q]])
(require '[goodguide.datomizer.datomize.setup :as setup])

(def db-uri "datomic:mem://example") ; or wherever
(d/create-database db-uri) ; if necessary
(def conn (d/connect db-uri))
(setup/install-datomizer conn)

```

## OK, it's installed.  How do I use it?

Here's an example: [src/goodguide/datomizer/example/core.clj](https://github.com/GoodGuide/datomizer/blob/master/src/goodguide/datomizer/example/core.clj)

Try it out in your repl:

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

A lot of Datomic's expressiveness comes from the flexibility of its
schema.  It's **not** schema-less, but arbitrary attribute values can be
attached to entities.  If you find yourself wanting to create a lot of
map values that use the same keys over and over, ask yourself whether
the keys just should be Datomic attributes.  You don't need Datomizer to
map attributes to values.

## Hey, what's the deal this "nil" value?

In Datomic, the idiomatic way to represent absence of an attribute-value
on an entity is to... not add a fact assigning the attribute-value to
the entity.  (Or to retract it, if it exists.)  Absence of a fact is
represented by... absence of a fact.

In relational databases using tables, a special "NULL" value is needed
to represent information missing from a record because there's no other
way to omit a field.  Each column is present for every row.

Since Datomic lets us compose entities with arbitrary sets of
attribute-values, a special NULL value is not needed.

However: It may be necessary (for whatever weird reason) to represent
data structures containing nulls.  Your application might use maps with
keys pointing to nil or vectors containing nils.  Datomizer uses the
:dmzr.element.value/nil attribute with a value of (the keyword) :NIL to
represent these.

(Note that there's probably not a great reason to create a variant with
a :dmzr.element.value/nil value.)

## OK, what about performance?

We haven't used this at any real scale yet.  It may perform poorly for
many use cases.  This is an experiment and we're still playing with it.
Let us know what you discover.

## Who owns this code?  How am I allowed to use it?

Datomizer is copyright (c) GoodGuide.  All rights reserved.

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0
(http://opensource.org/licenses/eclipse-1.0.php) which can be found in
the file epl-v10.html at the root of this distribution.

By using this software in any fashion, you are agreeing to be bound by
the terms of this license.

You must not remove this notice, or any other, from this software.
